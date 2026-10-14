package com.rossomak.networkgraph

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlinx.coroutines.currentCoroutineContext

/** The longest gap between two frames a catch-up advances over, so a stalled or paused clock resumes where it stopped. */
private const val MAX_FRAME_SECONDS = 1f / 30f
private const val MIN_FRAME_SECONDS = 1f / 1_000f
private const val NANOS_PER_SECOND = 1_000_000_000f

/** How far from the position a catch-up starts slowing down, in specs: it then stops in half the minimum morph duration. */
private const val TAIL_DISTANCE = 0.25f

/** How close a catch-up has to get before it lands on the position. */
private const val LANDING_DISTANCE = 0.001f

/**
 * Feeds a [MorphFollower] the followed position, but never faster than one spec per minimum morph
 * duration, so a quick flick or a pager's own short scroll animation still morphs over that long.
 *
 * Slower motion reaches the follower as it is, so scrubbing stays exact. Faster motion leaves the
 * background behind; it then moves at the cap until close, slows down to land on the position, and
 * follows it exactly again from there. A position turning back while it lags just turns the
 * background around. A target beyond the pair of specs the background shows, such as a page several
 * Next taps ahead, skips the specs in between: the background morphs from the screen straight to it
 * over the minimum duration, starting again from the screen whenever the target changes. A jump of
 * `jumpThreshold` or more goes to the follower's timed morph, with no cap. The minimum is scaled by
 * the context's [MotionDurationScale], so "Remove animations" lifts it.
 */
internal class LimitedMorphFollower(
    private val follower: MorphFollower,
    jumpThreshold: Float,
    private val position: () -> Float,
    private val target: () -> Int?,
    private val specs: () -> List<NetworkGraphSpec>,
    private val animationSpec: () -> AnimationSpec<Float>,
    private val minMorphDuration: () -> Duration,
) {
    private val limiter = MorphSpeedLimiter(jumpThreshold)

    /** Hands the follower the position, then, while the background lags it, the next step every frame until it catches up. */
    suspend fun update() {
        do {
            val minMorphSeconds = minMorphDuration().toDouble(DurationUnit.SECONDS).toFloat() * durationScale()
            val followedSpecs = specs()
            if (minMorphSeconds <= 0f || followedSpecs.isEmpty()) {
                val followed = position()
                limiter.reset(followed.coerceIn(0f, max(followedSpecs.lastIndex, 0).toFloat()))
                follower.follow(followed, followedSpecs, animationSpec())
                return
            }
            val frameNanos = withFrameNanos { it }
            val followed = position().coerceIn(0f, followedSpecs.lastIndex.toFloat())
            val heading = target()?.coerceIn(0, followedSpecs.lastIndex)
            when (val step = limiter.step(followed, heading, frameNanos, minMorphSeconds)) {
                is MorphStep.Follow -> follower.follow(step.position, followedSpecs, animationSpec())
                is MorphStep.Jump -> follower.jumpTo(step.position, followedSpecs, animationSpec())
                is MorphStep.Retarget -> follower.morphToward(step.target, step.fraction, followedSpecs)
                MorphStep.Hold -> Unit
            }
        } while (!limiter.isCaughtUp)
    }

    private suspend fun durationScale(): Float = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
}

/** What a capped background does on one frame. */
internal sealed interface MorphStep {
    /** Follows [position] along the pairs of specs. */
    data class Follow(val position: Float) : MorphStep

    /** Runs a timed morph to the spec nearest [position], which jumped there. */
    data class Jump(val position: Float) : MorphStep

    /** Shows the morph from the screen toward the spec at [target] at [fraction]; 1 lands on it. */
    data class Retarget(val target: Int, val fraction: Float) : MorphStep

    /** Keeps the screen as it is. */
    data object Hold : MorphStep
}

/**
 * The position a capped background shows: it trails the followed position by no more than its speed
 * allows, or, for a heading beyond the pair it shows, morphs straight there over the minimum duration.
 */
internal class MorphSpeedLimiter(private val jumpThreshold: Float) {
    private var shown = Float.NaN
    private var lastFollowed = Float.NaN
    private var lastFrameNanos: Long? = null

    /** The spec a retarget morphs to, or has landed on and holds until the position gets there too. */
    private var retarget: Int? = null
    private var retargetSeconds = 0f

    /** True once the shown position is the followed one. */
    val isCaughtUp: Boolean get() = retarget == null && shown == lastFollowed

    /** Shows [position] as it is, with nothing to catch up. */
    fun reset(position: Float) {
        shown = position
        lastFollowed = position
        lastFrameNanos = null
        retarget = null
    }

    /**
     * What to show for [followed] at [frameNanos]. [heading] is the spec the position is heading for,
     * or null to infer it from the position's direction of travel.
     */
    fun step(followed: Float, heading: Int?, frameNanos: Long, minMorphSeconds: Float): MorphStep {
        val previousFrameNanos = lastFrameNanos
        val previousFollowed = lastFollowed
        if (shown.isNaN() || abs(followed - previousFollowed) >= jumpThreshold) {
            val jumped = !shown.isNaN()
            reset(followed)
            lastFrameNanos = frameNanos
            return if (jumped) MorphStep.Jump(followed) else MorphStep.Follow(followed)
        }
        lastFrameNanos = frameNanos
        lastFollowed = followed
        val elapsedSeconds = previousFrameNanos?.let { (frameNanos - it) / NANOS_PER_SECOND } ?: MAX_FRAME_SECONDS
        val elapsed = elapsedSeconds.coerceIn(MIN_FRAME_SECONDS, MAX_FRAME_SECONDS)
        val target = heading ?: inferredHeading(followed, previousFollowed)
        val current = retarget
        return when {
            current == null && abs(target - shown) > 1f -> startRetarget(target, elapsed, minMorphSeconds)
            current == null -> MorphStep.Follow(capped(followed, previousFollowed, elapsed, minMorphSeconds))
            target != current && (shown != current.toFloat() || abs(target - shown) > 1f) -> startRetarget(target, elapsed, minMorphSeconds)
            target != current -> {
                // Landed, and the position heads elsewhere close by: catch up with it from the landed spec.
                retarget = null
                MorphStep.Follow(capped(followed, previousFollowed, elapsed, minMorphSeconds))
            }
            shown != current.toFloat() -> advanceRetarget(current, elapsed, minMorphSeconds)
            abs(followed - shown) < LANDING_DISTANCE -> {
                retarget = null
                shown = followed
                MorphStep.Follow(followed)
            }
            // Landed ahead of the position: hold the spec until the position gets there.
            else -> MorphStep.Hold
        }
    }

    private fun startRetarget(target: Int, elapsed: Float, minMorphSeconds: Float): MorphStep {
        retarget = target
        retargetSeconds = 0f
        shown = Float.POSITIVE_INFINITY
        return advanceRetarget(target, elapsed, minMorphSeconds)
    }

    private fun advanceRetarget(target: Int, elapsed: Float, minMorphSeconds: Float): MorphStep {
        retargetSeconds += elapsed
        val progress = retargetSeconds / minMorphSeconds
        if (progress < 1f) return MorphStep.Retarget(target, LinearOutSlowInEasing.transform(progress))
        shown = target.toFloat()
        return MorphStep.Retarget(target, 1f)
    }

    /** The shown position moved toward [followed] by no more than the cap allows. */
    private fun capped(followed: Float, previousFollowed: Float, elapsed: Float, minMorphSeconds: Float): Float {
        val remaining = followed - shown
        val distance = abs(remaining)
        val maxTravel = elapsed / minMorphSeconds
        // Never less than the position moved, so motion under the cap is followed exactly.
        val followedTravel = abs(followed - previousFollowed)
        val tailTravel = maxTravel * sqrt(distance / TAIL_DISTANCE)
        val travel = min(maxTravel, max(followedTravel, tailTravel))
        shown = if (travel >= distance || distance < LANDING_DISTANCE) followed else shown + sign(remaining) * travel
        return shown
    }

    /** The whole position [followed] moves toward, or the nearest one while it holds still. */
    private fun inferredHeading(followed: Float, previousFollowed: Float): Int = when {
        followed > previousFollowed -> ceil(followed).toInt()
        followed < previousFollowed -> floor(followed).toInt()
        else -> followed.roundToInt()
    }
}
