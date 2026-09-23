package com.rossomak.flashcards.core.voice.data

import android.annotation.SuppressLint
import com.rossomak.flashcards.core.domain.model.VoiceDemoFailureReason
import com.rossomak.flashcards.core.domain.model.VoiceDemoState
import com.rossomak.flashcards.core.domain.repository.VoiceDemoGateway
import com.rossomak.flashcards.core.voice.AudioRouteManager
import com.rossomak.flashcards.core.voice.CapturedUtterance
import com.rossomak.flashcards.core.voice.PcmPlayer
import com.rossomak.flashcards.core.voice.VoiceCaptureEngine
import com.rossomak.flashcards.core.voice.VoiceCaptureEvent
import com.rossomak.flashcards.core.voice.VoiceCaptureEvent.CaptureFailed
import com.rossomak.flashcards.core.voice.VoiceCaptureEvent.SpeechEnded
import com.rossomak.flashcards.core.voice.VoiceCaptureEvent.SpeechStarted
import com.rossomak.flashcards.core.voice.VoiceCaptureEvent.UtteranceCaptured
import dagger.hilt.android.ViewModelLifecycle
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The onboarding voice demo over core:voice's capture, routing and playback stack. Unlike the study
 * session's continuous voice answering, this is a single tap-scoped listen bound to one screen
 * visit: no foreground service, and [stop] is a hard cut rather than a graceful drain.
 *
 * ViewModel-scoped: it stops itself and cancels its own scope when the owning ViewModel is cleared,
 * so the ViewModel needs no teardown call.
 */
class DefaultVoiceDemoGateway @Inject constructor(
    private val voiceCaptureEngine: VoiceCaptureEngine,
    private val audioRouteManager: AudioRouteManager,
    private val pcmPlayer: PcmPlayer,
    viewModelLifecycle: ViewModelLifecycle,
) : VoiceDemoGateway {

    private val _state = MutableStateFlow<VoiceDemoState>(VoiceDemoState.Idle)
    override val state: StateFlow<VoiceDemoState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var captureEventsJob: Job? = null
    private var sessionAudioRouteAcquireJob: Job? = null
    private var listenJob: Job? = null
    private var noSpeechTimeoutJob: Job? = null
    private var playbackResetJob: Job? = null

    @Volatile private var capturedUtterance: CapturedUtterance? = null

    init {
        viewModelLifecycle.addOnClearedListener {
            stop()
            scope.cancel()
        }
    }

    // The ViewModel confirms the microphone grant through RequestPermissionUseCase before calling
    // StartVoiceDemoUseCase; that guard lives outside this class, so lint can't trace it here.
    @SuppressLint("MissingPermission")
    override fun start() {
        resetPlayback()
        stopListeningInternal()
        capturedUtterance = null
        _state.value = VoiceDemoState.Listening
        captureEventsJob = scope.launch {
            voiceCaptureEngine.events.collect { event -> handleCaptureEvent(event) }
        }
        sessionAudioRouteAcquireJob = scope.launch { audioRouteManager.acquireSessionRoute() }
        listenJob = scope.launch {
            val routeReady = withTimeoutOrNull(ROUTE_READY_TIMEOUT_MS.milliseconds) {
                audioRouteManager.awaitRouteReady()
            } != null
            if (!routeReady) {
                stopListeningInternal()
                _state.value = VoiceDemoState.Failed(VoiceDemoFailureReason.RouteUnavailable)
                return@launch
            }
            voiceCaptureEngine.startListening(MAX_UTTERANCE_DURATION)
            restartNoSpeechTimeout()
        }
    }

    override fun play() {
        if (_state.value != VoiceDemoState.Ready) return
        val utterance = capturedUtterance ?: return
        playbackResetJob?.cancel()
        pcmPlayer.play(utterance.obfuscatedPcm)
        _state.value = VoiceDemoState.Playing
        playbackResetJob = scope.launch {
            delay(utterance.durationMs.milliseconds)
            if (_state.value == VoiceDemoState.Playing) _state.value = VoiceDemoState.Ready
        }
    }

    override fun stop() {
        resetPlayback()
        stopListeningInternal()
        capturedUtterance = null
        _state.value = VoiceDemoState.Idle
    }

    private fun resetPlayback() {
        playbackResetJob?.cancel()
        playbackResetJob = null
        pcmPlayer.stop()
    }

    private fun stopListeningInternal() {
        noSpeechTimeoutJob?.cancel()
        noSpeechTimeoutJob = null
        listenJob?.cancel()
        listenJob = null
        voiceCaptureEngine.stopListening()
        // Cancel before releasing: acquireSessionRoute() can still be mid-handshake here, and a
        // stale resume after releaseSessionRoute() would re-apply routing on a dead attempt.
        sessionAudioRouteAcquireJob?.cancel()
        sessionAudioRouteAcquireJob = null
        audioRouteManager.releaseSessionRoute()
        captureEventsJob?.cancel()
        captureEventsJob = null
    }

    private fun handleCaptureEvent(event: VoiceCaptureEvent) {
        when (event) {
            is SpeechStarted -> {
                noSpeechTimeoutJob?.cancel()
                _state.value = VoiceDemoState.SpeechDetected
            }
            // A short blip (below MIN_UTTERANCE_FRAMES) ends without UtteranceCaptured following,
            // so restart the timeout here or the demo is stuck listening with nothing to time it out.
            is SpeechEnded -> restartNoSpeechTimeout()
            is UtteranceCaptured -> {
                noSpeechTimeoutJob?.cancel()
                noSpeechTimeoutJob = null
                voiceCaptureEngine.stopListening()
                capturedUtterance = event.utterance
                _state.value = VoiceDemoState.Ready
            }
            is CaptureFailed -> {
                stopListeningInternal()
                _state.value = VoiceDemoState.Failed(VoiceDemoFailureReason.CaptureError(event.reason))
            }
        }
    }

    private fun restartNoSpeechTimeout() {
        noSpeechTimeoutJob?.cancel()
        noSpeechTimeoutJob = scope.launch {
            delay(NO_SPEECH_TIMEOUT_MS.milliseconds)
            stopListeningInternal()
            _state.value = VoiceDemoState.Idle
        }
    }

    private companion object {
        const val NO_SPEECH_TIMEOUT_MS = 8_000L
        const val ROUTE_READY_TIMEOUT_MS = 8_000L

        // Demo is a single tap-scoped listen, not the study session's continuous flow — keep it
        // well short of VoiceCaptureEngine's engine-wide 30s hard cap so an onboarding user can't
        // ramble past what the demo is meant to show.
        val MAX_UTTERANCE_DURATION = 8.seconds
    }
}
