package com.rossomak.flashcards.core.ui.composables.level

import io.kotest.matchers.shouldBe
import org.junit.Test

class FlashcardsLevelCardProgressTest {

    @Test
    fun `levelCardProgress with points into the level returns their share of the level`() {
        levelCardProgress(xpIntoCurrentLevel = 2_500L, xpForNextLevel = 10_000L) shouldBe 0.25f
    }

    @Test
    fun `levelCardProgress with no points into the level returns zero`() {
        levelCardProgress(xpIntoCurrentLevel = 0L, xpForNextLevel = 10_000L) shouldBe 0f
    }

    @Test
    fun `levelCardProgress with more points than the level needs returns one`() {
        levelCardProgress(xpIntoCurrentLevel = 12_000L, xpForNextLevel = 10_000L) shouldBe 1f
    }

    @Test
    fun `levelCardProgress with a level that needs no points returns zero`() {
        levelCardProgress(xpIntoCurrentLevel = 500L, xpForNextLevel = 0L) shouldBe 0f
    }

    @Test
    fun `levelCardProgress with a negative level size returns zero`() {
        levelCardProgress(xpIntoCurrentLevel = 500L, xpForNextLevel = -1L) shouldBe 0f
    }

    @Test
    fun `levelCardProgress with negative points into the level returns zero`() {
        levelCardProgress(xpIntoCurrentLevel = -500L, xpForNextLevel = 10_000L) shouldBe 0f
    }
}
