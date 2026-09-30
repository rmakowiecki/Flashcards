package com.rossomak.flashcards.feature.study.voice.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.rossomak.flashcards.core.common.logw

/**
 * Plays a short burst of silence from the app's own process right before each utterance. The
 * [android.speech.tts.TextToSpeech] engine plays speech from its own process, so without this the
 * app never plays audio itself, which causes two problems:
 *
 * - Headset buttons: the system sends media keys to the media session of the app that most recently
 *   played audio. The engine has no media session, so the keys went to whichever media app played
 *   last (YouTube, Spotify), even while this session was playing and held audio focus.
 * - Clipped speech on Bluetooth: an idle A2DP stream goes to sleep, and the headset drops the first
 *   syllable while it wakes up. The silence wakes it during the engine's synthesis delay.
 *
 * It requests no audio focus: [TtsPlayer] already holds it. Any failure is logged and ignored, and
 * reading goes on without it.
 */
internal class PlaybackPreroll {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var audioTrack: AudioTrack? = null

    private val silence = ShortArray(SAMPLE_RATE_HZ * DURATION_MS / MILLIS_PER_SECOND)

    fun play() {
        mainHandler.post {
            runCatching { startTrack() }
                .onFailure { exception -> logw(exception) { "Playback preroll failed to play" } }
        }
    }

    fun release() {
        mainHandler.post { releaseTrack() }
    }

    private fun startTrack() {
        releaseTrack()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE_HZ)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(silence.size * Short.SIZE_BYTES)
            .build()
        audioTrack = track
        track.write(silence, 0, silence.size)
        track.notificationMarkerPosition = silence.size
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

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val DURATION_MS = 100
        const val MILLIS_PER_SECOND = 1_000
    }
}
