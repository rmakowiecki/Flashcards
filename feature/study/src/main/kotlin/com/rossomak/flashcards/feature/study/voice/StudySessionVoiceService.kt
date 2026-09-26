package com.rossomak.flashcards.feature.study.voice

import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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
 * the session, never outlives it (manifest declares the `microphone` FGS type alongside
 * `mediaPlayback`).
 */
@UnstableApi
@AndroidEntryPoint
class StudySessionVoiceService : MediaSessionService() {

    @Inject
    lateinit var voiceAnswerController: VoiceAnswerController

    private val binder = LocalBinder()

    private lateinit var player: TtsPlayer
    private lateinit var mediaSession: MediaSession

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var sessionCards: List<VoiceFlashcard> = emptyList()

    inner class LocalBinder : Binder() {
        val state: StateFlow<VoicePlaybackState> get() = player.voiceState

        val voiceAnswerState: StateFlow<VoiceAnswerState> get() = voiceAnswerController.state

        val rawVoiceLevel: Flow<Float> get() = voiceAnswerController.rawVoiceLevel

        fun loadSession(cards: List<VoiceFlashcard>, startIndex: Int, subcategoryName: String) {
            sessionCards = cards
            player.loadAndStartSession(cards, startIndex, subcategoryName)
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
            if (enabled) voiceAnswerController.start() else voiceAnswerController.stop()
        }

        fun setNextSilenceWillPauseSession(willPause: Boolean) {
            voiceAnswerController.setNextSilenceWillPauseSession(willPause)
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

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == ACTION_BIND_LOCAL) binder else super.onBind(intent)

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Voice is tied to the study screen; swiping the app away ends playback.
        stopPlayback()
        super.onTaskRemoved(rootIntent)
    }

    private fun stopPlayback() {
        voiceAnswerController.stop()
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
