package com.rossomak.flashcards.core.domain.model

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test

class LevelProgressTest {

    @Test
    fun `progress is the share of the current Level earned`() {
        LevelProgress(level = 2, xpIntoCurrentLevel = 250, xpForNextLevel = 1000).progress shouldBe 0.25f
    }

    @Test
    fun `progress is zero when the threshold is not positive`() {
        LevelProgress(level = 2, xpIntoCurrentLevel = 250, xpForNextLevel = 0).progress shouldBe 0f
        LevelProgress(level = 2, xpIntoCurrentLevel = 250, xpForNextLevel = -1).progress shouldBe 0f
    }

    @Test
    fun `progress over a full Level clamps to one`() {
        LevelProgress(level = 2, xpIntoCurrentLevel = 1500, xpForNextLevel = 1000).progress shouldBe 1f
    }

    @Test
    fun `the starting scoring state with the default configuration is Level 1 with 0 of 1000 points`() {
        ScoringState().toLevelProgress(XpConfig()) shouldBe LevelProgress(level = 1, xpIntoCurrentLevel = 0, xpForNextLevel = 1000)
    }

    @Test
    fun `the threshold comes from the configuration's Level curve for the scoring state's Level`() {
        val config = XpConfig(levelCurveBase = 2000.0, levelCurveExponent = 2.0)
        val level = 2
        val xpIntoCurrentLevel = 300L

        ScoringState(xp = 9000, level = level, xpIntoCurrentLevel = xpIntoCurrentLevel).toLevelProgress(config) shouldBe
            LevelProgress(level = level, xpIntoCurrentLevel = xpIntoCurrentLevel, xpForNextLevel = config.levelThreshold(level))
    }

    @Test
    fun `equality depends on the three constructor values only`() {
        val level = 3
        val xpIntoCurrentLevel = 100L
        val xpForNextLevel = 16_000L
        val levelProgress = LevelProgress(level = level, xpIntoCurrentLevel = xpIntoCurrentLevel, xpForNextLevel = xpForNextLevel)

        levelProgress shouldBe LevelProgress(level = level, xpIntoCurrentLevel = xpIntoCurrentLevel, xpForNextLevel = xpForNextLevel)
        levelProgress shouldNotBe levelProgress.copy(level = level + 1)
        levelProgress shouldNotBe levelProgress.copy(xpIntoCurrentLevel = xpIntoCurrentLevel + 1)
        levelProgress shouldNotBe levelProgress.copy(xpForNextLevel = xpForNextLevel + 1)
    }
}
