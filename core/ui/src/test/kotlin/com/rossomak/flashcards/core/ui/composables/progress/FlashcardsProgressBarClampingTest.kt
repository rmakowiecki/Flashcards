package com.rossomak.flashcards.core.ui.composables.progress

import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * Documents `clampProgressBarProgress`/`clampSegmentCount`/`clampFilledSegmentCount` as
 * intentional, tested behavior — the progress bar family renders out-of-range domain data rather
 * than crashing on it (ADR-0035). These are regression tests for that contract, not a "should this
 * throw instead" debate.
 */
class FlashcardsProgressBarClampingTest {

    @Test
    fun `clampProgressBarProgress below zero clamps to zero`() {
        clampProgressBarProgress(-0.5f) shouldBe 0f
    }

    @Test
    fun `clampProgressBarProgress above one clamps to one`() {
        clampProgressBarProgress(1.5f) shouldBe 1f
    }

    @Test
    fun `clampProgressBarProgress within range is unchanged`() {
        clampProgressBarProgress(0.42f) shouldBe 0.42f
    }

    @Test
    fun `clampProgressBarProgress at exact bounds is unchanged`() {
        clampProgressBarProgress(0f) shouldBe 0f
        clampProgressBarProgress(1f) shouldBe 1f
    }

    @Test
    fun `clampProgressBarProgress with NaN falls back to zero`() {
        // Float#coerceIn does not catch NaN (IEEE 754 comparisons against NaN are always false),
        // so this must be checked explicitly rather than relying on coerceIn alone.
        clampProgressBarProgress(Float.NaN) shouldBe 0f
    }

    @Test
    fun `clampSegmentCount zero clamps to one`() {
        clampSegmentCount(0) shouldBe 1
    }

    @Test
    fun `clampSegmentCount negative clamps to one`() {
        clampSegmentCount(-3) shouldBe 1
    }

    @Test
    fun `clampSegmentCount positive is unchanged`() {
        clampSegmentCount(8) shouldBe 8
    }

    @Test
    fun `clampSegmentCount at the maximum is unchanged`() {
        clampSegmentCount(100) shouldBe 100
    }

    @Test
    fun `clampSegmentCount above the maximum clamps down`() {
        clampSegmentCount(101) shouldBe 100
    }

    @Test
    fun `clampSegmentCount with Int max value clamps to the maximum, not a runaway draw loop`() {
        clampSegmentCount(Int.MAX_VALUE) shouldBe 100
    }

    @Test
    fun `clampFilledSegmentCount negative clamps to zero`() {
        clampFilledSegmentCount(filledSegmentCount = -1, segmentCount = 8) shouldBe 0
    }

    @Test
    fun `clampFilledSegmentCount above segmentCount clamps to segmentCount`() {
        clampFilledSegmentCount(filledSegmentCount = 12, segmentCount = 8) shouldBe 8
    }

    @Test
    fun `clampFilledSegmentCount within range is unchanged`() {
        clampFilledSegmentCount(filledSegmentCount = 4, segmentCount = 8) shouldBe 4
    }
}
