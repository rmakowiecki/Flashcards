package com.rossomak.flashcards.core.voice

import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Turns raw PCM frames into a smoothed `0..1` microphone input level: frame RMS in dBFS, mapped
 * linearly from [SILENCE_DBFS] to [FULL_SCALE_DBFS], then smoothed with a fast attack and a slow
 * decay. Output below [NOISE_FLOOR] reads as 0.
 *
 * Reacts to any sound, not only VAD-detected speech. Keeps no audio: only the last level survives
 * a frame.
 */
internal class InputLevelMeter(frameDuration: Duration = VoiceCaptureEngine.FRAME_DURATION_MS.milliseconds) {

    private val attackCoefficient = smoothingCoefficient(frameDuration, ATTACK_TIME)
    private val decayCoefficient = smoothingCoefficient(frameDuration, DECAY_TIME)
    private var smoothedLevel = 0f

    /** Feeds one frame of [sampleCount] samples and returns the new level. */
    fun process(frame: ShortArray, sampleCount: Int): Float {
        val frameLevel = frameLevel(frame, sampleCount)
        val coefficient = if (frameLevel > smoothedLevel) attackCoefficient else decayCoefficient
        smoothedLevel += (frameLevel - smoothedLevel) * coefficient
        return if (smoothedLevel < NOISE_FLOOR) 0f else smoothedLevel
    }

    companion object {
        private const val SILENCE_DBFS = -50f
        private const val FULL_SCALE_DBFS = -10f
        private const val NOISE_FLOOR = 0.05f
        private const val FULL_SCALE_AMPLITUDE = 32_768f
        private const val DECIBELS_PER_DECADE = 20f
        private val ATTACK_TIME = 30.milliseconds
        private val DECAY_TIME = 250.milliseconds

        /** Unsmoothed level of one frame: RMS in dBFS mapped to `0..1`, clamped. */
        fun frameLevel(frame: ShortArray, sampleCount: Int): Float {
            if (sampleCount <= 0) return 0f
            var sumOfSquares = 0.0
            for (index in 0 until sampleCount) {
                val sample = frame[index] / FULL_SCALE_AMPLITUDE
                sumOfSquares += sample * sample
            }
            val rms = sqrt(sumOfSquares / sampleCount).toFloat()
            if (rms <= 0f) return 0f
            val dbfs = DECIBELS_PER_DECADE * log10(rms)
            return ((dbfs - SILENCE_DBFS) / (FULL_SCALE_DBFS - SILENCE_DBFS)).coerceIn(0f, 1f)
        }

        private fun smoothingCoefficient(frameDuration: Duration, timeConstant: Duration): Float =
            (1 - exp(-frameDuration.inWholeMicroseconds.toDouble() / timeConstant.inWholeMicroseconds)).toFloat()
    }
}
