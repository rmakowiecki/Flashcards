package com.rossomak.flashcards.feature.study.summary

import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Where a User stands in a Level: the Level, the XP held in it and the XP that completes it. */
internal data class LevelPosition(
    val level: Int,
    val xpIntoLevel: Long,
    val xpForNextLevel: Long,
) {
    /** The bar's fill. A Level with no known threshold reads as an empty bar, never a division by zero. */
    val fraction: Float
        get() = if (xpForNextLevel <= 0L) 0f else (xpIntoLevel.toFloat() / xpForNextLevel.toFloat()).coerceIn(0f, 1f)
}

internal fun StudySessionSummaryScreenState.positionBefore() =
    LevelPosition(levelBefore, xpIntoCurrentLevelBefore, xpForNextLevelBefore)

internal fun StudySessionSummaryScreenState.positionAfter() = LevelPosition(level, xpIntoCurrentLevel, xpForNextLevel)

/**
 * One stretch of the Level pour inside a single Level: the bar runs from [fromFraction] to
 * [toFraction] while the "x / y XP" text counts from [xpFrom] to [xpTo] out of [xpForNextLevel]. A
 * Level crossed in the middle of a pour hides that text ([showXpCount] `false`), because its
 * threshold is not known.
 */
internal data class PourSegment(
    val level: Int,
    val fromFraction: Float,
    val toFraction: Float,
    val xpForNextLevel: Long,
    val xpFrom: Long,
    val xpTo: Long,
    val showXpCount: Boolean,
    val duration: Duration,
)

/**
 * The segments that pour a session's XP into the Level bar, in order. Empty when nothing moves.
 *
 * - **No Level crossed:** one segment from [before] to [after], for a gain and a loss alike. A loss
 *   never leaves its Level, so the bar simply drains.
 * - **n Levels crossed:** the first segment fills the Level the User was in to 100%. n − 1 middle
 *   segments each fill a whole Level, showing only the bar and the Level number. The last segment
 *   fills the Level the User ended in from 0 to its [after] position. At every cut the bar snaps to
 *   0, the Level number moves on and the text restarts at 0.
 *
 * Durations: the first and last segment take [POUR_BASE] in proportion to their share of [xpTotal],
 * never less than [POUR_SEGMENT_MIN]. A middle segment takes [POUR_INTERMEDIATE]. When the whole pour
 * would exceed [POUR_CAP], the middle segments shrink first, down to [POUR_INTERMEDIATE_MIN], and only
 * then the first and last, down to their floor. A last segment that does not move (a Level-up exactly
 * to the boundary) takes no time of its own.
 */
internal fun planPour(
    before: LevelPosition,
    after: LevelPosition,
    levelsCrossed: List<Int>,
    xpTotal: Int,
): List<PourSegment> = when {
    levelsCrossed.isNotEmpty() -> planLevelUpPour(before, after, levelsCrossed, xpTotal)
    before.xpIntoLevel == after.xpIntoLevel && before.fraction == after.fraction -> emptyList()
    else -> listOf(
        PourSegment(
            level = after.level,
            fromFraction = before.fraction,
            toFraction = after.fraction,
            xpForNextLevel = after.xpForNextLevel,
            xpFrom = before.xpIntoLevel,
            xpTo = after.xpIntoLevel,
            showXpCount = true,
            duration = POUR_BASE,
        ),
    )
}

private fun planLevelUpPour(
    before: LevelPosition,
    after: LevelPosition,
    levelsCrossed: List<Int>,
    xpTotal: Int,
): List<PourSegment> {
    val middleLevels = levelsCrossed.dropLast(1)
    val firstXp = (before.xpForNextLevel - before.xpIntoLevel).coerceAtLeast(0L)
    val lastXp = after.xpIntoLevel
    val durations = fitToCap(
        first = shareOfBase(firstXp, xpTotal),
        last = shareOfBase(lastXp, xpTotal),
        middleCount = middleLevels.size,
    )
    val lastMoves = after.fraction > 0f || after.xpIntoLevel > 0L

    val first = PourSegment(
        level = before.level,
        fromFraction = before.fraction,
        toFraction = 1f,
        xpForNextLevel = before.xpForNextLevel,
        xpFrom = before.xpIntoLevel,
        xpTo = before.xpForNextLevel,
        showXpCount = true,
        duration = durations.first,
    )
    val middle = middleLevels.map { level ->
        PourSegment(
            level = level,
            fromFraction = 0f,
            toFraction = 1f,
            xpForNextLevel = 0L,
            xpFrom = 0L,
            xpTo = 0L,
            showXpCount = false,
            duration = durations.middle,
        )
    }
    val last = PourSegment(
        level = levelsCrossed.last(),
        fromFraction = 0f,
        toFraction = after.fraction,
        xpForNextLevel = after.xpForNextLevel,
        xpFrom = 0L,
        xpTo = after.xpIntoLevel,
        showXpCount = true,
        duration = if (lastMoves) durations.last else Duration.ZERO,
    )
    return listOf(first) + middle + last
}

private fun shareOfBase(segmentXp: Long, xpTotal: Int): Duration {
    val totalXp = xpTotal.coerceAtLeast(1).toDouble()
    val proportional = (POUR_BASE.inWholeMilliseconds * segmentXp / totalXp).roundToLong().milliseconds
    return maxOf(POUR_SEGMENT_MIN, proportional)
}

private data class PourDurations(val first: Duration, val middle: Duration, val last: Duration)

private fun fitToCap(first: Duration, last: Duration, middleCount: Int): PourDurations {
    val middleTotal = { middle: Duration -> middle * middleCount }
    var middle = POUR_INTERMEDIATE
    if (first + last + middleTotal(middle) > POUR_CAP && middleCount > 0) {
        middle = ((POUR_CAP - first - last) / middleCount).coerceIn(POUR_INTERMEDIATE_MIN, POUR_INTERMEDIATE)
    }
    var fitFirst = first
    var fitLast = last
    if (fitFirst + fitLast + middleTotal(middle) > POUR_CAP) {
        val available = maxOf(POUR_CAP - middleTotal(middle), POUR_SEGMENT_MIN * 2)
        val scale = available / (fitFirst + fitLast)
        fitFirst = maxOf(POUR_SEGMENT_MIN, fitFirst * scale)
        fitLast = maxOf(POUR_SEGMENT_MIN, fitLast * scale)
    }
    return PourDurations(first = fitFirst, middle = middle, last = fitLast)
}
