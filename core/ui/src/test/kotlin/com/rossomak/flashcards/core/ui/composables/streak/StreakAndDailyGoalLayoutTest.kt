package com.rossomak.flashcards.core.ui.composables.streak

import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import org.junit.Test

class StreakAndDailyGoalLayoutTest {

    @Test
    fun `streakAndDailyGoalCardsStack with room for two 150 dp cards keeps them side by side`() {
        streakAndDailyGoalCardsStack(availableWidth = WIDTH_FOR_TWO_MIN_CARDS, cardSpacing = CARD_SPACING) shouldBe false
    }

    @Test
    fun `streakAndDailyGoalCardsStack with less than 150 dp per card stacks them`() {
        streakAndDailyGoalCardsStack(availableWidth = WIDTH_SHORT_OF_TWO_MIN_CARDS, cardSpacing = CARD_SPACING) shouldBe true
    }

    @Test
    fun `dailyGoalRingDiameter with small content returns the minimum diameter`() {
        dailyGoalRingDiameter(contentWidth = 30, contentHeight = 40, strokeWidth = 6, minDiameter = 88) shouldBe 88
    }

    @Test
    fun `dailyGoalRingDiameter with large content fits its diagonal inside the stroke`() {
        dailyGoalRingDiameter(contentWidth = 90, contentHeight = 120, strokeWidth = 6, minDiameter = 88) shouldBe 162
    }

    @Test
    fun `dailyGoalRingDiameter rounds a fractional diagonal up`() {
        dailyGoalRingDiameter(contentWidth = 100, contentHeight = 1, strokeWidth = 0, minDiameter = 0) shouldBe 101
    }

    private companion object {
        val CARD_SPACING = 12.dp
        val WIDTH_FOR_TWO_MIN_CARDS = 312.dp
        val WIDTH_SHORT_OF_TWO_MIN_CARDS = 311.dp
    }
}
