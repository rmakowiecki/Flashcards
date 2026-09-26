package com.rossomak.flashcards.core.voice

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import com.rossomak.flashcards.core.common.logd
import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.common.logi
import com.rossomak.flashcards.core.domain.model.VoiceCaptureFailureReason
import com.rossomak.flashcards.core.voice.VoiceCaptureEvent.CaptureFailed
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A single VAD-bounded utterance. Only obfuscated audio ever leaves the engine in this form. */
data class CapturedUtterance(
    val obfuscatedPcm: ShortArray,
    val wavBytes: ByteArray,
    val durationMs: Long,
)

/**
 * Continuous mic capture: AudioRecord (16kHz mono PCM) feeding 20ms frames through the
 * [VoiceActivityDetector]; VAD-bounded utterances are buffered, run through the [VoiceObfuscator]
 * on-device, WAV-wrapped and emitted via [events]. Raw (un-obfuscated) buffers are zeroed as soon
 * as the obfuscated copy exists.
 *
 * Microphone *routing* (BLE-first, SCO fallback, Bluetooth-strict — ADR-0027) is owned by
 * [AudioRouteManager] at the session level, not here. This engine only reads the currently active
 * [CaptureRoute]: it binds the input device via `AudioRecord.setPreferredDevice`, verifies the
 * honored [AudioRecord.getRoutedDevice] after `startRecording()`, and — when a Bluetooth mic is
 * required but unavailable ([CaptureRouteType.Waiting]) — strict-pauses rather than silently falling
 * back to the pocketed phone mic. On a mid-session route change it rebuilds the [AudioRecord] at the
 * next utterance boundary (an [AudioRecord] cannot change device live).
 *
 * Callers own the surrounding foreground-service + wake-lock lifecycle and the session route
 * (feature:study): they call [AudioRouteManager.acquireSessionRoute] before listening and
 * [AudioRouteManager.releaseSessionRoute] at session end.
 */
@Singleton
class VoiceCaptureEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val voiceActivityDetector: VoiceActivityDetector,
    private val voiceObfuscator: VoiceObfuscator,
    private val audioRouteManager: AudioRouteManager,
) {

    private enum class CaptureResult { Stopped, Failed, RouteChanged }

    private val _events = MutableSharedFlow<VoiceCaptureEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<VoiceCaptureEvent> = _events.asSharedFlow()

    private val _isSpeechDetected = MutableStateFlow(false)
    val isSpeechDetected: StateFlow<Boolean> = _isSpeechDetected.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    /**
     * Smoothed `0..1` microphone input level, updated every frame while listening and 0 otherwise.
     * Reacts to any sound above the noise floor, not only VAD-detected speech (see [InputLevelMeter]).
     *
     * Privacy: computed on the device from raw PCM only. It is never logged, stored or uploaded.
     */
    private val _inputLevel = MutableStateFlow(0f)
    val inputLevel: StateFlow<Float> = _inputLevel.asStateFlow()

    /**
     * The mic device actually carrying audio right now, confirmed via [AudioRecord.getRoutedDevice]
     * — `null` whenever nothing is recording (debug-screen route indicator; see [isRouteHonored] for
     * the boolean form production capture already relies on).
     */
    private val _actualMicDevice = MutableStateFlow<AudioDeviceInfo?>(null)
    val actualMicDevice: StateFlow<AudioDeviceInfo?> = _actualMicDevice.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var captureJob: Job? = null

    /**
     * Starts continuous VAD-driven capture. No-op when already listening.
     *
     * @param maxUtteranceDuration per-call override of the hard per-utterance cap, clamped to
     * [MAX_UTTERANCE_DURATION] so a caller can only tighten it, never loosen it beyond the
     * engine-wide default other callers (e.g. feature:study's continuous voice answering) rely on.
     */
    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun startListening(maxUtteranceDuration: Duration = MAX_UTTERANCE_DURATION) {
        if (captureJob?.isActive == true) return
        logd { "startListening" }
        voiceActivityDetector.reset()
        voiceObfuscator.randomizeSessionShift()
        _isListening.value = true
        val maxUtteranceFrames =
            (minOf(maxUtteranceDuration, MAX_UTTERANCE_DURATION).inWholeMilliseconds / FRAME_DURATION_MS).toInt()
                .coerceAtLeast(MIN_UTTERANCE_FRAMES)
        captureJob = scope.launch { runCaptureLoop(maxUtteranceFrames) }
    }

    fun stopListening() {
        logd { "stopListening" }
        captureJob?.cancel()
        captureJob = null
        _isListening.value = false
        _isSpeechDetected.value = false
        _inputLevel.value = 0f
    }

    /**
     * One-shot fixed-length recording of *raw* (un-obfuscated) PCM. Debug-screen use only —
     * on-device playback for verifying capture and judging the obfuscation A/B by ear. This
     * audio must never be uploaded; production capture goes through [startListening], which
     * emits obfuscated audio exclusively.
     */
    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    suspend fun recordRawClip(durationMs: Long): ShortArray = withContext(Dispatchers.IO) {
        val totalSamples = (SAMPLE_RATE_HZ * durationMs / 1000).toInt()
        val clip = ShortArray(totalSamples)
        // Debug capture: use whatever route is active (phone if no session route acquired); no
        // strict-pause or verification here — this path is dev-only, never production capture.
        val audioRecord = createAudioRecord(audioRouteManager.route.value) ?: return@withContext ShortArray(0)
        try {
            audioRecord.startRecording()
            var readSamples = 0
            while (readSamples < totalSamples) {
                val read = audioRecord.read(clip, readSamples, totalSamples - readSamples)
                if (read <= 0) break
                readSamples += read
                updateActualMicDevice(audioRecord)
            }
        } finally {
            runCatching { audioRecord.stop() }
            audioRecord.release()
            _actualMicDevice.value = null
        }
        clip
    }

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    private suspend fun runCaptureLoop(maxUtteranceFrames: Int) {
        // A route change (BT connect/disconnect mid-session) can't be applied to a live AudioRecord,
        // so it's applied by rebuilding at the next utterance boundary. This flag is raised by the
        // route-change collector and consumed at a safe point inside captureFrames(). AtomicBoolean
        // because the collector and the capture loop run as separate coroutines on Dispatchers.Default
        // and can land on different threads — a plain var risks a stale read delaying the rebuild.
        val routeChangePending = AtomicBoolean(false)
        val routeChangeJob = scope.launch {
            audioRouteManager.routeChanges.collect { routeChangePending.set(true) }
        }
        try {
            while (captureJob?.isActive == true) {
                val route = audioRouteManager.route.value
                if (!route.isCapturable) {
                    // Bluetooth-strict (ADR-0027): a mic-capable BT device is connected but its link
                    // isn't ready — never fall back to the pocketed phone mic. Strict-pause instead.
                    loge { "Bluetooth microphone unavailable" }
                    _events.emit(CaptureFailed(VoiceCaptureFailureReason.BluetoothMicUnavailable))
                    return
                }
                routeChangePending.set(false)
                val result = attemptCapture(route, maxUtteranceFrames, routeChangePending)
                logd { "capture attempt result: $result" }
                when (result) {
                    CaptureResult.Stopped, CaptureResult.Failed -> return
                    CaptureResult.RouteChanged -> Unit // loop and rebuild AudioRecord on the new route
                }
            }
        } finally {
            routeChangeJob.cancel()
            _isListening.value = false
            _isSpeechDetected.value = false
            _inputLevel.value = 0f
            _actualMicDevice.value = null
        }
    }

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    private suspend fun attemptCapture(
        route: CaptureRoute,
        maxUtteranceFrames: Int,
        routeChangePending: AtomicBoolean,
    ): CaptureResult {
        val audioRecord = createAudioRecord(route) ?: run {
            loge { "AudioRecord initialization failed" }
            _events.emit(CaptureFailed(VoiceCaptureFailureReason.AudioRecordInitFailed))
            return CaptureResult.Failed
        }
        return try {
            audioRecord.startRecording()
            if (!isRouteHonored(audioRecord, route)) {
                loge { "Capture not routed to Bluetooth microphone" }
                _events.emit(CaptureFailed(VoiceCaptureFailureReason.CaptureNotRoutedToBluetooth))
                CaptureResult.Failed
            } else {
                warmUpBluetoothRoute(audioRecord, route)
                captureFrames(audioRecord, maxUtteranceFrames) { routeChangePending.get() }
            }
        } catch (exception: SecurityException) {
            loge(exception) { "Microphone permission missing" }
            _events.emit(CaptureFailed(VoiceCaptureFailureReason.PermissionMissing(exception.message)))
            CaptureResult.Failed
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            // VAD inference or AudioRecord failures used to kill the loop silently, leaving
            // the UI stuck on "silence". Surface them so they show up in the debug event log.
            loge(exception) { "capture loop error" }
            _events.emit(CaptureFailed(VoiceCaptureFailureReason.CaptureLoopError(exception.message)))
            CaptureResult.Failed
        } finally {
            runCatching { audioRecord.stop() }
            audioRecord.release()
        }
    }

    /**
     * Reads VAD-bounded utterances off [audioRecord] until the job is cancelled ([CaptureResult.Stopped])
     * or [isRouteChangePending] flips. A pending route change is honored at an utterance boundary:
     * an in-flight utterance is finished first, then [CaptureResult.RouteChanged] is returned so the
     * caller rebuilds on the new route (never a mid-clip device switch).
     */
    private suspend fun captureFrames(
        audioRecord: AudioRecord,
        maxUtteranceFrames: Int,
        isRouteChangePending: () -> Boolean,
    ): CaptureResult {
        val frame = ShortArray(FRAME_SIZE_SAMPLES)
        val state = UtteranceState()
        val inputLevelMeter = InputLevelMeter()
        while (captureJob?.isActive == true) {
            if (isRouteChangePending() && !state.isInUtterance) return CaptureResult.RouteChanged
            val read = audioRecord.read(frame, 0, frame.size)
            if (read <= 0) continue
            updateActualMicDevice(audioRecord)
            _inputLevel.value = inputLevelMeter.process(frame, read)
            if (read < frame.size) frame.fill(0, read, frame.size)
            val isSpeech = voiceActivityDetector.isSpeech(frame)
            _isSpeechDetected.value = isSpeech
            val routeChangeRequested = processFrame(frame, isSpeech, state, maxUtteranceFrames, isRouteChangePending)
            if (routeChangeRequested) return CaptureResult.RouteChanged
        }
        return CaptureResult.Stopped
    }

    /** Mutable per-utterance buffers, kept together so [captureFrames] passes a single state object around. */
    private class UtteranceState {
        val preRoll = ArrayDeque<ShortArray>(PRE_ROLL_FRAMES)
        val frames = mutableListOf<ShortArray>()
        var isInUtterance = false
        var trailingSilenceFrames = 0
        var speechFrameCount = 0
        var speechStartEmitted = false

        /**
         * True exactly once per utterance: when it first has enough speech for [finishUtterance] to
         * keep it. SpeechStarted waits for this so a blip that is later discarded never announces
         * itself. Counts speech frames across pauses, the same total [finishUtterance] checks.
         */
        fun claimSpeechStart(): Boolean {
            if (speechStartEmitted || speechFrameCount < MIN_UTTERANCE_FRAMES) return false
            speechStartEmitted = true
            return true
        }
    }

    /** Returns true if a pending route change was honored and the caller should return [CaptureResult.RouteChanged]. */
    private suspend fun processFrame(
        frame: ShortArray,
        isSpeech: Boolean,
        state: UtteranceState,
        maxUtteranceFrames: Int,
        isRouteChangePending: () -> Boolean,
    ): Boolean = when {
        isSpeech && !state.isInUtterance -> {
            startUtterance(frame, state)
            false
        }
        state.isInUtterance && isSpeech -> continueUtterance(frame, state, maxUtteranceFrames, isRouteChangePending)
        state.isInUtterance -> handleTrailingSilence(frame, state, maxUtteranceFrames, isRouteChangePending)
        else -> {
            bufferPreRoll(frame, state)
            false
        }
    }

    private suspend fun startUtterance(frame: ShortArray, state: UtteranceState) {
        state.isInUtterance = true
        state.trailingSilenceFrames = 0
        state.speechFrameCount = 1
        state.speechStartEmitted = false
        state.frames.clear()
        state.frames.addAll(state.preRoll)
        state.preRoll.clear()
        state.frames.add(frame.copyOf())
        if (state.claimSpeechStart()) _events.emit(VoiceCaptureEvent.SpeechStarted)
    }

    /** Returns true if a pending route change was honored and the caller should return [CaptureResult.RouteChanged]. */
    private suspend fun continueUtterance(
        frame: ShortArray,
        state: UtteranceState,
        maxUtteranceFrames: Int,
        isRouteChangePending: () -> Boolean,
    ): Boolean {
        state.frames.add(frame.copyOf())
        state.speechFrameCount++
        state.trailingSilenceFrames = 0
        if (state.claimSpeechStart()) _events.emit(VoiceCaptureEvent.SpeechStarted)
        if (state.frames.size < maxUtteranceFrames) return false
        return finishUtteranceAndCheckRoute(state, isRouteChangePending)
    }

    /**
     * Only pads a short context window (leading-in for the next burst, or trailing-out for this
     * one) into the buffer; frames beyond GAP_PAD_FRAMES are neither appended nor
     * obfuscated/uploaded — dead air between thinking-pauses never leaves the device.
     * trailingSilenceFrames still counts every silent frame so the END_SILENCE_FRAMES hangover
     * timing is unaffected.
     */
    private suspend fun handleTrailingSilence(
        frame: ShortArray,
        state: UtteranceState,
        maxUtteranceFrames: Int,
        isRouteChangePending: () -> Boolean,
    ): Boolean {
        state.trailingSilenceFrames++
        if (state.trailingSilenceFrames <= GAP_PAD_FRAMES) {
            state.frames.add(frame.copyOf())
        }
        val utteranceEnded = state.trailingSilenceFrames >= END_SILENCE_FRAMES
        val utteranceTooLong = state.frames.size >= maxUtteranceFrames
        if (!utteranceEnded && !utteranceTooLong) return false
        return finishUtteranceAndCheckRoute(state, isRouteChangePending)
    }

    /** Shared by cap-hit (mid-speech) and silence-hit (trailing) endings, so both emit the same event sequence. */
    private suspend fun finishUtteranceAndCheckRoute(
        state: UtteranceState,
        isRouteChangePending: () -> Boolean,
    ): Boolean {
        state.isInUtterance = false
        // Paired with SpeechStarted: a discarded blip emitted neither.
        if (state.speechStartEmitted) _events.emit(VoiceCaptureEvent.SpeechEnded)
        finishUtterance(state.frames, state.speechFrameCount)
        state.frames.clear()
        state.trailingSilenceFrames = 0
        state.speechFrameCount = 0
        state.speechStartEmitted = false
        // Finished the in-flight utterance; now it's safe to switch devices.
        return isRouteChangePending()
    }

    private fun bufferPreRoll(frame: ShortArray, state: UtteranceState) {
        state.preRoll.addLast(frame.copyOf())
        if (state.preRoll.size > PRE_ROLL_FRAMES) state.preRoll.removeFirst()
    }

    private suspend fun finishUtterance(frames: List<ShortArray>, speechFrameCount: Int) {
        if (speechFrameCount < MIN_UTTERANCE_FRAMES) return
        val raw = ShortArray(frames.size * FRAME_SIZE_SAMPLES)
        frames.forEachIndexed { index, chunk -> chunk.copyInto(raw, index * FRAME_SIZE_SAMPLES) }
        frames.forEach { it.fill(0) } // Per-frame copies are scrubbed once folded into raw.
        val obfuscated = voiceObfuscator.obfuscate(raw)
        raw.fill(0) // Raw voiceprint is dropped the moment the obfuscated copy exists.
        val durationMs = obfuscated.size * 1000L / SAMPLE_RATE_HZ
        _events.emit(
            VoiceCaptureEvent.UtteranceCaptured(
                CapturedUtterance(
                    obfuscatedPcm = obfuscated,
                    wavBytes = WavEncoder.encode(obfuscated, SAMPLE_RATE_HZ),
                    durationMs = durationMs,
                )
            )
        )
    }

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    private fun createAudioRecord(route: CaptureRoute): AudioRecord? {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize <= 0) return null
        val audioRecord = AudioRecord(
            preferredAudioSource(route),
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBufferSize, FRAME_SIZE_SAMPLES * Short.SIZE_BYTES * BUFFER_FRAMES),
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            return null
        }
        // Bind the input to the Bluetooth mic before recording starts — OEMs latch the input route
        // at startRecording(), so the preferred device must be set here, not after.
        route.device?.let { audioRecord.setPreferredDevice(it) }
        return audioRecord
    }

    /**
     * Drains frames off a freshly-opened Bluetooth [audioRecord] until real signal is observed or
     * [BT_WARMUP_TIMEOUT_MS] elapses, before [captureFrames] starts feeding the VAD. BT SCO/LE-Audio
     * streams have a HAL-level ramp-up right after `startRecording()` — the first stretch of reads
     * can be silence or garbage while the codec and jitter buffer settle — so words spoken into that
     * window are genuinely never captured; no amount of pre-roll buffering recovers audio that was
     * never delivered. Phone-mic routes have no such ramp-up and are skipped entirely. Logs the
     * observed settle time per route type so [BT_WARMUP_ENERGY_THRESHOLD]/[BT_WARMUP_TIMEOUT_MS] can
     * be tuned from real-device numbers instead of guessed.
     */
    private fun warmUpBluetoothRoute(audioRecord: AudioRecord, route: CaptureRoute) {
        if (!route.isBluetooth) return
        val frame = ShortArray(FRAME_SIZE_SAMPLES)
        val startNanos = System.nanoTime()
        val deadlineNanos = startNanos + BT_WARMUP_TIMEOUT_MS * 1_000_000
        while (System.nanoTime() < deadlineNanos) {
            val read = audioRecord.read(frame, 0, frame.size)
            if (read <= 0) continue
            if (frameEnergy(frame, read) >= BT_WARMUP_ENERGY_THRESHOLD) {
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                logi { "BT warm-up settled in ${elapsedMs}ms (route=${route.type})" }
                return
            }
        }
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
        logi { "BT warm-up timed out after ${elapsedMs}ms (route=${route.type}) — proceeding anyway" }
    }

    private fun frameEnergy(frame: ShortArray, sampleCount: Int): Long {
        var sum = 0L
        for (index in 0 until sampleCount) sum += kotlin.math.abs(frame[index].toInt())
        return sum / sampleCount
    }

    /**
     * After startRecording(), confirm a Bluetooth route actually landed on the Bluetooth mic. A
     * mismatch means the OEM silently steered capture to the phone mic — which, phone-in-pocket, is
     * the core failure this pipeline exists to prevent. Phone/none routes are trivially honored.
     *
     * `routedDevice` is frequently still null for a few ms right after `startRecording()` — the
     * stream's routing settles asynchronously even though the BT link itself was already confirmed
     * up by [AudioRouteManager]'s session handshake. A short poll avoids treating that transient
     * null as a real steer-to-phone-mic failure; a non-null mismatch fails immediately since that is
     * an actual wrong-device signal, not a timing gap.
     */
    private suspend fun isRouteHonored(audioRecord: AudioRecord, route: CaptureRoute): Boolean {
        if (!route.isBluetooth) return true
        val deadlineNanos = System.nanoTime() + ROUTE_HONORED_TIMEOUT_MS * 1_000_000
        while (true) {
            val routedDevice = audioRecord.routedDevice
            if (routedDevice != null) return routedDevice.type == route.device?.type
            if (System.nanoTime() >= deadlineNanos) return false
            delay(ROUTE_HONORED_POLL_MS.milliseconds)
        }
    }

    /** Debug-screen route indicator: publish [audioRecord]'s actual routed device on change only. */
    private fun updateActualMicDevice(audioRecord: AudioRecord) {
        val routedDevice = audioRecord.routedDevice
        if (routedDevice?.id != _actualMicDevice.value?.id) _actualMicDevice.value = routedDevice
    }

    /**
     * Route-dependent audio source (ADR-0027 Q1). Phone mic stays MIC: VOICE_RECOGNITION routed
     * through OEM noise-suppression/AGC that gutted the signal to near silence on some devices (e.g.
     * Realme/MTK). Bluetooth routes use VOICE_COMMUNICATION — the source semantically paired with
     * [AudioManager.MODE_IN_COMMUNICATION] (set by [AudioRouteManager] for BT routes); Android's
     * audio policy wires SCO/LE-Audio input most consistently for this source across OEMs, whereas
     * MIC over a BT communication device is a comparatively under-tested combination.
     */
    private fun preferredAudioSource(route: CaptureRoute): Int =
        if (route.isBluetooth) {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        } else {
            MediaRecorder.AudioSource.MIC
        }

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val FRAME_DURATION_MS = 20
        const val FRAME_SIZE_SAMPLES = SAMPLE_RATE_HZ * FRAME_DURATION_MS / 1000
        private const val BUFFER_FRAMES = 8

        // Heuristic mean-abs-amplitude floor for "real signal, not BT ramp-up silence/garbage"
        // (int16 PCM). Tune from the settle-time numbers this logs on real BT hardware.
        private const val BT_WARMUP_ENERGY_THRESHOLD = 250L

        // Hard cap on how long warmUpBluetoothRoute() waits for real signal before giving up and
        // starting VAD capture anyway — never block the loop indefinitely on a stuck link.
        private const val BT_WARMUP_TIMEOUT_MS = 600L

        // isRouteHonored() poll: bridges the async gap between startRecording() and routedDevice
        // reporting a non-null value, distinct from BT_WARMUP_*'s later real-signal settle check.
        private const val ROUTE_HONORED_TIMEOUT_MS = 200L
        private const val ROUTE_HONORED_POLL_MS = 20L

        private const val PRE_ROLL_FRAMES = 10 // 200ms of audio kept before speech onset

        // 2.5s silence closes the utterance. Deliberately generous: spoken answers contain
        // thinking-pauses, and a shorter window cut recordings off mid-answer. Silero's accurate
        // soft-speech detection keeps these pauses from being padded with false-positive frames.
        private const val END_SILENCE_FRAMES = 125

        // Only this much silence is kept around each speech burst (leading-in for the next one,
        // trailing-out for this one) before ElevenLabs upload; the rest of a thinking-pause is
        // trimmed at capture time. Silence carries no ASR signal, so cutting it is safe and keeps
        // payload/latency down — the pad is just insurance against clipping a boundary word.
        private const val GAP_PAD_FRAMES = 8 // 160ms
        private const val MIN_UTTERANCE_FRAMES = 15 // <300ms of speech is discarded as noise
        private const val MAX_UTTERANCE_FRAMES = 1500 // 30s hard cap per utterance
        val MAX_UTTERANCE_DURATION: Duration = (MAX_UTTERANCE_FRAMES * FRAME_DURATION_MS).milliseconds
    }
}
