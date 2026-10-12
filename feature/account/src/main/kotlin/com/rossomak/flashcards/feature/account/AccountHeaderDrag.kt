package com.rossomak.flashcards.feature.account

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

/** Below this a collapse is treated as fully expanded: the fraction is a float quotient, so never exactly 0. */
private const val SETTLED_EXPANDED_FRACTION = 0.01f

/** Past this fraction a released drag snaps to collapsed, short of it back to expanded. */
private const val SNAP_TO_COLLAPSED_FRACTION = 0.5f

/** Leftover distance below which a fling is taken to have hit the end of the bar's travel. */
private const val FLING_ROUNDING_TOLERANCE_PX = 0.5f

/** Velocity below which a release is a plain drag stop with no fling to continue. */
private const val MIN_FLING_VELOCITY = 1f

/**
 * Lets the header itself be dragged, which M3's bars do not offer: list scrolling already moves
 * [scrollBehavior]'s state through its nested-scroll connection, and this adds the same movement
 * for a drag that starts on the bar. On release the bar settles on whichever end is nearer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Modifier.accountHeaderDrag(scrollBehavior: TopAppBarScrollBehavior): Modifier {
    val appBarState = scrollBehavior.state
    return draggable(
        state = rememberDraggableState { delta -> appBarState.heightOffset += delta },
        orientation = Orientation.Vertical,
        onDragStopped = { velocity -> settleAccountHeader(scrollBehavior, velocity) },
    )
}

/**
 * M3's private `settleAppBar`, rebuilt on public API: continues a fling with the behavior's decay
 * spec, then animates the remainder to the nearer end with its snap spec. Skips both when the bar
 * is already fully expanded or collapsed.
 */
@OptIn(ExperimentalMaterial3Api::class)
private suspend fun settleAccountHeader(
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
            // Anything left unconsumed means the bar hit an end, so the fling is over.
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
