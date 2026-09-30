package com.rossomak.flashcards.feature.study.voice.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import com.rossomak.flashcards.core.common.logi
import com.rossomak.flashcards.core.domain.model.CaptureEvent
import com.rossomak.flashcards.core.domain.model.VoiceCaptureFailureReason
import com.rossomak.flashcards.core.voice.AudioRouteManager
import com.rossomak.flashcards.core.voice.VoiceCaptureEngine
import com.rossomak.flashcards.core.voice.VoiceCaptureEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The microphone half of a voice-answering session, hosted by [StudySessionVoiceService] so capture
 * shares the session's foreground lifecycle and never outlives it. Owns the [VoiceCaptureEngine]
 * (microphone, voice activity detection and on-device obfuscation), the [AudioRouteManager] route,
 * the [ListeningCuePlayer] and a partial wake lock, so OEM battery managers cannot starve the
 * capture loop with the screen off. It makes no session decision.
 *
 * The wake lock and the device watching last the whole session; the route is activated for one
 * listening window at a time ([prepareListening] to [stopListening]), so a Bluetooth headset keeps
 * its media link, and its buttons, while nothing listens (ADR-0027).
 */
class VoiceCaptureSession @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val voiceCaptureEngine: VoiceCaptureEngine,
    private val audioRouteManager: AudioRouteManager,
    private val listeningCuePlayer: ListeningCuePlayer,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val eventChannel = Channel<CaptureEvent>(Channel.UNLIMITED)

    /** Unconflated, single collector. Only reports while [start]ed. */
    val events: Flow<CaptureEvent> = eventChannel.receiveAsFlow()

    /** 0 whenever the engine is not listening. Computed on the device and never logged, stored or uploaded. */
    val rawVoiceLevel: Flow<Float> = voiceCaptureEngine.inputLevel

    private var isStarted = false
    private var captureEventsJob: Job? = null
    private var routeObserverJob: Job? = null
    private var routeActivationJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    fun start() {
        if (isStarted) return
        isStarted = true
        acquireWakeLock()
        captureEventsJob = scope.launch {
            voiceCaptureEngine.events.collect { event -> eventChannel.trySend(event.toCaptureEvent()) }
        }
        // Route changes are only logged; each listening window activates the route (ADR-0027).
        routeObserverJob = scope.launch {
            audioRouteManager.route.collect { route -> logi { "Voice capture route: ${route.type}" } }
        }
        audioRouteManager.startSession()
    }

    fun stop() {
        voiceCaptureEngine.stopListening()
        cancelRouteActivation()
        audioRouteManager.releaseSession()
        routeObserverJob?.cancel()
        routeObserverJob = null
        captureEventsJob?.cancel()
        captureEventsJob = null
        releaseWakeLock()
        isStarted = false
    }

    /** Full teardown when the owning service dies. */
    fun release() {
        stop()
        listeningCuePlayer.release()
        scope.cancel()
    }

    /**
     * Activates the route for one listening window (BLE first, then SCO, then the phone) and
     * suspends until it can capture. Bluetooth-strict (ADR-0027): when a microphone-capable headset
     * is present but its link is not up, this waits for it instead of capturing on the pocketed
     * phone. Cancelling the caller cancels the activation; [stopListening] releases the route.
     */
    suspend fun prepareListening() {
        val activation = scope.launch { audioRouteManager.activateRoute() }
        routeActivationJob = activation
        try {
            activation.join()
            audioRouteManager.awaitRouteReady()
        } catch (cancellation: CancellationException) {
            activation.cancel()
            throw cancellation
        }
    }

    // The coordinator confirms the microphone permission before every voice-answering start
    // (ADR-0052); a permission revoked since then fails inside the engine as a capture failure.
    @SuppressLint("MissingPermission")
    fun startListening() {
        acquireWakeLock()
        voiceCaptureEngine.startListening()
    }

    fun stopListening() {
        voiceCaptureEngine.stopListening()
        cancelRouteActivation()
        audioRouteManager.deactivateRoute()
    }

    fun playListeningCue() {
        listeningCuePlayer.play(isBluetoothRoute = audioRouteManager.route.value.isBluetooth)
    }

    // Cancelled before releasing: the handshake can still be running, and a late resume after the
    // release would set Bluetooth routing up again with nothing listening.
    private fun cancelRouteActivation() {
        routeActivationJob?.cancel()
        routeActivationJob = null
    }

    /**
     * Reports a failure from outside the capture engine, such as the service being refused the
     * microphone foreground-service type. Ignored while not started: there is no round to fail.
     */
    fun reportCaptureFailure(reason: VoiceCaptureFailureReason) {
        if (isStarted) eventChannel.trySend(CaptureEvent.CaptureFailed(reason))
    }

    private fun VoiceCaptureEvent.toCaptureEvent(): CaptureEvent = when (this) {
        VoiceCaptureEvent.MicrophoneOpened -> CaptureEvent.MicrophoneOpened
        VoiceCaptureEvent.SpeechStarted -> CaptureEvent.SpeechStarted
        VoiceCaptureEvent.SpeechEnded -> CaptureEvent.SpeechEnded
        is VoiceCaptureEvent.UtteranceCaptured -> CaptureEvent.UtteranceCaptured(utterance.wavBytes)
        is VoiceCaptureEvent.CaptureFailed -> CaptureEvent.CaptureFailed(reason)
    }

    // Renewed every listening window rather than acquired once for the whole session: acquire(timeout)
    // on a non-reference-counted lock just resets its auto-release deadline, so renewing keeps the lock
    // alive across a long session while WAKE_LOCK_TIMEOUT_MS still caps the damage if renewal stalls.
    private fun acquireWakeLock() {
        val lock = wakeLock ?: run {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
            }.also { wakeLock = it }
        }
        lock.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private companion object {
        const val WAKE_LOCK_TAG = "flashcards:voiceAnswerCapture"
        const val WAKE_LOCK_TIMEOUT_MS = 60L * 60L * 1000L // 1h safety cap per session
    }
}
