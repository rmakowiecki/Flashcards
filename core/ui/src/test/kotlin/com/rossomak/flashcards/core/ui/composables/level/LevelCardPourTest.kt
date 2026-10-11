package com.rossomak.flashcards.core.ui.composables.level

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LevelCardPourTest {

    private val start = LevelCardPosition(level = 7, xpIntoLevel = 2_300, xpForNextLevel = 6_000)

    @Test
    fun `moveTo with animations off snaps to a higher Level`() = runTest {
        val pour = LevelCardPour(start)
        val target = LevelCardPosition(level = 9, xpIntoLevel = 400, xpForNextLevel = 8_000)

        pour.moveTo(target, animate = false)

        pour.shows(target)
    }

    @Test
    fun `moveTo a lower Level snaps even with animations on`() = runTest {
        val pour = LevelCardPour(start)
        val lower = LevelCardPosition(level = 2, xpIntoLevel = 150, xpForNextLevel = 1_500)

        pour.moveTo(lower, animate = true)

        pour.shows(lower)
    }

    @Test
    fun `moveTo the position already shown leaves it in place`() = runTest {
        val pour = LevelCardPour(start)

        pour.moveTo(start, animate = true)

        pour.shows(start)
    }

    private fun LevelCardPour.shows(position: LevelCardPosition) {
        level shouldBe position.level
        xpForNextLevel shouldBe position.xpForNextLevel
        xpIntoLevel.value shouldBe position.xpIntoLevel.toFloat()
        fraction.value shouldBe position.fraction
        showsXpCount shouldBe true
        levelScale.value shouldBe 1f
    }
}
