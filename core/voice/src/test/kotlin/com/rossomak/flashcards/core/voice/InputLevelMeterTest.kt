package com.rossomak.flashcards.core.voice

import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.Test

class InputLevelMeterTest {

    private fun constantFrame(amplitude: Int): ShortArray =
        ShortArray(VoiceCaptureEngine.FRAME_SIZE_SAMPLES) { amplitude.toShort() }

    private fun InputLevelMeter.processFrames(frame: ShortArray, frameCount: Int): Float {
        var level = 0f
        repeat(frameCount) { level = process(frame, frame.size) }
        return level
    }

    @Test
    fun `frameLevel of silence is zero`() {
        val silence = constantFrame(amplitude = 0)

        InputLevelMeter.frameLevel(silence, silence.size) shouldBe 0f
    }

    @Test
    fun `frameLevel of a near full-scale frame is one`() {
        val fullScale = constantFrame(amplitude = Short.MAX_VALUE.toInt())

        InputLevelMeter.frameLevel(fullScale, fullScale.size) shouldBe 1f
    }

    @Test
    fun `frameLevel grows monotonically with amplitude`() {
        val levels = listOf(100, 300, 1_000, 3_000, 10_000).map { amplitude ->
            val frame = constantFrame(amplitude)
            InputLevelMeter.frameLevel(frame, frame.size)
        }

        levels.zipWithNext().forEach { (quieter, louder) -> (louder >= quieter) shouldBe true }
        levels.last() shouldBeGreaterThan levels.first()
    }

    @Test
    fun `frameLevel of an empty read is zero`() {
        InputLevelMeter.frameLevel(constantFrame(amplitude = 10_000), sampleCount = 0) shouldBe 0f
    }

    @Test
    fun `level below the noise floor reads as zero`() {
        val meter = InputLevelMeter()

        meter.processFrames(constantFrame(amplitude = 110), frameCount = 50) shouldBe 0f
    }

    @Test
    fun `level rises fast and decays slowly`() {
        val meter = InputLevelMeter()

        val risenLevel = meter.processFrames(constantFrame(amplitude = Short.MAX_VALUE.toInt()), frameCount = 5)
        val decayedLevel = meter.processFrames(constantFrame(amplitude = 0), frameCount = 5)

        risenLevel shouldBeGreaterThan 0.9f
        decayedLevel shouldBeGreaterThan 0.5f
        decayedLevel shouldBeLessThan risenLevel
    }
}
