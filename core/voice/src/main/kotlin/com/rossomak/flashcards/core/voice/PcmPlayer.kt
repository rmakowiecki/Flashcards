package com.rossomak.flashcards.core.voice

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Minimal on-device playback of raw PCM clips: used by the debug Voice screen to audition captured
 * audio and the obfuscation A/B, and by onboarding's voice demo to play back the obfuscated
 * utterance. Card playback during study sessions stays on the separate Media3 [TtsPlayer] stack.
 *
 * Main-thread only: [play] and [stop] must be called on the main thread, where completion and level
 * updates are also delivered.
 */
@Singleton
class PcmPlayer @Inject constructor() {

    private var audioTrack: AudioTrack? = null
    private val handler = Handler(Looper.getMainLooper())

    /** Debug-screen route indicator: the device actually carrying playback, `null` when idle. */
    private val _actualDevice = MutableStateFlow<AudioDeviceInfo?>(null)
    val actualDevice: StateFlow<AudioDeviceInfo?> = _actualDevice.asStateFlow()

    /** True from [play] until the clip has played to its end or [stop] is called. */
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /**
     * Smoothed `0..1` level of the audio at the current play position, 0 when idle. Precomputed per
     * frame from the clip ([PcmLevelEnvelope]), so it matches the live microphone level's scale.
     */
    private val _playbackLevel = MutableStateFlow(0f)
    val playbackLevel: StateFlow<Float> = _playbackLevel.asStateFlow()

    private var levelEnvelope = FloatArray(0)
    private var frameSizeSamples = 1
    private var clipSampleCount = 0

    private val routingListener = AudioRouting.OnRoutingChangedListener { router ->
        _actualDevice.value = (router as? AudioTrack)?.routedDevice
    }

    private val playbackPositionListener = object : AudioTrack.OnPlaybackPositionUpdateListener {
        override fun onMarkerReached(track: AudioTrack) {
            if (track === audioTrack) stop()
        }

        override fun onPeriodicNotification(track: AudioTrack) = Unit
    }

    private val levelPoll = object : Runnable {
        override fun run() {
            val track = audioTrack ?: return
            val playedSamples = runCatching { track.playbackHeadPosition }.getOrDefault(clipSampleCount)
            // Fallback for devices that never deliver the end marker.
            if (playedSamples >= clipSampleCount) {
                stop()
                return
            }
            val frameIndex = (playedSamples / frameSizeSamples).coerceIn(0, levelEnvelope.lastIndex)
            _playbackLevel.value = levelEnvelope[frameIndex]
            handler.postDelayed(this, LEVEL_POLL_INTERVAL_MS)
        }
    }

    fun play(pcm: ShortArray, sampleRateHz: Int = VoiceCaptureEngine.SAMPLE_RATE_HZ) {
        stop()
        if (pcm.isEmpty()) return
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * Short.SIZE_BYTES)
            .build()
        track.write(pcm, 0, pcm.size)
        levelEnvelope = PcmLevelEnvelope.compute(pcm, sampleRateHz)
        frameSizeSamples = PcmLevelEnvelope.frameSizeSamples(sampleRateHz)
        clipSampleCount = pcm.size
        track.setPlaybackPositionUpdateListener(playbackPositionListener, handler)
        track.notificationMarkerPosition = pcm.size
        track.play()
        track.addOnRoutingChangedListener(routingListener, handler)
        _actualDevice.value = track.routedDevice
        audioTrack = track
        _isPlaying.value = true
        handler.post(levelPoll)
    }

    fun stop() {
        handler.removeCallbacks(levelPoll)
        audioTrack?.let { track ->
            track.setPlaybackPositionUpdateListener(null)
            track.removeOnRoutingChangedListener(routingListener)
            runCatching { track.stop() }
            track.release()
        }
        audioTrack = null
        levelEnvelope = FloatArray(0)
        clipSampleCount = 0
        _actualDevice.value = null
        _playbackLevel.value = 0f
        _isPlaying.value = false
    }

    private companion object {
        const val LEVEL_POLL_INTERVAL_MS = 20L
    }
}
