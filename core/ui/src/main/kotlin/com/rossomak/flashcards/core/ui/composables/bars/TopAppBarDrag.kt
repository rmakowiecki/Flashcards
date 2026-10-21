package com.rossomak.flashcards.core.ui.composables.bars

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.math.abs

/** The fraction is a float quotient, never exactly 0, so below this the bar counts as expanded. */
private const val SETTLED_EXPANDED_FRACTION = 0.01f

private const val SNAP_TO_COLLAPSED_FRACTION = 0.5f

private const val FLING_ROUNDING_TOLERANCE_PX = 0.5f

private const val MIN_FLING_VELOCITY = 1f

/**
 * Moves [scrollBehavior]'s state for a drag that starts on the bar, as content scrolling does, and
 * settles on the nearer end on release. The settle runs inside the draggable's mutex, so a new drag
 * cancels it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Modifier.topAppBarDrag(scrollBehavior: TopAppBarScrollBehavior): Modifier {
    val appBarState = scrollBehavior.state
    val draggableState = rememberDraggableState { delta -> appBarState.heightOffset += delta }
    return draggable(
        state = draggableState,
        orientation = Orientation.Vertical,
        onDragStopped = { velocity -> draggableState.drag { settleTopAppBar(scrollBehavior, velocity) } },
    )
}

/** M3's private `settleAppBar` on public API: continue the fling, then snap to the nearer end. */
@OptIn(ExperimentalMaterial3Api::class)
private suspend fun settleTopAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    velocity: Float,
) {
    val appBarState = scrollBehavior.state
    if (appBarState.collapsedFraction < SETTLED_EXPANDED_FRACTION || appBarState.collapsedFraction == 1f) return

    scrollBehavior.flingAnimationSpec?.takeIf { abs(velocity) > MIN_FLING_VELOCITY }?.let { flingSpec ->
        var lastValue = 0f
        AnimationState(initialValue = 0f, initialVelocity = velocity).animateDecay(flingSpec) {
            val delta = value - lastValue
            val offsetBeforeStep = appBarState.heightOffset
            appBarState.heightOffset = offsetBeforeStep + delta
            val consumed = abs(offsetBeforeStep - appBarState.heightOffset)
            lastValue = value
            // Unconsumed delta means the bar hit an end.
            if (abs(delta - consumed) > FLING_ROUNDING_TOLERANCE_PX) cancelAnimation()
        }
    }

    scrollBehavior.snapAnimationSpec?.let { snapSpec ->
        if (appBarState.heightOffset < 0f && appBarState.heightOffset > appBarState.heightOffsetLimit) {
            val target = when {
                appBarState.collapsedFraction < SNAP_TO_COLLAPSED_FRACTION -> 0f
                else -> appBarState.heightOffsetLimit
            }
            AnimationState(initialValue = appBarState.heightOffset).animateTo(target, animationSpec = snapSpec) {
                appBarState.heightOffset = value
            }
        }
    }
}
