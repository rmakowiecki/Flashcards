package com.rossomak.flashcards.core.ui.composables.bars

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.snap
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class TopAppBarDragTest {

    private val heightOffsetLimit = -100f
    private val flingVelocity = 5_000f
    private val frameStepNanos = 16_000_000L

    private val frameClock = object : MonotonicFrameClock {
        private var nanos = 0L

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            nanos += frameStepNanos
            return onFrame(nanos)
        }
    }

    private fun scrollBehaviorAt(
        heightOffset: Float,
        flingAnimationSpec: DecayAnimationSpec<Float>? = null,
    ) = FakeScrollBehavior(
        state = TopAppBarState(
            initialHeightOffsetLimit = heightOffsetLimit,
            initialHeightOffset = heightOffset,
            initialContentOffset = 0f,
        ),
        snapAnimationSpec = snap(),
        flingAnimationSpec = flingAnimationSpec,
    )

    private suspend fun TopAppBarScrollBehavior.settle(velocity: Float = 0f) =
        withContext(frameClock) { settleTopAppBar(this@settle, velocity) }

    @Test
    fun `release before halfway snaps back to expanded`() = runTest {
        val scrollBehavior = scrollBehaviorAt(heightOffset = heightOffsetLimit * 0.3f)

        scrollBehavior.settle()

        scrollBehavior.state.heightOffset shouldBe 0f
    }

    @Test
    fun `release past halfway snaps on to collapsed`() = runTest {
        val scrollBehavior = scrollBehaviorAt(heightOffset = heightOffsetLimit * 0.7f)

        scrollBehavior.settle()

        scrollBehavior.state.heightOffset shouldBe heightOffsetLimit
    }

    @Test
    fun `a bar already at an end stays there`() = runTest {
        val expanded = scrollBehaviorAt(heightOffset = 0f)
        val collapsed = scrollBehaviorAt(heightOffset = heightOffsetLimit)

        expanded.settle()
        collapsed.settle()

        expanded.state.heightOffset shouldBe 0f
        collapsed.state.heightOffset shouldBe heightOffsetLimit
    }

    @Test
    fun `a fast upward fling carries the bar to collapsed`() = runTest {
        val scrollBehavior = scrollBehaviorAt(heightOffset = heightOffsetLimit * 0.3f, flingAnimationSpec = exponentialDecay())

        scrollBehavior.settle(velocity = -flingVelocity)

        scrollBehavior.state.heightOffset shouldBe heightOffsetLimit
    }

    @Test
    fun `a fast downward fling carries the bar to expanded`() = runTest {
        val scrollBehavior = scrollBehaviorAt(heightOffset = heightOffsetLimit * 0.7f, flingAnimationSpec = exponentialDecay())

        scrollBehavior.settle(velocity = flingVelocity)

        scrollBehavior.state.heightOffset shouldBe 0f
    }

    private class FakeScrollBehavior(
        override val state: TopAppBarState,
        override val snapAnimationSpec: AnimationSpec<Float>?,
        override val flingAnimationSpec: DecayAnimationSpec<Float>?,
    ) : TopAppBarScrollBehavior {
        override val isPinned = false
        override val nestedScrollConnection = object : NestedScrollConnection {}
    }
}
