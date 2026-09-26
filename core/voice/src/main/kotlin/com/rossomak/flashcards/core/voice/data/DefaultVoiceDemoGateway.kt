package com.rossomak.flashcards.core.voice.data

import android.annotation.SuppressLint
import com.rossomak.flashcards.core.domain.model.VoiceDemoFailureReason
import com.rossomak.flashcards.core.domain.model.VoiceDemoRecordingResult
import com.rossomak.flashcards.core.domain.model.VoiceDemoRecordingResult.Cancelled
import com.rossomak.flashcards.core.domain.model.VoiceDemoRecordingResult.Captured
import com.rossomak.flashcards.core.domain.model.VoiceDemoRecordingResult.NothingCaptured
import com.rossomak.flashcards.core.domain.model.VoiceDemoState
import com.rossomak.flashcards.core.domain.model.VoiceDemoState.Failed
import com.rossomak.flashcards.core.domain.model.VoiceDemoState.Idle
import com.rossomak.flashcards.core.domain.model.VoiceDemoState.Listening
import com.rossomak.flashcards.core.domain.model.VoiceDemoState.Playing
import com.rossomak.flashcards.core.domain.model.VoiceDemoState.Processing
import com.rossomak.flashcards.core.domain.model.VoiceDemoState.Ready
import com.rossomak.flashcards.core.domain.model.VoiceDemoState.SpeechDetected
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
import com.rossomak.flashcards.core.voice.VoiceLevelWaveShaper
import dagger.hilt.android.ViewModelLifecycle
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The onboarding voice demo over core:voice's capture, routing and playback stack. Unlike the study
 * session's continuous voice answering, this is a single tap-scoped listen bound to one screen
 * visit: no foreground service. [stop] is a hard cut; [finishRecording] is the graceful early end,
 * keeping what was already said.
 *
 * Each attempt holds the session audio route only while listening: it is released as soon as an
 * utterance is captured, before playback starts.
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

    private val _state = MutableStateFlow<VoiceDemoState>(Idle)
    override val state: StateFlow<VoiceDemoState> = _state.asStateFlow()

    private val isPlaying: Flow<Boolean> = _state.map { state -> state is Playing }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val levels: Flow<List<Float>> = VoiceLevelWaveShaper().shape(
        level = isPlaying.flatMapLatest { playing -> if (playing) pcmPlayer.playbackLevel else voiceCaptureEngine.inputLevel },
        isActive = combine(voiceCaptureEngine.isListening, isPlaying) { listening, playing -> listening || playing },
    )

    // Main.immediate: the public calls arrive on Main, so every job field and state write runs on one
    // thread and a late capture event can't race stop().
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var captureEventsJob: Job? = null
    private var sessionAudioRouteAcquireJob: Job? = null
    private var listenJob: Job? = null
    private var noSpeechTimeoutJob: Job? = null
    private var playbackResetJob: Job? = null

    private var capturedUtterance: CapturedUtterance? = null

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
        _state.value = Listening
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
                _state.value = Failed(VoiceDemoFailureReason.RouteUnavailable)
                return@launch
            }
            voiceCaptureEngine.startListening(MAX_UTTERANCE_DURATION)
            restartNoSpeechTimeout()
        }
    }

    override suspend fun finishRecording(): VoiceDemoRecordingResult = when (val current = _state.value) {
        Listening, SpeechDetected -> finishListening()
        // A second call while the first is still finishing waits for it. Only the first call reports
        // NothingCaptured, so a double tap surfaces that result once; later calls see Cancelled.
        Processing -> settledOutcome(_state.first { state -> state !is Processing })
        else -> settledOutcome(current)
    }

    override fun play() {
        if (_state.value != Ready) return
        val utterance = capturedUtterance ?: return
        playbackResetJob?.cancel()
        pcmPlayer.play(utterance.obfuscatedPcm)
        _state.value = Playing
        playbackResetJob = scope.launch {
            pcmPlayer.isPlaying.first { playing -> !playing }
            if (_state.value == Playing) _state.value = Ready
        }
    }

    override fun stop() {
        resetPlayback()
        stopListeningInternal()
        capturedUtterance = null
        _state.value = Idle
    }

    private suspend fun finishListening(): VoiceDemoRecordingResult {
        noSpeechTimeoutJob?.cancel()
        noSpeechTimeoutJob = null
        if (listenJob?.isActive == true) {
            // Still waiting for the route: the microphone never opened, so nothing was recorded.
            stopListeningInternal()
            _state.value = Idle
            return NothingCaptured
        }
        _state.value = Processing
        val utterance = voiceCaptureEngine.finishListening()
        when (_state.value) {
            // The natural end of the utterance beat the stop and was already handled.
            Ready, Playing -> return Captured
            // stop() or a capture failure ended the attempt while it was finishing.
            Idle, is Failed -> return Cancelled
            Listening, SpeechDetected, Processing -> Unit
        }
        stopListeningInternal()
        return if (utterance != null) {
            capturedUtterance = utterance
            _state.value = Ready
            Captured
        } else {
            _state.value = Idle
            NothingCaptured
        }
    }

    private fun settledOutcome(state: VoiceDemoState): VoiceDemoRecordingResult =
        if (state is Ready || state is Playing) Captured else Cancelled

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
                // Never step back out of Processing: a speech start can still be in flight when the user stops.
                if (_state.value == Listening) _state.value = SpeechDetected
            }
            is SpeechEnded -> Unit
            is UtteranceCaptured -> {
                capturedUtterance = event.utterance
                _state.value = Ready
                // Releases the route too: the attempt is over. Cancels this collector last.
                stopListeningInternal()
            }
            is CaptureFailed -> {
                stopListeningInternal()
                _state.value = Failed(VoiceDemoFailureReason.CaptureError(event.reason))
            }
        }
    }

    private fun restartNoSpeechTimeout() {
        noSpeechTimeoutJob?.cancel()
        noSpeechTimeoutJob = scope.launch {
            delay(NO_SPEECH_TIMEOUT_MS.milliseconds)
            stopListeningInternal()
            _state.value = Idle
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
