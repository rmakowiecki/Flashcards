package com.rossomak.networkgraph

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.math.abs
import kotlin.math.min
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
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

    @Test
    fun `a capped follow scrubs exactly while the position moves slower than the cap`() = runMorphTest {
        val state = NetworkGraphMorphState(first)
        val capped = CappedFollow(state, this)
        capped.follower.update()

        repeat(SLOW_FRAMES) { frame ->
            capped.position = (frame + 1) * SLOW_STEP
            capped.follower.update()

            state.morph shouldBe NetworkGraphMorph(first, second, capped.position)
        }
    }

    @Test
    fun `a capped follow takes at least the minimum to morph one page however fast the position gets there`() = runMorphTest {
        val state = NetworkGraphMorphState(first)
        val capped = CappedFollow(state, this)
        capped.follower.update()

        capped.position = HALF_WAY
        launch { capped.follower.update() }
        advanceTimeBy(FRAME_MILLIS + 1)
        capped.position = 1f
        advanceTimeBy(MIN_MORPH_MILLIS - 2 * FRAME_MILLIS)
        state.isShowing(second) shouldBe false
        advanceUntilIdle()

        state.isShowing(second) shouldBe true
    }

    @Test
    fun `a capped follow turns around without a snap when the position turns back`() = runMorphTest {
        val state = NetworkGraphMorphState(first)
        val capped = CappedFollow(state, this)
        capped.follower.update()
        val maxFrameTravel = FRAME_MILLIS.toFloat() / MIN_MORPH_MILLIS
        capped.position = HALF_WAY
        var catchUp = launch { capped.follower.update() }
        advanceTimeBy(FRAME_MILLIS + 1)
        capped.position = 1f

        var previousFraction = state.morph.fraction
        repeat(TURN_FRAMES) { frame ->
            when (frame) {
                TURN_FRAMES / 2 -> capped.position = HALF_WAY
                TURN_FRAMES / 2 + 1 -> capped.position = 0f
            }
            // As Follow does, update again on a move made after a catch-up finished.
            if (catchUp.isCompleted) catchUp = launch { capped.follower.update() }
            advanceTimeBy(FRAME_MILLIS)
            val morph = state.morph
            morph.to shouldBe second
            abs(morph.fraction - previousFraction) shouldBeLessThan maxFrameTravel + TOLERANCE
            previousFraction = morph.fraction
        }
        advanceUntilIdle()

        state.isShowing(first) shouldBe true
    }

    @Test
    fun `a jump skips the cap and morphs straight to its target`() = runMorphTest {
        val state = NetworkGraphMorphState(first)
        val capped = CappedFollow(state, this)
        capped.follower.update()

        capped.position = specs.lastIndex.toFloat()
        launch { capped.follower.update() }
        advanceTimeBy(FRAME_MILLIS * 2)

        state.target shouldBe specs.last()
        state.isAnimating shouldBe true
        advanceUntilIdle()
        state.isShowing(specs.last()) shouldBe true
    }

    @Test
    fun `a capped follow tracks the position exactly when animations are removed`() = runTest(StepFrameClock() + NoMotion) {
        val state = NetworkGraphMorphState(first)
        val capped = CappedFollow(state, this)
        capped.follower.update()

        capped.position = FAST_STEP
        capped.follower.update()

        state.morph shouldBe NetworkGraphMorph(first, second, FAST_STEP)
    }

    @Test
    fun `a capped follow catches up with a position held mid page, then follows it exactly`() = runMorphTest {
        val state = NetworkGraphMorphState(first)
        val capped = CappedFollow(state, this)
        capped.follower.update()

        capped.position = FAST_STEP
        launch { capped.follower.update() }
        advanceTimeBy(FRAME_MILLIS * 2)
        state.morph.fraction shouldBeLessThan FAST_STEP
        advanceUntilIdle()
        state.morph shouldBe NetworkGraphMorph(first, second, FAST_STEP)
        capped.position = FAST_STEP + SLOW_STEP
        capped.follower.update()

        state.morph shouldBe NetworkGraphMorph(first, second, FAST_STEP + SLOW_STEP)
    }

    @Test
    fun `quick taps toward a page several ahead skip the specs in between and land within the minimum of the last tap`() = runMorphTest {
        val state = NetworkGraphMorphState(first)
        val capped = CappedFollow(state, this)
        capped.follower.update()
        var landedFrame: Int? = null

        runPager(capped) { frame ->
            if (frame % TAP_FRAMES == 0 && frame <= LAST_TAP_FRAME) capped.target = frame / TAP_FRAMES + 1
            state.isShowing(second) shouldBe false
            state.isShowing(third) shouldBe false
            if (landedFrame == null && state.isShowing(specs.last())) landedFrame = frame
        }

        landedFrame.shouldNotBeNull() shouldBeLessThanOrEqual LAST_TAP_FRAME + MIN_MORPH_FRAMES + 1
    }

    @Test
    fun `a target beyond the shown pair morphs from the screen without a snap`() = runMorphTest {
        val state = NetworkGraphMorphState(first)
        val capped = CappedFollow(state, this)
        capped.follower.update()
        capped.target = 1
        capped.position = HALF_WAY
        // Holds the target until the position gets there, which it never does here.
        backgroundScope.launch { capped.follower.update() }
        advanceTimeBy(FRAME_MILLIS + 1)
        val screen = state.morph

        capped.target = specs.lastIndex
        advanceTimeBy(FRAME_MILLIS)

        state.morph.to shouldBe specs.last()
        state.morph.origin shouldBe screen
        state.morph.fraction shouldBeLessThan FIRST_RETARGET_FRACTION
    }

    @Test
    fun `without a target a fast run over several pages lands sooner than morphing through each page`() = runMorphTest {
        val state = NetworkGraphMorphState(first)
        val capped = CappedFollow(state, this)
        capped.follower.update()
        var landedFrame: Int? = null

        runPager(capped) { frame ->
            if (landedFrame == null && state.isShowing(specs.last())) landedFrame = frame
        }

        landedFrame.shouldNotBeNull() shouldBeLessThan specs.lastIndex * MIN_MORPH_FRAMES
    }

    @Test
    fun `a target within the shown pair keeps the cap`() = runMorphTest {
        val state = NetworkGraphMorphState(first)
        val capped = CappedFollow(state, this)
        capped.follower.update()

        capped.target = 1
        capped.position = HALF_WAY
        launch { capped.follower.update() }
        advanceTimeBy(FRAME_MILLIS + 1)
        capped.position = 1f
        advanceTimeBy(MIN_MORPH_MILLIS - 2 * FRAME_MILLIS)
        state.isShowing(second) shouldBe false
        advanceUntilIdle()

        state.isShowing(second) shouldBe true
    }

    @Test
    fun `a target is ignored when animations are removed`() = runTest(StepFrameClock() + NoMotion) {
        val state = NetworkGraphMorphState(first)
        val capped = CappedFollow(state, this)
        capped.follower.update()

        capped.target = specs.lastIndex
        capped.position = FAST_STEP
        capped.follower.update()

        state.morph shouldBe NetworkGraphMorph(first, second, FAST_STEP)
    }

    /**
     * Runs [capped]'s position from the first spec to the last at [PAGER_STEP] a frame, as a pager's
     * scroll would, for [RUN_FRAMES] frames, calling [onFrame] before each one and updating again on a
     * move made after a catch-up finished, as Follow does.
     */
    private fun TestScope.runPager(capped: CappedFollow, onFrame: (frame: Int) -> Unit) {
        var catchUp: Job? = null
        repeat(RUN_FRAMES) { frame ->
            onFrame(frame)
            capped.position = min(frame * PAGER_STEP, specs.lastIndex.toFloat())
            if (catchUp?.isActive != true) catchUp = launch { capped.follower.update() }
            advanceTimeBy(FRAME_MILLIS)
        }
        advanceUntilIdle()
    }

    /** A [LimitedMorphFollower] over [specs] with a [MIN_MORPH_MILLIS] minimum, following [position] toward [target]. */
    private inner class CappedFollow(state: NetworkGraphMorphState, scope: TestScope) {
        var position = 0f
        var target: Int? = null
        val follower = LimitedMorphFollower(
            follower = MorphFollower(state, jumpThreshold = 1f, scope = scope),
            jumpThreshold = 1f,
            position = { position },
            target = { target },
            specs = { specs },
            animationSpec = { timed },
            minMorphDuration = { MIN_MORPH_MILLIS.milliseconds },
        )
    }

    private object NoMotion : MotionDurationScale {
        override val scaleFactor: Float = 0f
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
        const val MIN_MORPH_MILLIS = 500L
        const val SLOW_FRAMES = 20
        const val SLOW_STEP = 0.01f
        const val FAST_STEP = 0.8f
        const val TURN_FRAMES = 30
        const val TOLERANCE = 0.0001f
        const val MIN_MORPH_FRAMES = 32
        const val TAP_FRAMES = 4
        const val LAST_TAP_FRAME = 8
        const val PAGER_STEP = 0.125f
        const val RUN_FRAMES = 80
        const val FIRST_RETARGET_FRACTION = 0.25f
    }
}
