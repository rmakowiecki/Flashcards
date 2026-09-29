package com.rossomak.flashcards.feature.study.voice.data

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
import com.rossomak.flashcards.core.domain.model.CaptureEvent
import com.rossomak.flashcards.core.domain.model.PlaybackEvent
import com.rossomak.flashcards.core.domain.model.SpokenNotice
import com.rossomak.flashcards.core.domain.model.TransportCommandType
import com.rossomak.flashcards.core.domain.model.VoiceCaptureFailureReason
import com.rossomak.flashcards.core.domain.model.VoicePlaybackState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Media3 [MediaSessionService] that reads flashcards aloud, with background playback capabilities. It owns a
 * [TtsPlayer] (TextToSpeech wrapped as a Media3 `Player`) and a [MediaSession]; Media3 provides the
 * lock-screen / Bluetooth / notification transport controls, media-button routing and foreground
 * lifecycle. Audio focus is managed inside [TtsPlayer] because Media3 only auto-handles focus for
 * `ExoPlayer`, which we cannot use because it does not support TTS OOTB.
 *
 * [StudySessionVoiceGateway] binds via [LocalBinder] (custom [ACTION_BIND_LOCAL] intent) to push the
 * card queue and drive playback, and observes [LocalBinder.state] — which carries the TTS-specific
 * phase that the standard `Player` state cannot express — and the ordered
 * [LocalBinder.playbackEvents]. System controllers connect to the [MediaSession] returned from
 * [onGetSession]; their commands come back out as [PlaybackEvent.ExternalCommand].
 *
 * It also hosts the rest of the voice stack, so it all shares this session-scoped foreground
 * lifecycle: a [NoticeSpeaker] on its own text-to-speech engine, and, for voice answering, a
 * [VoiceCaptureSession]. Nothing here makes a session decision; the study session coordinators do.
 *
 * A [PlaybackWakeLock] is held exactly while the player plays, including the silent pauses between
 * parts, which the coordinator times in this process.
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
    lateinit var voiceCaptureSession: VoiceCaptureSession

    private val binder = LocalBinder()

    // Single collector (the gateway); unlimited so nothing reported before it subscribes is lost.
    private val playbackEvents = Channel<PlaybackEvent>(Channel.UNLIMITED)

    private lateinit var player: TtsPlayer
    private lateinit var noticeSpeaker: NoticeSpeaker
    private lateinit var mediaSession: MediaSession
    private lateinit var playbackWakeLock: PlaybackWakeLock

    private lateinit var notificationProvider: DefaultMediaNotificationProvider
    private val notificationActionFactory = VoiceSessionNotificationActionFactory(service = this)

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Fixed when the session loads, never by stopVoiceAnswering: a pause stops voice answering, and
    // the microphone type must survive it for a background resume to be able to listen.
    private var isVoiceAnsweringSession = false
    private var isStartedForVoiceAnswering = false

    @Suppress("TooManyFunctions") // one method per voice-stack command the gateway forwards.
    inner class LocalBinder : Binder() {
        val state: StateFlow<VoicePlaybackState> get() = player.voiceState

        val playbackEvents: Flow<PlaybackEvent> get() = this@StudySessionVoiceService.playbackEvents.receiveAsFlow()

        val captureEvents: Flow<CaptureEvent> get() = voiceCaptureSession.events

        val rawVoiceLevel: Flow<Float> get() = voiceCaptureSession.rawVoiceLevel

        fun loadSession(cards: List<VoiceFlashcard>, startIndex: Int, sessionTitle: String, isVoiceAnsweringSession: Boolean) {
            this@StudySessionVoiceService.isVoiceAnsweringSession = isVoiceAnsweringSession
            player.loadAndStartSession(cards, startIndex, sessionTitle)
            // Right away, while the study screen that loads the session is still visible: the
            // microphone type can only be acquired from the foreground.
            if (isVoiceAnsweringSession) startForegroundWithMicrophone(mediaSession)
        }

        fun updateQueue(cards: List<VoiceFlashcard>) = player.updateQueue(cards)

        fun play() = player.startReading()

        fun pause() = player.pauseReading()

        fun presentQuestion(index: Int) = player.presentQuestion(index)

        fun presentAnswer(index: Int) = player.presentAnswer(index)

        // Notices and feedback speak with the same voice and rate as the questions.
        fun setSpeechRate(rate: Float) {
            player.setPlaybackSpeechRate(rate)
            noticeSpeaker.setSpeechRate(rate)
        }

        fun setVoice(voiceId: String?) {
            player.setVoice(voiceId)
            noticeSpeaker.setVoice(voiceId)
        }

        fun speakNotice(notice: SpokenNotice) = noticeSpeaker.speak(notice)

        fun stopFeedback() = noticeSpeaker.stopFeedback()

        fun resumeWithoutReading() = player.resumeWithoutReading()

        fun setAvailableCommands(commands: Set<TransportCommandType>) = player.setTransportCommands(commands)

        fun setSessionProgress(completedCount: Int, totalCount: Int) = player.setSessionProgress(completedCount, totalCount)

        fun startVoiceAnswering() {
            voiceCaptureSession.start()
            // Voice answering starts only after loadSession, which drops a refused microphone type
            // while capture is not started yet; retry so a refusal reaches the capture events.
            if (isVoiceAnsweringSession) startForegroundWithMicrophone(mediaSession)
        }

        fun stopVoiceAnswering() = voiceCaptureSession.stop()

        suspend fun prepareListening() = voiceCaptureSession.prepareListening()

        fun startListening() = voiceCaptureSession.startListening()

        fun stopListening() = voiceCaptureSession.stopListening()

        fun playListeningCue() = voiceCaptureSession.playListeningCue()

        fun stopPlayback() = this@StudySessionVoiceService.stopPlayback()
    }

    override fun onCreate() {
        super.onCreate()
        // applicationContext, not `this` — TextToSpeech's engine binder connection outlives our
        // own shutdown() call by a beat (async unbind), and would otherwise pin the whole Service
        // (MediaSession, CoroutineScope, VoiceCaptureSession) alive past onDestroy() (leak). Both
        // engines start here, so both initialize as soon as the voice stack starts.
        player = TtsPlayer(applicationContext) { event -> playbackEvents.trySend(event) }
        noticeSpeaker = NoticeSpeaker(
            scope = serviceScope,
            resolveText = applicationContext::spokenText,
            onNoticeFinished = { notice -> playbackEvents.trySend(PlaybackEvent.NoticeFinished(notice)) },
            onEngineUnavailable = { playbackEvents.trySend(PlaybackEvent.EngineUnavailable) },
            engineFactory = { listener -> TextToSpeechNoticeEngine(applicationContext, listener) },
        )
        playbackWakeLock = PlaybackWakeLock(applicationContext)
        serviceScope.launch {
            player.voiceState.collect { state -> if (state.isActive && state.isPlaying) playbackWakeLock.acquire() else playbackWakeLock.release() }
        }
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(contentPendingIntent())
            .build()
        // Shared with Media3's default path, so both post the same notification on the same channel.
        notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        setMediaNotificationProvider(notificationProvider)
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
        voiceCaptureSession.reportCaptureFailure(VoiceCaptureFailureReason.CaptureLoopError(exception.message))
    }

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == ACTION_BIND_LOCAL) binder else super.onBind(intent)

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Voice is tied to the study screen; swiping the app away ends playback.
        stopPlayback()
        super.onTaskRemoved(rootIntent)
    }

    private fun stopPlayback() {
        voiceCaptureSession.stop()
        if (isVoiceAnsweringSession) {
            // Hands the notification back to Media3's default path before the queue empties.
            isVoiceAnsweringSession = false
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        player.stopPlayback()
        playbackWakeLock.release()
        stopSelf()
    }

    override fun onDestroy() {
        voiceCaptureSession.release()
        noticeSpeaker.release()
        serviceScope.cancel()
        playbackWakeLock.release()
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
        const val ACTION_BIND_LOCAL = "com.rossomak.flashcards.feature.study.voice.data.BIND_LOCAL"
    }
}
