package com.rossomak.flashcards.feature.study.voice

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.domain.model.VoiceCaptureFailureReason
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Media3 [MediaSessionService] that reads flashcards aloud, with background playback capabilities. It owns a
 * [TtsPlayer] (TextToSpeech wrapped as a Media3 `Player`) and a [MediaSession]; Media3 provides the
 * lock-screen / Bluetooth / notification transport controls, media-button routing and foreground
 * lifecycle. Audio focus is managed inside [TtsPlayer] because Media3 only auto-handles focus for
 * `ExoPlayer`, which we cannot use because it does not support TTS OOTB.
 *
 * The in-app UI binds via [LocalBinder] (custom [ACTION_BIND_LOCAL] intent) to push the card queue
 * and drive playback, and observes [LocalBinder.state] — which carries TTS-specific phase and
 * between-card-pause flags that the standard `Player` state cannot express. System controllers
 * connect to the [MediaSession] returned from [onGetSession].
 *
 * Voice answering (premium): the service also hosts a [VoiceAnswerController], so background mic
 * capture shares this exact session-scoped foreground lifecycle — listening starts/stops with
 * the session, never outlives it.
 *
 * Media3 only ever starts the foreground service with the `mediaPlayback` type, and drops the
 * foreground state on every pause. Without an active `microphone` type, a background app records
 * silence. So for a voice-answering session, [onUpdateNotification] never delegates to Media3: the
 * service builds Media3's notification itself and holds both types from the first update, made
 * with the app visible, until [stopPlayback]. Android 14+ refuses to add the `microphone` type from
 * the background, so the type set never shrinks mid-session, not even while voice answering is
 * paused. The notification cannot be swiped away for the whole session. Fast sessions keep
 * Media3's default behavior.
 */
@UnstableApi
@AndroidEntryPoint
class StudySessionVoiceService : MediaSessionService() {

    @Inject
    lateinit var voiceAnswerController: VoiceAnswerController

    private val binder = LocalBinder()

    private lateinit var player: TtsPlayer
    private lateinit var mediaSession: MediaSession

    private lateinit var notificationProvider: DefaultMediaNotificationProvider
    private val notificationActionFactory = VoiceSessionNotificationActionFactory(service = this)

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var sessionCards: List<VoiceFlashcard> = emptyList()

    // Fixed when the session loads, never by setVoiceAnswering: a pause turns voice answering off,
    // and the microphone type must survive it for a background resume to be able to listen.
    private var isVoiceAnsweringSession = false
    private var isStartedForVoiceAnswering = false

    inner class LocalBinder : Binder() {
        val state: StateFlow<VoicePlaybackState> get() = player.voiceState

        val voiceAnswerState: StateFlow<VoiceAnswerState> get() = voiceAnswerController.state

        val rawVoiceLevel: Flow<Float> get() = voiceAnswerController.rawVoiceLevel

        fun loadSession(cards: List<VoiceFlashcard>, startIndex: Int, subcategoryName: String, isVoiceAnsweringSession: Boolean) {
            sessionCards = cards
            this@StudySessionVoiceService.isVoiceAnsweringSession = isVoiceAnsweringSession
            player.loadAndStartSession(cards, startIndex, subcategoryName)
            // Right away, while the study screen that loads the session is still visible: the
            // microphone type can only be acquired from the foreground.
            if (isVoiceAnsweringSession) startForegroundWithMicrophone(mediaSession)
        }

        fun updateQueue(cards: List<VoiceFlashcard>) {
            sessionCards = cards
            player.updateQueue(cards)
        }

        fun togglePlayPause() = player.togglePlayPause()

        fun moveToNextCard() = player.moveToNextCard()

        fun moveToPreviousCard() = player.moveToPreviousCard()

        fun restartCurrentCardPlayback() = player.restartCurrentCardPlayback()

        fun skipToCardAnswerPlayback() = player.skipToCardAnswerPlayback()

        fun setPlaybackSpeechRate(rate: Float) = player.setPlaybackSpeechRate(rate)

        fun setVoice(voiceId: String?) = player.setVoice(voiceId)

        fun setVoiceAnswering(enabled: Boolean) {
            player.setVoiceAnsweringMode(enabled)
            if (enabled) {
                voiceAnswerController.start()
                // Voice answering turns on only after loadSession, which ignores a refused
                // microphone type while the controller is not running yet; retry so a refusal reaches it.
                if (isVoiceAnsweringSession) startForegroundWithMicrophone(mediaSession)
            } else {
                voiceAnswerController.stop()
            }
        }

        fun setNextSilenceWillPauseSession(willPause: Boolean) {
            voiceAnswerController.setNextSilenceWillPauseSession(willPause)
        }

        fun setNextGradingFailureWillPauseSession(willPause: Boolean) {
            voiceAnswerController.setNextGradingFailureWillPauseSession(willPause)
        }

        fun stopPlayback() = this@StudySessionVoiceService.stopPlayback()
    }

    override fun onCreate() {
        super.onCreate()
        // applicationContext, not `this` — TextToSpeech's engine binder connection outlives our
        // own shutdown() call by a beat (async unbind), and would otherwise pin the whole Service
        // (MediaSession, CoroutineScope, VoiceAnswerController) alive past onDestroy() (leak).
        player = TtsPlayer(applicationContext)
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(contentPendingIntent())
            .build()
        // Shared with Media3's default path, so both post the same notification on the same channel.
        notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        setMediaNotificationProvider(notificationProvider)
        observeCurrentCardForVoiceAnswering()
        observeVoiceAnswerAdvanceRequests()
    }

    // Keeps the grading context in lockstep with whichever card TTS playback is on, so a
    // captured utterance is always graded against the card the user just heard, and opens the
    // controller's listening window exactly when the shared TTS engine finishes the question
    // (ADR-0025: never listen while any TTS is speaking).
    private fun observeCurrentCardForVoiceAnswering() {
        serviceScope.launch {
            var wasAwaitingSpokenAnswer = false
            player.voiceState.collect { voice ->
                val currentCard = if (voice.isActive) sessionCards.getOrNull(voice.currentIndex) else null
                voiceAnswerController.setActiveCard(currentCard)
                if (voice.isAwaitingSpokenAnswer && !wasAwaitingSpokenAnswer) {
                    voiceAnswerController.onQuestionFinishedSpeaking()
                }
                wasAwaitingSpokenAnswer = voice.isAwaitingSpokenAnswer
            }
        }
    }

    // Grade computed (or silence-timeout skip) + notice spoken -> controller asks to move on.
    private fun observeVoiceAnswerAdvanceRequests() {
        serviceScope.launch {
            voiceAnswerController.advanceRequests.collect { player.advanceToNextCardAfterVoiceAnswer() }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        if (isVoiceAnsweringSession) {
            startForegroundWithMicrophone(session)
        } else {
            super.onUpdateNotification(session, startInForegroundRequired)
        }
    }

    private fun startForegroundWithMicrophone(session: MediaSession) {
        val mediaNotification = notificationProvider.createNotification(
            session,
            session.mediaButtonPreferences,
            notificationActionFactory,
        ) { updatedNotification ->
            ContextCompat.getMainExecutor(this).execute {
                if (isVoiceAnsweringSession) postForegroundNotification(session, updatedNotification)
            }
        }
        postForegroundNotification(session, mediaNotification)
    }

    // Runs on every notification update, paused or not: each startForeground call must carry both
    // types, since passing mediaPlayback alone would drop the microphone type for good.
    @SuppressLint("InlinedApi") // ServiceCompat drops the types a platform release does not know
    private fun postForegroundNotification(session: MediaSession, mediaNotification: MediaNotification) {
        mediaNotification.notification.extras.putParcelable(Notification.EXTRA_MEDIA_SESSION, session.platformToken)
        try {
            if (!isStartedForVoiceAnswering) {
                // Started, not only bound, like Media3's own foreground path. A plain start: a
                // foreground-service start would crash the app if startForeground below then threw.
                startService(Intent(this, StudySessionVoiceService::class.java))
                isStartedForVoiceAnswering = true
            }
            ServiceCompat.startForeground(
                this,
                mediaNotification.notificationId,
                mediaNotification.notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } catch (exception: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: the app was already in the background.
            onMicrophoneForegroundRefused(exception)
        } catch (exception: SecurityException) {
            onMicrophoneForegroundRefused(exception)
        }
    }

    private fun onMicrophoneForegroundRefused(exception: RuntimeException) {
        loge(exception) { "Voice answering session refused the microphone foreground-service type" }
        voiceAnswerController.reportCaptureFailure(VoiceCaptureFailureReason.CaptureLoopError(exception.message))
    }

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == ACTION_BIND_LOCAL) binder else super.onBind(intent)

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Voice is tied to the study screen; swiping the app away ends playback.
        stopPlayback()
        super.onTaskRemoved(rootIntent)
    }

    private fun stopPlayback() {
        voiceAnswerController.stop()
        if (isVoiceAnsweringSession) {
            // Hands the notification back to Media3's default path before the queue empties.
            isVoiceAnsweringSession = false
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        player.stopPlayback()
        stopSelf()
    }

    override fun onDestroy() {
        voiceAnswerController.release()
        serviceScope.cancel()
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    private fun contentPendingIntent(): PendingIntent {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP }
            ?: Intent().apply { setPackage(packageName) }
        return PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_BIND_LOCAL = "com.rossomak.flashcards.feature.study.voice.BIND_LOCAL"
    }
}
