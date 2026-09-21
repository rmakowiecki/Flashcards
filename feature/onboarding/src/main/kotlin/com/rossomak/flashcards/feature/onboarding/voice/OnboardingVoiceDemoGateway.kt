package com.rossomak.flashcards.feature.onboarding.voice

import android.annotation.SuppressLint
import com.rossomak.flashcards.core.voice.AudioRouteManager
import com.rossomak.flashcards.core.voice.PcmPlayer
import com.rossomak.flashcards.core.voice.VoiceCaptureEngine
import com.rossomak.flashcards.core.voice.VoiceCaptureEvent
import com.rossomak.flashcards.core.voice.VoiceCaptureEvent.CaptureFailed
import com.rossomak.flashcards.core.voice.VoiceCaptureEvent.SpeechEnded
import com.rossomak.flashcards.core.voice.VoiceCaptureEvent.SpeechStarted
import com.rossomak.flashcards.core.voice.VoiceCaptureEvent.UtteranceCaptured
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
 * Onboarding's only consumer of core:voice concretes — see [VoiceDemoGateway]. Unlike the study
 * session's continuous voice answering ([com.rossomak.flashcards.feature.study.voice.StudySessionVoiceGateway]),
 * this is a single tap-scoped listen bound to one screen visit: no foreground service, and [stop]
 * is a hard cut rather than a graceful drain.
 */
class OnboardingVoiceDemoGateway @Inject constructor(
    private val voiceCaptureEngine: VoiceCaptureEngine,
    private val audioRouteManager: AudioRouteManager,
    private val pcmPlayer: PcmPlayer,
) : VoiceDemoGateway {

    private val _state = MutableStateFlow<VoiceDemoState>(VoiceDemoState.Idle)
    override val state: StateFlow<VoiceDemoState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var captureEventsJob: Job? = null
    private var sessionAudioRouteAcquireJob: Job? = null
    private var listenJob: Job? = null
    private var noSpeechTimeoutJob: Job? = null
    private var playbackResetJob: Job? = null

    // The screen already confirmed RECORD_AUDIO before calling this (see VoiceDemoGateway's KDoc);
    // that guard lives outside this class, so lint can't trace it back to this call site.
    @SuppressLint("MissingPermission")
    override fun start() {
        resetPlayback()
        stopListeningInternal()
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
        val ready = _state.value as? VoiceDemoState.Ready ?: return
        playbackResetJob?.cancel()
        pcmPlayer.play(ready.utterance.obfuscatedPcm)
        _state.value = VoiceDemoState.Playing
        playbackResetJob = scope.launch {
            delay(ready.utterance.durationMs.milliseconds)
            if (_state.value == VoiceDemoState.Playing) _state.value = ready
        }
    }

    override fun stop() {
        resetPlayback()
        stopListeningInternal()
        _state.value = VoiceDemoState.Idle
    }

    override fun release() {
        scope.cancel()
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
                _state.value = VoiceDemoState.Ready(event.utterance)
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
