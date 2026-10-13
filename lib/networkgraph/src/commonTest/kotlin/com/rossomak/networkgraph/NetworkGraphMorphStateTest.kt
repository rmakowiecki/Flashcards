package com.rossomak.networkgraph

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MonotonicFrameClock
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/** [NetworkGraphMorphState]'s seeks and timed morphs, and how [MorphFollower] drives it from a position. */
class NetworkGraphMorphStateTest {

    private val specs = NetworkGraphPresets.MorphFamily.all.take(SPEC_COUNT)
    private val first = specs[0]
    private val second = specs[1]
    private val third = specs[2]
    private val timed = tween<Float>(TIMED_MILLIS, easing = LinearEasing)

    @Test
    fun `a seek scrubs one morph from the screen and back to it exactly`() = runMorphTest {
        val state = NetworkGraphMorphState(first)

        state.seekTo(second, HALF_WAY)
        state.morph shouldBe NetworkGraphMorph(first, second, HALF_WAY)
        state.seekTo(second, 0f)

        state.morph shouldBe NetworkGraphMorph(first, second, 0f)
        state.isShowing(first) shouldBe true
    }

    @Test
    fun `a seek toward a new target starts from the screen part way through the last one`() = runMorphTest {
        val state = NetworkGraphMorphState(first)
        state.seekTo(second, HALF_WAY)

        state.seekTo(third, 0f)

        state.morph.origin shouldBe NetworkGraphMorph(first, second, HALF_WAY)
        state.morph.to shouldBe third
    }

    @Test
    fun `a timed morph arrives at its target and leaves no origin behind`() = runMorphTest {
        val state = NetworkGraphMorphState(first)

        state.animateTo(second, timed)

        state.isShowing(second) shouldBe true
        state.morph.origin.shouldBeNull()
        state.isAnimating shouldBe false
    }

    @Test
    fun `a seek during a timed morph cancels it and starts from where it got to`() = runMorphTest {
        val state = NetworkGraphMorphState(first)
        val animation = launch { state.animateTo(second, timed) }
        advanceTimeBy(TIMED_MILLIS / 2L)
        val reached = state.morph

        state.seekTo(third, 0f)

        animation.isCancelled shouldBe true
        state.morph.origin shouldBe reached
        reached.fraction shouldBeGreaterThan 0f
    }

    @Test
    fun `a new timed morph cancels the running one and starts from the screen`() = runMorphTest {
        val state = NetworkGraphMorphState(first)
        val animation = launch { state.animateTo(second, timed) }
        advanceTimeBy(TIMED_MILLIS / 2L)

        launch { state.animateTo(third, timed) }
        advanceUntilIdle()

        animation.isCancelled shouldBe true
        state.isShowing(third) shouldBe true
    }

    @Test
    fun `following a position shows the pair of specs it lies between`() = runMorphTest {
        val state = NetworkGraphMorphState(first)
        val follower = MorphFollower(state, jumpThreshold = 1f, scope = this)

        follower.follow(0f, specs, timed)
        follower.follow(0.4f, specs, timed)
        state.morph shouldBe NetworkGraphMorph(first, second, 0.4f)
        follower.follow(1.25f, specs, timed)

        state.morph shouldBe NetworkGraphMorph(second, third, 0.25f)
    }

    @Test
    fun `a jump runs a timed morph, a drag takes over from the screen, and settling finishes the morph`() = runMorphTest {
        val state = NetworkGraphMorphState(first)
        val follower = MorphFollower(state, jumpThreshold = 1f, scope = this)
        val last = specs.lastIndex.toFloat()
        follower.follow(0f, specs, timed)

        follower.follow(last, specs, timed)
        advanceTimeBy(TIMED_MILLIS / 2L)
        state.isAnimating shouldBe true
        val reached = state.morph
        follower.follow(last - 0.1f, specs, timed)

        state.isAnimating shouldBe false
        state.morph.origin shouldBe reached
        state.morph.to shouldBe specs[specs.lastIndex - 1]
        follower.follow(last, specs, timed)
        advanceUntilIdle()
        state.isShowing(specs.last()) shouldBe true
        follower.follow(last - 0.25f, specs, timed)
        state.morph shouldBe NetworkGraphMorph(specs[specs.lastIndex - 1], specs.last(), 0.75f)
    }

    @Test
    fun `a position first seen while the state shows something else leaves that alone until it moves`() = runMorphTest {
        val transparent = first.copy(layers = first.layers.map { it.copy(alpha = 0f) })
        val state = NetworkGraphMorphState(transparent)
        val follower = MorphFollower(state, jumpThreshold = 1f, scope = this)
        val entrance = launch { state.animateTo(first, timed) }

        follower.follow(0f, specs, timed)
        advanceTimeBy(TIMED_MILLIS / 2L)

        entrance.isActive shouldBe true
        val reached = state.morph
        follower.follow(0.2f, specs, timed)
        state.morph.origin shouldNotBe null
        state.morph.origin.shouldNotBeNull().fraction shouldBe reached.fraction
        state.morph.fraction shouldBeLessThan 1f
    }

    private fun runMorphTest(body: suspend TestScope.() -> Unit) = runTest(StepFrameClock()) { body() }

    /** A frame every [FRAME_MILLIS] of test time, so a timed morph advances with the test scheduler. */
    private class StepFrameClock : MonotonicFrameClock {
        private var nanos = 0L

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            delay(FRAME_MILLIS)
            nanos += FRAME_MILLIS * NANOS_PER_MILLI
            return onFrame(nanos)
        }
    }

    private companion object {
        const val SPEC_COUNT = 4
        const val HALF_WAY = 0.5f
        const val TIMED_MILLIS = 400
        const val FRAME_MILLIS = 16L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
