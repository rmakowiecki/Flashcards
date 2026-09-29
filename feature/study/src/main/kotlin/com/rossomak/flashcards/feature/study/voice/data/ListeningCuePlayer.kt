package com.rossomak.flashcards.feature.study.voice.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.rossomak.flashcards.core.common.logw
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Plays the short tone that tells the user the microphone records. The tone is synthesized, well
 * under the capture's minimum utterance length, so it can never count as an answer even when the
 * microphone hears it. It requests no audio focus: the session's player already holds it.
 *
 * On a Bluetooth route the tone plays as call audio, so it follows the communication device into
 * the headset; on the phone route it plays as a sonification from the speaker. Any failure is
 * logged and ignored: listening goes on without the cue.
 */
class ListeningCuePlayer @Inject constructor() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var audioTrack: AudioTrack? = null

    private val cueSamples: ShortArray by lazy { synthesizeCue() }

    fun play(isBluetoothRoute: Boolean) {
        mainHandler.post {
            runCatching { startTrack(isBluetoothRoute) }
                .onFailure { exception -> logw(exception) { "Listening cue failed to play" } }
        }
    }

    fun release() {
        mainHandler.post { releaseTrack() }
    }

    private fun startTrack(isBluetoothRoute: Boolean) {
        releaseTrack()
        val samples = cueSamples
        val track = AudioTrack.Builder()
            .setAudioAttributes(cueAttributes(isBluetoothRoute))
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE_HZ)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
            .build()
        audioTrack = track
        track.write(samples, 0, samples.size)
        track.notificationMarkerPosition = samples.size
        track.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(finishedTrack: AudioTrack?) {
                    if (finishedTrack === audioTrack) releaseTrack()
                }

                override fun onPeriodicNotification(track: AudioTrack?) = Unit
            },
            mainHandler,
        )
        track.play()
    }

    private fun releaseTrack() {
        val track = audioTrack ?: return
        audioTrack = null
        runCatching {
            track.stop()
            track.release()
        }
    }

    private fun cueAttributes(isBluetoothRoute: Boolean): AudioAttributes {
        val usage = if (isBluetoothRoute) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
        return AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    /** A sine tone with short linear fades, so it starts and ends without a click. */
    private fun synthesizeCue(): ShortArray {
        val sampleCount = SAMPLE_RATE_HZ * CUE_DURATION_MS / MILLIS_PER_SECOND
        val fadeSamples = SAMPLE_RATE_HZ * FADE_DURATION_MS / MILLIS_PER_SECOND
        return ShortArray(sampleCount) { index ->
            val envelope = min(1.0, min(index, sampleCount - 1 - index).toDouble() / fadeSamples)
            val sample = sin(2 * PI * CUE_FREQUENCY_HZ * index / SAMPLE_RATE_HZ) * envelope * CUE_GAIN
            (sample * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val CUE_DURATION_MS = 120
        const val FADE_DURATION_MS = 10
        const val CUE_FREQUENCY_HZ = 880.0
        const val CUE_GAIN = 0.5
        const val MILLIS_PER_SECOND = 1_000
    }
}
