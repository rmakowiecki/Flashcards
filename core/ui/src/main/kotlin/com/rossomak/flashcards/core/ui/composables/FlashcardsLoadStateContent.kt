package com.rossomak.flashcards.core.ui.composables

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.IntOffset
import com.rossomak.flashcards.core.ui.composables.LoadStateSlot.Blank
import com.rossomak.flashcards.core.ui.composables.LoadStateSlot.Content
import com.rossomak.flashcards.core.ui.composables.LoadStateSlot.Indicator
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.theme.FlashcardsMotion
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.spacing
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.delay

/**
 * Shows a screen's load state: a delayed loading indicator while [targetState] is loading, then the
 * loaded content (or its empty or error state) revealed as one block, fading in while sliding up by
 * `spacing.medium`. Every swap is sequential: the outgoing block fades out before the incoming one
 * starts, and nothing is clipped to its final bounds while it slides.
 *
 * Loading rules:
 * - The indicator appears only once a load has run for [FlashcardsMotion.LOADING_INDICATOR_DELAY_MS],
 *   and once shown stays at least [FlashcardsMotion.LOADING_INDICATOR_MIN_VISIBLE_MS], so a fast load
 *   never shows it and a slow one never flashes it.
 * - A load that starts while content is showing keeps that content on screen until the indicator's
 *   delay runs out. A result arriving within it with the same [contentKey] updates the content in
 *   place, with no animation; one with a different key swaps to it as usual.
 * - [content] maps each state to [FlashcardsLoadStateScope.loading] or [FlashcardsLoadStateScope.content],
 *   usually with one exhaustive `when`. It is a plain function, not a composable: the helper calls it
 *   to classify every new state before drawing anything, then again for the state a block shows, so
 *   that block always draws with the caller's latest values. Keep it free of side effects, and decide
 *   loading from its parameter only, never from a captured value: the content kept on screen is
 *   re-classified while the screen loads, and a capture would read as loading and draw nothing. A
 *   screen can keep the previous content on screen without carrying it inside its loading state.
 *
 * A state already loaded at the first composition (back navigation, rotation, process restore) shows
 * without animation. Changes that keep the same [contentKey] never re-reveal the block; rows moving
 * inside it animate on their own (`Modifier.animateItem()`).
 *
 * Each block fills the available space and is placed by [contentAlignment]; the indicator is always
 * centered. [style] picks the indicator's color for the background behind it.
 */
@Composable
fun <S> FlashcardsLoadStateContent(
    targetState: S,
    modifier: Modifier = Modifier,
    style: FlashcardsComponentStyle = FlashcardsComponentStyle.OnSurface,
    contentAlignment: Alignment = Alignment.TopStart,
    contentKey: (S) -> Any? = { it },
    content: FlashcardsLoadStateScope.(S) -> FlashcardsLoadStateResult,
) {
    val isLoading = FlashcardsLoadStateScope.content(targetState).block == null
    val slot = rememberLoadStateSlot(targetState = targetState, isLoading = isLoading)
    val slideDistancePx = with(LocalDensity.current) { MaterialTheme.spacing.medium.roundToPx() }
    AnimatedContent(
        targetState = slot,
        modifier = modifier,
        transitionSpec = { loadStateTransform(slideDistancePx) },
        contentKey = { shownSlot -> shownSlot.key(contentKey) },
        label = "FlashcardsLoadStateContent",
    ) { shownSlot ->
        when (shownSlot) {
            Blank -> Box(modifier = Modifier.fillMaxSize())
            Indicator -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = style.indicatorColor())
            }

            is Content -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = contentAlignment) {
                // Invoked in place, never passed on as a parameter: the lambda is a new instance on
                // every call, so a composable taking it would never skip.
                FlashcardsLoadStateScope.content(shownSlot.state).block?.invoke(this)
            }
        }
    }
}

/** Builds what [FlashcardsLoadStateContent] shows for one state. */
object FlashcardsLoadStateScope {
    private val Loading = FlashcardsLoadStateResult(block = null)

    /** The state is loading: the helper shows its delayed indicator, or keeps the previous content. */
    fun loading(): FlashcardsLoadStateResult = Loading

    /** The state is loaded (content, empty or error): the helper reveals [block]. */
    fun content(block: @Composable BoxScope.() -> Unit): FlashcardsLoadStateResult = FlashcardsLoadStateResult(block)
}

/** One state's answer to [FlashcardsLoadStateContent]; built only through [FlashcardsLoadStateScope]. */
class FlashcardsLoadStateResult internal constructor(internal val block: (@Composable BoxScope.() -> Unit)?)

/** What [FlashcardsLoadStateContent] has on screen: nothing yet, the loading indicator, or a loaded state. */
private sealed interface LoadStateSlot<out S> {
    data object Blank : LoadStateSlot<Nothing>

    data object Indicator : LoadStateSlot<Nothing>

    data class Content<S>(val state: S) : LoadStateSlot<S>
}

/** Wraps a caller's content key so it can never collide with [Blank] or [Indicator]. */
private data class ContentSlotKey(val key: Any?)

private fun <S> LoadStateSlot<S>.key(contentKey: (S) -> Any?): Any? =
    when (this) {
        Blank, Indicator -> this
        is Content -> ContentSlotKey(contentKey(state))
    }

/** The last loaded state shown; a plain holder, written after each composition that shows one. */
private class SettledSlot<S>(var slot: Content<S>? = null)

/**
 * Resolves [targetState] to the slot on screen. Loaded states pass straight through, so in-place
 * updates land in the same frame; while loading, the last loaded state stays up until the indicator's
 * delay runs out, and the indicator then holds for its minimum time even if the load finishes first.
 */
@Composable
private fun <S> rememberLoadStateSlot(targetState: S, isLoading: Boolean): LoadStateSlot<S> {
    val settled = remember { SettledSlot<S>() }
    var isIndicatorShown by remember { mutableStateOf(false) }
    var indicatorShownAt by remember { mutableStateOf(TimeSource.Monotonic.markNow()) }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            if (!isIndicatorShown) {
                delay(FlashcardsMotion.LOADING_INDICATOR_DELAY_MS.milliseconds)
                indicatorShownAt = TimeSource.Monotonic.markNow()
                isIndicatorShown = true
            }
        } else if (isIndicatorShown) {
            val remaining = FlashcardsMotion.LOADING_INDICATOR_MIN_VISIBLE_MS.milliseconds - indicatorShownAt.elapsedNow()
            if (remaining.isPositive()) delay(remaining)
            isIndicatorShown = false
        }
    }

    if (!isLoading) {
        SideEffect { settled.slot = Content(targetState) }
    }

    return when {
        isIndicatorShown -> Indicator
        !isLoading -> Content(targetState)
        else -> settled.slot ?: Blank
    }
}

private fun <S> AnimatedContentTransitionScope<LoadStateSlot<S>>.loadStateTransform(slideDistancePx: Int): ContentTransform {
    // The incoming block waits for the outgoing one to fade out; nothing fades out of a blank slot.
    val enterDelayMs = initialState.fadeOutDurationMs()
    val enter = when (targetState) {
        is Content -> revealEnter(slideDistancePx, enterDelayMs)
        Blank, Indicator -> fadeIn(tween(FlashcardsMotion.DURATION_SHORT_MS, enterDelayMs))
    }
    return unclipped(enter = enter, exit = fadeOut(tween(initialState.fadeOutDurationMs())))
}

private fun revealEnter(slideDistancePx: Int, delayMs: Int): EnterTransition {
    val revealSpec = tween<Float>(FlashcardsMotion.REVEAL_DURATION_MS, delayMs, FlashcardsMotion.EmphasizedEasing)
    val slideSpec = tween<IntOffset>(FlashcardsMotion.REVEAL_DURATION_MS, delayMs, FlashcardsMotion.EmphasizedEasing)
    return fadeIn(revealSpec) + slideInVertically(slideSpec) { slideDistancePx }
}

// Fade-only exit and an unclipped size transform: a shrink or a clipping container would cut the
// incoming block off at its final bounds while it slides up into them.
private fun AnimatedContentTransitionScope<*>.unclipped(enter: EnterTransition, exit: ExitTransition): ContentTransform =
    (enter togetherWith exit) using SizeTransform(clip = false)

/**
 * Swaps blocks that need no loading indicator with the same motion as [FlashcardsLoadStateContent]:
 * the outgoing block fades out, then the incoming one fades in while sliding up, unclipped. For a part
 * of a screen that changes on its own after the screen's load has revealed, such as a section that
 * arrives late. Changes that keep the same [contentKey] update in place; the first composition shows
 * without animation.
 */
@Composable
fun <S> FlashcardsRevealContent(
    targetState: S,
    modifier: Modifier = Modifier,
    contentKey: (S) -> Any? = { it },
    content: @Composable (S) -> Unit,
) {
    val slideDistancePx = with(LocalDensity.current) { MaterialTheme.spacing.medium.roundToPx() }
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            unclipped(
                enter = revealEnter(slideDistancePx, FlashcardsMotion.DURATION_MEDIUM_MS),
                exit = fadeOut(tween(FlashcardsMotion.DURATION_MEDIUM_MS)),
            )
        },
        contentKey = contentKey,
        label = "FlashcardsRevealContent",
    ) { shownState ->
        content(shownState)
    }
}

/** Loaded content fades out more slowly than the indicator, which only ever fades quickly. */
private fun LoadStateSlot<*>.fadeOutDurationMs(): Int =
    when (this) {
        Blank -> 0
        Indicator -> FlashcardsMotion.DURATION_SHORT_MS
        is Content -> FlashcardsMotion.DURATION_MEDIUM_MS
    }

@Composable
private fun FlashcardsComponentStyle.indicatorColor() =
    when (this) {
        FlashcardsComponentStyle.OnSurface -> ProgressIndicatorDefaults.circularColor
        FlashcardsComponentStyle.OnGradient -> MaterialTheme.brandColors.onGradientContent
    }

/** Interactive mode cycles loading, loaded and a reload, so the timing can be watched. */
@PreviewLightDark
@Composable
private fun FlashcardsLoadStateContentPreview() {
    var loadedText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        var reloads = 0
        while (true) {
            delay(PREVIEW_LOAD_DURATION)
            reloads++
            loadedText = "Loaded $reloads"
            delay(PREVIEW_LOAD_DURATION)
            loadedText = null
        }
    }
    FlashcardsTheme {
        Surface {
            FlashcardsLoadStateContent(
                targetState = loadedText,
                contentAlignment = Alignment.Center,
            ) { text ->
                when (text) {
                    null -> loading()
                    else -> content { Text(text = text, style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
    }
}

private val PREVIEW_LOAD_DURATION = 1500.milliseconds
