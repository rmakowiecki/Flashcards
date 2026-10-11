package com.rossomak.flashcards.core.ui.composables.level

import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Test

class LevelCardPourPlanTest {

    private val sameLevelBefore = LevelCardPosition(level = 7, xpIntoLevel = 2_300, xpForNextLevel = 6_000)
    private val levelUpBefore = LevelCardPosition(level = 12, xpIntoLevel = 2_840, xpForNextLevel = 5_000)
    private val levelUpAfter = LevelCardPosition(level = 13, xpIntoLevel = 1_520, xpForNextLevel = 6_000)
    private val levelUpPouredXp = 3_680

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
                duration = POUR_BASE,
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
    fun `the first and last segments share the base duration in proportion to the XP they pour`() {
        val plan = planLevelCardPour(levelUpBefore, levelUpAfter)

        val baseMillis = POUR_BASE.inWholeMilliseconds.toDouble()
        val firstShare = (levelUpBefore.xpForNextLevel - levelUpBefore.xpIntoLevel) * baseMillis / levelUpPouredXp
        val lastShare = levelUpAfter.xpIntoLevel * baseMillis / levelUpPouredXp
        plan.first().duration.inWholeMilliseconds shouldBe Math.round(firstShare)
        plan.last().duration.inWholeMilliseconds shouldBe Math.round(lastShare)
    }

    @Test
    fun `a tiny share of the XP still gets the minimum duration`() {
        val barelyCrossed = LevelCardPosition(level = 13, xpIntoLevel = 5, xpForNextLevel = 6_000)

        val plan = planLevelCardPour(levelUpBefore, barelyCrossed)

        plan.last().duration shouldBe POUR_SEGMENT_MIN
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
        middle.map { it.duration } shouldBe listOf(POUR_INTERMEDIATE, POUR_INTERMEDIATE)
        plan.last().xpTo shouldBe after.xpIntoLevel
        plan.last().xpForNextLevel shouldBe after.xpForNextLevel
    }

    @Test
    fun `many Levels crossed shrink the middle segments first so the pour stays under the cap`() {
        val after = LevelCardPosition(level = 22, xpIntoLevel = 4_000, xpForNextLevel = 20_000)

        val plan = planLevelCardPour(levelUpBefore, after)

        val middle = plan.subList(1, plan.size - 1)
        middle.all { it.duration < POUR_INTERMEDIATE } shouldBe true
        middle.all { it.duration >= POUR_INTERMEDIATE_MIN } shouldBe true
        (plan.first().duration >= POUR_SEGMENT_MIN) shouldBe true
        (plan.last().duration >= POUR_SEGMENT_MIN) shouldBe true
        plan.fold(Duration.ZERO) { total, segment -> total + segment.duration } shouldBeLessThanOrEqualTo (POUR_CAP + 1.milliseconds)
    }

    @Test
    fun `an extreme number of Levels bottoms out the middle segments even above the cap`() {
        val after = LevelCardPosition(level = 60, xpIntoLevel = 4_000, xpForNextLevel = 20_000)

        val plan = planLevelCardPour(levelUpBefore, after)

        plan.subList(1, plan.size - 1).all { it.duration == POUR_INTERMEDIATE_MIN } shouldBe true
        plan.first().duration shouldBe POUR_SEGMENT_MIN
        (plan.last().duration >= POUR_SEGMENT_MIN) shouldBe true
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
