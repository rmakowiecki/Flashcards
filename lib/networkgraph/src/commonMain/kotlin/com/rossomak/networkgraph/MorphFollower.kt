package com.rossomak.networkgraph

import androidx.compose.animation.core.AnimationSpec
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * What [Follow] does with each new position: shows the pair of specs the position lies between, runs a
 * timed morph for a jump, or, after a timed morph or another caller moved the state, seeks from the
 * screen toward the spec the position heads for until it settles on one.
 */
internal class MorphFollower(private val state: NetworkGraphMorphState, private val jumpThreshold: Float, private val scope: CoroutineScope) {
    private var lastPosition = Float.NaN
    private var mode: Mode = Mode.Seek(anchor = 0f)
    private var timedMorph: Job? = null

    /** The morph this follower last showed while following pairs, to notice anyone else moving the state. */
    private var shown: NetworkGraphMorph? = null

    /** The spec [morphToward] runs its morph to, while it does. */
    private var towards: Int? = null

    private sealed interface Mode {
        /** Showing the pair of specs the position lies between. */
        data object Pairs : Mode

        /** Seeking from the screen as it was at position [anchor] toward the next spec the position heads for. */
        data class Seek(val anchor: Float) : Mode

        /** A timed morph to [target] is running. */
        data class Timed(val target: Int) : Mode
    }

    suspend fun follow(position: Float, specs: List<NetworkGraphSpec>, animationSpec: AnimationSpec<Float>) {
        if (specs.isEmpty()) return
        towards = null
        val clamped = position.coerceIn(0f, specs.lastIndex.toFloat())
        val previous = lastPosition
        lastPosition = clamped
        if (previous.isNaN()) {
            mode = if (isWhole(clamped) && state.isShowing(specs[clamped.toInt()])) Mode.Pairs else Mode.Seek(anchor = clamped)
            return
        }
        val current = mode
        when {
            abs(clamped - previous) >= jumpThreshold -> startTimed(clamped.roundToInt(), specs, animationSpec)
            current is Mode.Timed && timedMorph?.isActive == true -> if (clamped != previous) {
                timedMorph?.cancel()
                mode = Mode.Seek(anchor = previous)
                seek(clamped, specs, animationSpec)
            }
            current is Mode.Timed -> {
                // Done, or cancelled by another caller: carry on from the screen.
                mode = if (clamped.roundToInt() == current.target && state.isShowing(specs[current.target])) Mode.Pairs else Mode.Seek(anchor = previous)
                follow(clamped, specs, animationSpec, previous)
            }
            current is Mode.Pairs && shown != null && state.morph != shown -> {
                mode = Mode.Seek(anchor = previous)
                seek(clamped, specs, animationSpec)
            }
            current is Mode.Pairs -> showPair(clamped, specs)
            else -> seek(clamped, specs, animationSpec)
        }
    }

    /** Runs a timed morph to the spec nearest [position], as a jump to it would. */
    fun jumpTo(position: Float, specs: List<NetworkGraphSpec>, animationSpec: AnimationSpec<Float>) {
        if (specs.isEmpty()) return
        val clamped = position.coerceIn(0f, specs.lastIndex.toFloat())
        lastPosition = clamped
        towards = null
        startTimed(clamped.roundToInt(), specs, animationSpec)
    }

    /**
     * Shows the morph from the screen toward the spec at [target] at [fraction], the screen as it was
     * when [target] became the one to morph to. At 1 it lands there and goes back to following pairs.
     */
    suspend fun morphToward(target: Int, fraction: Float, specs: List<NetworkGraphSpec>) {
        if (specs.isEmpty()) return
        val clamped = target.coerceIn(0, specs.lastIndex)
        if (towards != clamped) {
            timedMorph?.cancel()
            // Forgets any seek in progress, so the morph below starts from the screen as it is.
            state.snapTo(state.morph)
            towards = clamped
        }
        state.seekTo(specs[clamped], fraction)
        if (fraction < 1f) return
        towards = null
        lastPosition = clamped.toFloat()
        mode = Mode.Pairs
        showPair(lastPosition, specs)
    }

    /** [follow] again for [position] in the mode just switched to, as if the previous position had been [previous]. */
    private suspend fun follow(position: Float, specs: List<NetworkGraphSpec>, animationSpec: AnimationSpec<Float>, previous: Float) {
        lastPosition = previous
        follow(position, specs, animationSpec)
    }

    private suspend fun showPair(position: Float, specs: List<NetworkGraphSpec>) {
        val lower = floor(position).toInt().coerceIn(0, specs.lastIndex)
        val upper = (lower + 1).coerceAtMost(specs.lastIndex)
        val pair = NetworkGraphMorph(specs[lower], specs[upper], (position - lower).coerceIn(0f, 1f))
        state.snapTo(pair)
        shown = state.morph
    }

    /**
     * The morph from the screen as it was at the anchor toward the whole position the position moves
     * toward, at how far it has got; once it gets there, back to following pairs. Settling back on the
     * anchor itself, a whole position, finishes the morph to its spec over a timed morph.
     */
    private suspend fun seek(position: Float, specs: List<NetworkGraphSpec>, animationSpec: AnimationSpec<Float>) {
        val anchor = (mode as Mode.Seek).anchor
        val travel = position - anchor
        if (travel == 0f) {
            if (isWhole(position) && !state.isShowing(specs[position.toInt()])) startTimed(position.toInt(), specs, animationSpec)
            return
        }
        val target = (if (travel > 0f) floor(anchor) + 1f else ceil(anchor) - 1f).coerceIn(0f, specs.lastIndex.toFloat())
        val fraction = if (target == anchor) 1f else ((position - anchor) / (target - anchor)).coerceIn(0f, 1f)
        if (fraction >= 1f) {
            mode = Mode.Pairs
            showPair(position, specs)
        } else {
            state.seekTo(specs[target.toInt()], fraction)
        }
    }

    private fun startTimed(target: Int, specs: List<NetworkGraphSpec>, animationSpec: AnimationSpec<Float>) {
        mode = Mode.Timed(target)
        shown = null
        timedMorph?.cancel()
        timedMorph = scope.launch { state.animateTo(specs[target], animationSpec) }
    }

    private fun isWhole(position: Float): Boolean = position == floor(position)
}
