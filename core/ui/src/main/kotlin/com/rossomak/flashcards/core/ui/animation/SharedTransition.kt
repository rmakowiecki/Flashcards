package com.rossomak.flashcards.core.ui.animation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.SharedContentState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.first

/**
 * How long a shared element takes to fly from its old destination to its new one. Exposed so a
 * screen can sequence its own entrance animation behind the element that is still in flight.
 */
const val SHARED_ELEMENT_DURATION_MS: Int = 500

/** Stable identities for elements that survive a navigation change instead of being redrawn. */
object SharedElementKey {
    /** The brand mark, handed from the splash screen to the onboarding cover or the login screen. */
    const val APP_LOGO: String = "app-logo"
}

/**
 * The single [SharedTransitionScope] around the app's `NavHost`, and the per-destination
 * [AnimatedVisibilityScope] that a `composable` block provides.
 *
 * Both travel as CompositionLocals rather than parameters because a shared element sits several
 * layers deep inside a feature module, and threading two scopes through every composable between
 * the nav graph and the element would put animation plumbing in signatures that have nothing else
 * to do with it. Both default to `null`, so [sharedElementByKey] degrades to a no-op in previews
 * and tests, where neither scope exists.
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** @see LocalSharedTransitionScope */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

private val SharedElementBoundsTransform = BoundsTransform { _, _ ->
    tween(durationMillis = SHARED_ELEMENT_DURATION_MS, easing = FastOutSlowInEasing)
}

/**
 * Marks this element as the [key] shared element: when the same [key] is present in both the
 * outgoing and the incoming destination, Compose animates the one into the other's bounds instead
 * of cross-fading two separate copies.
 *
 * Apply it before any size modifier so the element's size is part of what animates. Outside a
 * [SharedTransitionScope] (previews, screenshot tests) it returns the receiver unchanged.
 */
@Composable
fun Modifier.sharedElementByKey(key: Any): Modifier = sharedElementByState(rememberSharedElementState(key))

/** [sharedElementByKey] for a [state] the caller also reads, as [rememberIsHandedOver] does. */
@Composable
fun Modifier.sharedElementByState(state: SharedContentState?): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this
    if (state == null) return this
    return with(sharedTransitionScope) {
        this@sharedElementByState.sharedElement(state, animatedVisibilityScope, SharedElementBoundsTransform)
    }
}

/** The state of the [key] shared element, for [sharedElementByState]; `null` outside a [SharedTransitionScope]. */
@Composable
fun rememberSharedElementState(key: Any): SharedContentState? {
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return null
    return with(sharedTransitionScope) { rememberSharedContentState(key) }
}

/**
 * Whether [state]'s element flew in from another destination: true from the first frame its partner is
 * found, and from then on, after the flight has landed too. Lets a screen reveal the element itself only
 * when nothing hands it over. Read it while drawing; it is always false for a `null` [state].
 */
@Composable
fun rememberIsHandedOver(state: SharedContentState?): () -> Boolean {
    var hasBeenHandedOver by remember(state) { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state == null) return@LaunchedEffect
        snapshotFlow { state.isMatchFound }.first { it }
        hasBeenHandedOver = true
    }
    return { hasBeenHandedOver || state?.isMatchFound == true }
}
