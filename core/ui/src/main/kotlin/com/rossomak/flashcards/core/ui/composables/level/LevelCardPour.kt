package com.rossomak.flashcards.core.ui.composables.level

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** How long the whole pour takes when all of its XP lands in one segment. */
internal val POUR_BASE: Duration = 1_500.milliseconds

/** The floor of the first and last pour segment, however small their share of the XP. */
internal val POUR_SEGMENT_MIN: Duration = 520.milliseconds

/** A Level crossed in the middle of a pour: its bar fills and resets, nothing else. */
internal val POUR_INTERMEDIATE: Duration = 300.milliseconds
internal val POUR_INTERMEDIATE_MIN: Duration = 80.milliseconds

/** The whole pour stays at or under this, unless the floors alone exceed it. */
internal val POUR_CAP: Duration = 2_500.milliseconds

/** The Level number's scale up and back down at every Level crossed. */
internal val LEVEL_PULSE: Duration = 250.milliseconds
internal const val LEVEL_PULSE_SCALE = 1.15f

/** Cubic ease-out, the curve of the bar and the XP count while they pour. */
private val PourEasing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

/** Where a User stands in a Level: the Level, the XP held in it and the XP that completes it. */
internal data class LevelCardPosition(
    val level: Int,
    val xpIntoLevel: Long,
    val xpForNextLevel: Long,
) {
    /** The bar's fill ([levelCardProgress]). */
    val fraction: Float
        get() = levelCardProgress(xpIntoLevel, xpForNextLevel)
}

/**
 * One stretch of a pour inside a single Level: the bar runs from [fromFraction] to [toFraction] while
 * the "x / y XP" text counts from [xpFrom] to [xpTo] out of [xpForNextLevel]. A Level crossed in the
 * middle of a pour hides that text ([showXpCount] `false`), because its threshold is not known.
 */
internal data class LevelCardPourSegment(
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
 * The segments that pour the bar from [from] to [to], in order. Empty when nothing moves. A [to] below
 * [from]'s Level is not a pour; the card snaps to it instead and never asks for one.
 *
 * - **Same Level:** one segment, for a gain and a loss alike. A loss never leaves its Level, so the bar
 *   simply drains.
 * - **n Levels up:** the first segment fills [from]'s Level to 100%. n − 1 middle segments each fill a
 *   whole Level, showing only the bar and the Level number. The last segment fills [to]'s Level from 0
 *   to its position. At every cut the bar snaps to 0, the Level number moves on and the text restarts
 *   at 0.
 *
 * Durations: the first and last segment share [POUR_BASE] in proportion to their XP, never less than
 * [POUR_SEGMENT_MIN] each. A middle segment takes [POUR_INTERMEDIATE]. When the whole pour would exceed
 * [POUR_CAP], the middle segments shrink first, down to [POUR_INTERMEDIATE_MIN], and only then the first
 * and last, down to their floor. A last segment that does not move (a Level-up exactly to the boundary)
 * takes no time of its own.
 */
internal fun planLevelCardPour(from: LevelCardPosition, to: LevelCardPosition): List<LevelCardPourSegment> = when {
    to.level > from.level -> planLevelUpPour(from, to)
    from.xpIntoLevel == to.xpIntoLevel && from.fraction == to.fraction -> emptyList()
    else -> listOf(
        LevelCardPourSegment(
            level = to.level,
            fromFraction = from.fraction,
            toFraction = to.fraction,
            xpForNextLevel = to.xpForNextLevel,
            xpFrom = from.xpIntoLevel,
            xpTo = to.xpIntoLevel,
            showXpCount = true,
            duration = POUR_BASE,
        ),
    )
}

private fun planLevelUpPour(from: LevelCardPosition, to: LevelCardPosition): List<LevelCardPourSegment> {
    val middleLevels = (from.level + 1 until to.level).toList()
    val firstXp = (from.xpForNextLevel - from.xpIntoLevel).coerceAtLeast(0L)
    val lastXp = to.xpIntoLevel.coerceAtLeast(0L)
    val durations = fitToCap(
        first = shareOfBase(firstXp, firstXp + lastXp),
        last = shareOfBase(lastXp, firstXp + lastXp),
        middleCount = middleLevels.size,
    )
    val lastMoves = to.fraction > 0f || to.xpIntoLevel > 0L

    val first = LevelCardPourSegment(
        level = from.level,
        fromFraction = from.fraction,
        toFraction = 1f,
        xpForNextLevel = from.xpForNextLevel,
        xpFrom = from.xpIntoLevel,
        xpTo = from.xpForNextLevel,
        showXpCount = true,
        duration = durations.first,
    )
    val middle = middleLevels.map { level ->
        LevelCardPourSegment(
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
    val last = LevelCardPourSegment(
        level = to.level,
        fromFraction = 0f,
        toFraction = to.fraction,
        xpForNextLevel = to.xpForNextLevel,
        xpFrom = 0L,
        xpTo = to.xpIntoLevel,
        showXpCount = true,
        duration = if (lastMoves) durations.last else Duration.ZERO,
    )
    return listOf(first) + middle + last
}

private fun shareOfBase(segmentXp: Long, pouredXp: Long): Duration {
    val totalXp = pouredXp.coerceAtLeast(1L).toDouble()
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

/**
 * What the Level card's figures show right now, moving from one [LevelCardPosition] to the next.
 * [fraction], [xpIntoLevel] and [levelScale] change every frame and are read in draw or in a
 * `derivedStateOf`; [level], [xpForNextLevel] and [showsXpCount] change only at a cut, so composition
 * reads them directly.
 */
@Stable
internal class LevelCardPour(initial: LevelCardPosition) {
    var level by mutableIntStateOf(initial.level)
        private set
    var xpForNextLevel by mutableLongStateOf(initial.xpForNextLevel)
        private set
    var showsXpCount by mutableStateOf(true)
        private set
    val fraction = Animatable(initial.fraction)
    val xpIntoLevel = Animatable(initial.xpIntoLevel.toFloat())
    val levelScale = Animatable(1f)

    /**
     * Pours from where the figures stand now to [target], or snaps there when [animate] is off or
     * [target] is a lower Level. Cancelled part-way (a newer target arrived), it leaves the figures
     * where they were, and the next call pours on from there.
     */
    suspend fun moveTo(target: LevelCardPosition, animate: Boolean) {
        levelScale.snapTo(1f)
        val current = LevelCardPosition(level, xpIntoLevel.value.roundToLong(), xpForNextLevel)
        if (!animate || target.level < current.level) {
            snapTo(target)
            return
        }
        coroutineScope {
            planLevelCardPour(current, target).forEachIndexed { index, segment ->
                if (index > 0) launch { pulseLevel() }
                level = segment.level
                showsXpCount = segment.showXpCount
                xpForNextLevel = segment.xpForNextLevel
                fraction.snapTo(segment.fromFraction)
                xpIntoLevel.snapTo(segment.xpFrom.toFloat())
                pourSegment(segment)
            }
        }
        snapTo(target)
    }

    private suspend fun pourSegment(segment: LevelCardPourSegment) {
        if (segment.duration <= Duration.ZERO) {
            fraction.snapTo(segment.toFraction)
            xpIntoLevel.snapTo(segment.xpTo.toFloat())
            return
        }
        val spec = tween<Float>(segment.duration.inWholeMilliseconds.toInt(), easing = PourEasing)
        coroutineScope {
            launch { fraction.animateTo(segment.toFraction, spec) }
            launch { xpIntoLevel.animateTo(segment.xpTo.toFloat(), spec) }
        }
    }

    private suspend fun pulseLevel() {
        val half = tween<Float>((LEVEL_PULSE / 2).inWholeMilliseconds.toInt())
        levelScale.animateTo(LEVEL_PULSE_SCALE, half)
        levelScale.animateTo(1f, half)
    }

    private suspend fun snapTo(target: LevelCardPosition) {
        level = target.level
        xpForNextLevel = target.xpForNextLevel
        showsXpCount = true
        fraction.snapTo(target.fraction)
        xpIntoLevel.snapTo(target.xpIntoLevel.toFloat())
        levelScale.snapTo(1f)
    }
}
