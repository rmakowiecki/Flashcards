package com.rossomak.flashcards.core.domain.model

import com.rossomak.flashcards.core.domain.scoring.XpScoringCases
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.Test

class XpConfigTest {

    @Test
    fun `the default constructor values match the shared default configuration file`() {
        XpConfig() shouldBe XpScoringCases.defaultConfig
    }

    @Test
    fun `an empty step list is rejected`() {
        shouldThrow<IllegalArgumentException> { XpConfig(levelCostSteps = emptyList()) }
            .message shouldContain "levelCostSteps"
    }

    @Test
    fun `a zero or negative step is rejected`() {
        shouldThrow<IllegalArgumentException> { XpConfig(levelCostSteps = listOf(100, 0)) }
            .message shouldContain "levelCostSteps"
        shouldThrow<IllegalArgumentException> { XpConfig(levelCostSteps = listOf(100, -300)) }
            .message shouldContain "levelCostSteps"
    }

    @Test
    fun `a zero or negative tier length is rejected`() {
        shouldThrow<IllegalArgumentException> { XpConfig(levelsPerCostStep = 0) }
            .message shouldContain "levelsPerCostStep"
        shouldThrow<IllegalArgumentException> { XpConfig(levelsPerCostStep = -5) }
            .message shouldContain "levelsPerCostStep"
    }

    @Test
    fun `the last step caps every later tier when a tier spans two levels`() {
        val config = XpConfig(levelBaseCost = 1000, levelCostSteps = listOf(100, 300), levelsPerCostStep = 2)

        config.levelThreshold(1) shouldBe 1000
        config.levelThreshold(2) shouldBe 1100
        config.levelThreshold(3) shouldBe 1400
        config.levelThreshold(4) shouldBe 1700
        config.levelThreshold(5) shouldBe 2000
        config.levelThreshold(10) shouldBe 3500
    }
}
