package com.rossomak.flashcards.core.ui.composables.level

import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import org.junit.Test

class LevelCardPourPlanTest {

    private val sameLevelBefore = LevelCardPosition(level = 7, xpIntoLevel = 2_300, xpForNextLevel = 6_000)
    private val levelUpBefore = LevelCardPosition(level = 12, xpIntoLevel = 2_840, xpForNextLevel = 5_000)
    private val levelUpAfter = LevelCardPosition(level = 13, xpIntoLevel = 1_520, xpForNextLevel = 6_000)

    @Test
    fun `a gain inside one Level pours in a single segment`() {
        val after = sameLevelBefore.copy(xpIntoLevel = 3_000)

        val plan = planLevelCardPour(sameLevelBefore, after)

        plan shouldBe listOf(
            LevelCardPourSegment(
                level = after.level,
                fromFraction = sameLevelBefore.fraction,
                toFraction = after.fraction,
                xpForNextLevel = after.xpForNextLevel,
                xpFrom = sameLevelBefore.xpIntoLevel,
                xpTo = after.xpIntoLevel,
                showXpCount = true,
                duration = POUR_SEGMENT,
            ),
        )
    }

    @Test
    fun `a loss drains the bar within the same Level and counts the text down`() {
        val after = sameLevelBefore.copy(xpIntoLevel = 2_060)

        val plan = planLevelCardPour(sameLevelBefore, after)

        val segment = plan.single()
        segment.level shouldBe sameLevelBefore.level
        segment.fromFraction shouldBe sameLevelBefore.fraction
        segment.toFraction shouldBe after.fraction
        (segment.toFraction < segment.fromFraction) shouldBe true
        segment.xpFrom shouldBe sameLevelBefore.xpIntoLevel
        segment.xpTo shouldBe after.xpIntoLevel
    }

    @Test
    fun `a position that does not move pours nothing`() {
        planLevelCardPour(sameLevelBefore, sameLevelBefore) shouldBe emptyList()
    }

    @Test
    fun `one Level-up fills the old Level then pours the rest into the new one`() {
        val plan = planLevelCardPour(levelUpBefore, levelUpAfter)

        plan.size shouldBe 2
        val (first, last) = plan
        first.level shouldBe levelUpBefore.level
        first.fromFraction shouldBe levelUpBefore.fraction
        first.toFraction shouldBe 1f
        first.xpFrom shouldBe levelUpBefore.xpIntoLevel
        first.xpTo shouldBe levelUpBefore.xpForNextLevel
        first.xpForNextLevel shouldBe levelUpBefore.xpForNextLevel
        last.level shouldBe levelUpAfter.level
        last.fromFraction shouldBe 0f
        last.toFraction shouldBe levelUpAfter.fraction
        last.xpFrom shouldBe 0L
        last.xpTo shouldBe levelUpAfter.xpIntoLevel
        last.xpForNextLevel shouldBe levelUpAfter.xpForNextLevel
        listOf(first, last).all { it.showXpCount } shouldBe true
    }

    @Test
    fun `three Levels crossed pour the middle ones as a bar and a Level number only`() {
        val after = LevelCardPosition(level = 15, xpIntoLevel = 900, xpForNextLevel = 8_000)

        val plan = planLevelCardPour(levelUpBefore, after)

        plan.map { it.level } shouldBe listOf(levelUpBefore.level, 13, 14, 15)
        val middle = plan.subList(1, plan.size - 1)
        middle.map { it.showXpCount } shouldBe listOf(false, false)
        middle.map { it.fromFraction } shouldBe listOf(0f, 0f)
        middle.map { it.toFraction } shouldBe listOf(1f, 1f)
        plan.map { it.duration } shouldBe listOf(POUR_SEGMENT, POUR_INTERMEDIATE, POUR_INTERMEDIATE, POUR_SEGMENT)
        plan.last().xpTo shouldBe after.xpIntoLevel
        plan.last().xpForNextLevel shouldBe after.xpForNextLevel
    }

    @Test
    fun `a Level-up exactly to the boundary ends on an empty bar without a pour of its own`() {
        val boundary = LevelCardPosition(level = 13, xpIntoLevel = 0, xpForNextLevel = 6_000)

        val plan = planLevelCardPour(levelUpBefore, boundary)

        val last = plan.last()
        last.level shouldBe boundary.level
        last.toFraction shouldBe 0f
        last.xpTo shouldBe 0L
        last.duration shouldBe Duration.ZERO
    }

    @Test
    fun `a Level with no known threshold is an empty bar and never divides by zero`() {
        val unknown = LevelCardPosition(level = 3, xpIntoLevel = 100, xpForNextLevel = 0)
        val unknownAfter = unknown.copy(xpIntoLevel = 250)

        val plan = planLevelCardPour(unknown, unknownAfter)

        unknown.fraction shouldBe 0f
        plan.single().fromFraction shouldBe 0f
        plan.single().toFraction shouldBe 0f
    }
}
