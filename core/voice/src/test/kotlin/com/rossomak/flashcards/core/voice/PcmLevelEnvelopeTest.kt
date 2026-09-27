package com.rossomak.flashcards.core.voice

import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.Test

class PcmLevelEnvelopeTest {

    private val sampleRateHz = VoiceCaptureEngine.SAMPLE_RATE_HZ
    private val frameSizeSamples = VoiceCaptureEngine.FRAME_SIZE_SAMPLES

    private fun constantClip(amplitude: Int, frameCount: Int): ShortArray =
        ShortArray(frameSizeSamples * frameCount) { amplitude.toShort() }

    @Test
    fun `an empty clip has no levels`() {
        PcmLevelEnvelope.compute(ShortArray(0), sampleRateHz).size shouldBe 0
    }

    @Test
    fun `yields one level per frame, counting a trailing partial frame`() {
        val clip = ShortArray(frameSizeSamples * 3 + frameSizeSamples / 2)

        PcmLevelEnvelope.compute(clip, sampleRateHz).size shouldBe 4
    }

    @Test
    fun `frame size follows the sample rate`() {
        PcmLevelEnvelope.frameSizeSamples(sampleRateHz) shouldBe frameSizeSamples
        PcmLevelEnvelope.frameSizeSamples(sampleRateHz = 48_000) shouldBe 960
    }

    @Test
    fun `a silent clip stays at zero`() {
        PcmLevelEnvelope.compute(constantClip(amplitude = 0, frameCount = 10), sampleRateHz).toList() shouldBe List(10) { 0f }
    }

    @Test
    fun `matches the live input level meter frame for frame`() {
        val clip = constantClip(amplitude = 3_000, frameCount = 5) + constantClip(amplitude = 0, frameCount = 5)
        val meter = InputLevelMeter()
        val expected = (0 until 10).map { frameIndex ->
            meter.process(clip.copyOfRange(frameIndex * frameSizeSamples, (frameIndex + 1) * frameSizeSamples), frameSizeSamples)
        }

        PcmLevelEnvelope.compute(clip, sampleRateHz).toList() shouldBe expected
    }

    @Test
    fun `rises on loud audio and decays after it`() {
        val clip = constantClip(amplitude = Short.MAX_VALUE.toInt(), frameCount = 5) + constantClip(amplitude = 0, frameCount = 5)

        val levels = PcmLevelEnvelope.compute(clip, sampleRateHz)

        levels[4] shouldBeGreaterThan 0.9f
        levels[9] shouldBeLessThan levels[4]
        levels[9] shouldBeGreaterThan 0f
    }
}
