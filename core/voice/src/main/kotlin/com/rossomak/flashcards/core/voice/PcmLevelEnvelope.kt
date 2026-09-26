package com.rossomak.flashcards.core.voice

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Precomputes the `0..1` level of a whole PCM clip, one value per [frameDuration] frame, with the
 * same dBFS mapping and attack/decay smoothing [InputLevelMeter] applies to live capture. Playback
 * looks the level up by play position, so a clip being played animates like the live microphone.
 */
internal object PcmLevelEnvelope {

    /** Returns one level per frame of [pcm]; a trailing partial frame gets its own level. Empty for empty input. */
    fun compute(pcm: ShortArray, sampleRateHz: Int, frameDuration: Duration = DEFAULT_FRAME_DURATION): FloatArray {
        val frameSizeSamples = frameSizeSamples(sampleRateHz, frameDuration)
        val frameCount = (pcm.size + frameSizeSamples - 1) / frameSizeSamples
        val meter = InputLevelMeter(frameDuration)
        val frame = ShortArray(frameSizeSamples)
        return FloatArray(frameCount) { frameIndex ->
            val start = frameIndex * frameSizeSamples
            val sampleCount = minOf(frameSizeSamples, pcm.size - start)
            pcm.copyInto(frame, destinationOffset = 0, startIndex = start, endIndex = start + sampleCount)
            meter.process(frame, sampleCount)
        }
    }

    fun frameSizeSamples(sampleRateHz: Int, frameDuration: Duration = DEFAULT_FRAME_DURATION): Int =
        (sampleRateHz * frameDuration.inWholeMilliseconds / MILLIS_PER_SECOND).toInt().coerceAtLeast(1)

    val DEFAULT_FRAME_DURATION: Duration = VoiceCaptureEngine.FRAME_DURATION_MS.milliseconds
    private const val MILLIS_PER_SECOND = 1000
}
