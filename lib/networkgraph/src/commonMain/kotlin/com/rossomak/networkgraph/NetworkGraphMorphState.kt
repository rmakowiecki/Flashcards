package com.rossomak.networkgraph

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val DEFAULT_MORPH_MILLIS = 900

/** How many interrupted morphs a [NetworkGraphMorphState] keeps nested under the current one before dropping the oldest. */
private const val MAX_MORPH_NESTING = 4

/**
 * Which spec a network-graph background shows, and how it gets to the next one, for
 * [networkGraphBackground]'s state overload, which reads it while drawing so the background follows
 * every change without recomposing.
 *
 * Three ways to move it, all starting from exactly what is on screen, part way through an earlier
 * morph or not, so nothing ever jumps:
 * - [snapTo] shows a spec, or any [NetworkGraphMorph], at once;
 * - [seekTo] puts the morph toward a target at a fraction the caller drives: a drag, a slider, a scroll
 *   position. The screen when the target was first sought is kept as the morph's start, so seeking back
 *   to 0 returns to it exactly;
 * - [animateTo] runs a timed morph to a target with any [AnimationSpec]. Under the system's "Remove
 *   animations" setting it lands on the target at once.
 *
 * A call cancels whichever one is still running, so the last caller wins. [Follow] drives the state
 * from a position along a list of specs, such as a pager's.
 *
 * Not saved across configuration changes: a recreated screen starts from its `initial` spec.
 */
@Stable
public class NetworkGraphMorphState(initial: NetworkGraphSpec) {
    private var from by mutableStateOf(initial)
    private var to by mutableStateOf(initial)
    private var origin by mutableStateOf<NetworkGraphMorph?>(null)
    private val progress = Animatable(1f)

    /** The target [seekTo] last froze the screen for, while seeks to it keep that start. */
    private var seekTarget: NetworkGraphSpec? = null
    private val mutator = Mutator()

    /** What the background draws now. */
    public val morph: NetworkGraphMorph
        get() {
            val fraction = progress.value
            return NetworkGraphMorph(from, to, fraction, origin.takeIf { fraction < 1f })
        }

    /** The spec the state is heading for, or shows once it has arrived. */
    public val target: NetworkGraphSpec get() = to

    /** True while an [animateTo] runs. */
    public val isAnimating: Boolean get() = progress.isRunning

    /** Shows [spec] at once. */
    public suspend fun snapTo(spec: NetworkGraphSpec): Unit = snapTo(NetworkGraphMorph(spec, spec, 1f))

    /** Shows [morph] at once, as the stateless background overload would. */
    public suspend fun snapTo(morph: NetworkGraphMorph): Unit = mutator.mutate {
        seekTarget = null
        from = morph.from
        to = morph.to
        origin = morph.origin
        progress.snapTo(morph.fraction.coerceIn(0f, 1f))
    }

    /**
     * Shows the morph toward [target] at [fraction], from 0 (the start) to 1 ([target]). The first seek
     * toward a target starts its morph from the screen as it is; later seeks toward the same target keep
     * that start, so a fraction moving back and forth scrubs one morph. Any other call in between starts
     * the next seek afresh.
     */
    public suspend fun seekTo(target: NetworkGraphSpec, fraction: Float): Unit = mutator.mutate {
        if (target != seekTarget) {
            freezeScreen()
            to = target
            seekTarget = target
        }
        progress.snapTo(fraction.coerceIn(0f, 1f))
    }

    /** Morphs to [target] over [animationSpec], from the screen as it is; returns once it arrives. */
    public suspend fun animateTo(target: NetworkGraphSpec, animationSpec: AnimationSpec<Float> = DefaultAnimationSpec): Unit = mutator.mutate {
        seekTarget = null
        if (target == to && progress.value >= 1f) return@mutate
        freezeScreen()
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec)
    }

    /** True while the screen shows exactly [spec], with no morph part way. */
    internal fun isShowing(spec: NetworkGraphSpec): Boolean {
        val fraction = progress.value
        return (fraction >= 1f && to == spec) || (fraction <= 0f && origin == null && from == spec)
    }

    /** Makes the screen as it is the start of a new morph: the morph part way stays nested as its origin. */
    private fun freezeScreen() {
        val fraction = progress.value
        when {
            fraction >= 1f -> {
                from = to
                origin = null
            }
            fraction > 0f -> {
                val interrupted = NetworkGraphMorph(from, to, fraction, origin)
                from = lerp(from, to, fraction)
                origin = interrupted.withNestingAtMost(MAX_MORPH_NESTING)
            }
        }
    }

    public companion object {
        /** What [animateTo] and [Follow]'s jumps use unless told otherwise. */
        public val DefaultAnimationSpec: AnimationSpec<Float> = tween(DEFAULT_MORPH_MILLIS, easing = FastOutSlowInEasing)
    }
}

/** This morph with at most [levels] morphs nested under it; the oldest beyond that snaps to its interpolated [from]. */
private fun NetworkGraphMorph.withNestingAtMost(levels: Int): NetworkGraphMorph? = when {
    levels <= 0 -> null
    fraction <= 0f -> origin?.withNestingAtMost(levels)
    else -> copy(origin = origin?.withNestingAtMost(levels - 1))
}

/** A [NetworkGraphMorphState] showing [initial], kept across recompositions. */
@Composable
public fun rememberNetworkGraphMorphState(initial: NetworkGraphSpec): NetworkGraphMorphState = remember { NetworkGraphMorphState(initial) }

/**
 * A timed morph toward [target]: every time [target] changes, the background morphs from wherever it
 * is to the new target over [animationSpec], including from part way through an earlier morph.
 */
@Composable
public fun animateNetworkGraphMorph(target: NetworkGraphSpec, animationSpec: AnimationSpec<Float> = NetworkGraphMorphState.DefaultAnimationSpec): NetworkGraphMorph {
    val state = rememberNetworkGraphMorphState(target)
    val currentAnimationSpec by rememberUpdatedState(animationSpec)
    LaunchedEffect(target) { state.animateTo(target, currentAnimationSpec) }
    return state.morph
}

/**
 * Keeps this state on [specs] at [position], read through a snapshot, so a pager's scroll position or
 * any other snapshot-backed value drives it without recomposing: at 2.4 it shows the morph from
 * `specs[2]` to `specs[3]` at 0.4, scrubbing back exactly as the position moves back.
 *
 * A position that leaps [jumpThreshold] or more between two frames, as a pager's `scrollToPage` does,
 * morphs to the nearest spec over [animationSpec] instead. Moving the position again while that runs
 * takes over from the screen as it is: the morph then follows the position toward the next spec it
 * heads for, and once the position settles on a spec it is back to plain following, from that spec on.
 *
 * At first the state is left as it is unless it already shows the spec at [position], so a caller may
 * run an entrance of its own, such as an [NetworkGraphMorphState.animateTo] from a transparent spec;
 * the position's first move takes over from wherever that has got to. Any other call to the state does
 * the same: the next move continues from the screen.
 */
@Composable
public fun NetworkGraphMorphState.Follow(
    specs: List<NetworkGraphSpec>,
    jumpThreshold: Float = 1f,
    animationSpec: AnimationSpec<Float> = NetworkGraphMorphState.DefaultAnimationSpec,
    position: () -> Float,
) {
    val currentSpecs by rememberUpdatedState(specs)
    val currentPosition by rememberUpdatedState(position)
    val currentAnimationSpec by rememberUpdatedState(animationSpec)
    LaunchedEffect(this, jumpThreshold) {
        val follower = MorphFollower(this@Follow, jumpThreshold, this)
        snapshotFlow { currentPosition() to currentSpecs }.collect { (followed, followedSpecs) ->
            follower.follow(followed, followedSpecs, currentAnimationSpec)
        }
    }
}

/** Runs one mutation at a time: starting one cancels the one still running, then waits for it to let go. */
private class Mutator {
    private val lock = Mutex()
    private var current: Job? = null

    suspend fun <R> mutate(block: suspend () -> R): R = coroutineScope {
        val job = coroutineContext.job
        val previous = current
        current = job
        previous?.cancel()
        try {
            lock.withLock { block() }
        } finally {
            if (current === job) current = null
        }
    }
}
