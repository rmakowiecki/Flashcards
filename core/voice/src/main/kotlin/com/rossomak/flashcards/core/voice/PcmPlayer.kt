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
 */
@Singleton
class PcmPlayer @Inject constructor() {

    private var audioTrack: AudioTrack? = null
    private val handler = Handler(Looper.getMainLooper())

    /** Debug-screen route indicator: the device actually carrying playback, `null` when idle. */
    private val _actualDevice = MutableStateFlow<AudioDeviceInfo?>(null)
    val actualDevice: StateFlow<AudioDeviceInfo?> = _actualDevice.asStateFlow()

    private val routingListener = AudioRouting.OnRoutingChangedListener { router ->
        _actualDevice.value = (router as? AudioTrack)?.routedDevice
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
        track.play()
        track.addOnRoutingChangedListener(routingListener, handler)
        _actualDevice.value = track.routedDevice
        audioTrack = track
    }

    fun stop() {
        audioTrack?.let { track ->
            track.removeOnRoutingChangedListener(routingListener)
            runCatching { track.stop() }
            track.release()
        }
        audioTrack = null
        _actualDevice.value = null
    }
}
