package com.rossomak.flashcards.core.ui.composables

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared default fade extent for [flashcardsListScrollFade] and [flashcardsGridScrollFade] — a
 * hint, not a peek. Public so a screen that wants the same fade tuned to match the rest of the app
 * pulls this constant in rather than re-guessing "24dp" at the call site.
 */
val FlashcardsScrollFadeHeight = 24.dp

/**
 * How an edge fade makes its faded strip read as "gone": paint over it with a known color that
 * matches the screen ([Solid]), or punch a real hole in the content's own alpha so whatever is
 * genuinely behind it shows through ([Transparent]).
 */
private sealed interface FlashcardsFadeReveal {
    data class Solid(val color: Color) : FlashcardsFadeReveal
    data object Transparent : FlashcardsFadeReveal
}

/**
 * Draws one edge's fade strip of [heightPx] within this already-sized draw area, using [reveal]'s
 * technique. [fromTop] picks which edge: `true` fades the first [heightPx] pixels from `y = 0`,
 * `false` fades the last [heightPx] pixels up to the bottom.
 */
private fun DrawScope.drawFadeEdge(reveal: FlashcardsFadeReveal, fromTop: Boolean, heightPx: Float) {
    val startY = if (fromTop) 0f else size.height - heightPx
    val endY = if (fromTop) heightPx else size.height
    when (reveal) {
        is FlashcardsFadeReveal.Solid -> drawRect(
            brush = Brush.verticalGradient(
                colors = if (fromTop) {
                    listOf(reveal.color, reveal.color.copy(alpha = 0f))
                } else {
                    listOf(reveal.color.copy(alpha = 0f), reveal.color)
                },
                startY = startY,
                endY = endY,
            ),
        )
        // DstIn multiplies the destination's own alpha by this mask, so it's the mirror image of
        // the Solid case above: the mask must be Transparent (alpha 0) right at the edge — that's
        // what erases the content there — and Black (alpha 1, a no-op) by the time it reaches
        // heightPx back into the content, where the fade should already have fully resolved.
        FlashcardsFadeReveal.Transparent -> drawRect(
            brush = Brush.verticalGradient(
                colors = if (fromTop) listOf(Color.Transparent, Color.Black) else listOf(Color.Black, Color.Transparent),
                startY = startY,
                endY = endY,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
}

/**
 * Shared plumbing behind [flashcardsListScrollFade] and [flashcardsGridScrollFade]: grows each
 * edge's fade strip from `0` up to [fadeHeight] as [topScrolledPx]/[bottomScrolledPx] report the
 * boundary item scrolling out from that edge, rather than snapping straight to full height on the
 * first scrolled pixel — see those functions' docs for why. [canFadeTop]/[canFadeBottom] gate
 * whether an edge is even in play (mirrors [LazyListState.canScrollBackward]/`Forward`, or the
 * grid equivalent) so a list too short to scroll never pays for the extra draw pass, and
 * [Transparent][FlashcardsFadeReveal.Transparent] additionally needs an offscreen
 * [graphicsLayer] so [BlendMode.DstIn] affects this content's real alpha rather than blending
 * against whatever the draw call already assumed was behind it.
 */
private fun Modifier.edgeScrollFade(
    fadeHeight: Dp,
    reveal: FlashcardsFadeReveal,
    canFadeTop: Boolean,
    canFadeBottom: Boolean,
    topScrolledPx: DrawScope.(fadeHeightPx: Float) -> Float,
    bottomScrolledPx: DrawScope.(fadeHeightPx: Float) -> Float,
): Modifier {
    if (!canFadeTop && !canFadeBottom) return this
    val base = if (reveal is FlashcardsFadeReveal.Transparent) {
        graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    } else {
        this
    }
    return base.drawWithContent {
        drawContent()
        val fadeHeightPx = fadeHeight.toPx()
        if (canFadeTop) {
            val heightPx = topScrolledPx(fadeHeightPx).coerceIn(0f, fadeHeightPx)
            if (heightPx > 0f) drawFadeEdge(reveal, fromTop = true, heightPx = heightPx)
        }
        if (canFadeBottom) {
            val heightPx = bottomScrolledPx(fadeHeightPx).coerceIn(0f, fadeHeightPx)
            if (heightPx > 0f) drawFadeEdge(reveal, fromTop = false, heightPx = heightPx)
        }
    }
}

/**
 * Fades this list's top and bottom edges once [listState] has scrolled past its first or last
 * item, signalling there's more content above/below — the same top/bottom-of-viewport cue
 * `flashcardsListGroupContainer` already gives by squaring off that corner. The bottom edge is
 * what keeps a cut-off, white list item from butting straight up against the bottom toolbar with
 * no visual separation.
 *
 * Paints [backgroundColor] itself as the fade (see [FlashcardsFadeReveal.Solid]) rather than
 * punching a hole in the list's own alpha: that only reads as "fading into the screen" when
 * whatever the runtime actually composites behind this layer happens to match — true here only as
 * long as nothing else (a scrim, a differently-colored parent, the app bar's collapse animation,
 * the bottom toolbar) ever sits between this list and the screen. Painting the real screen color
 * explicitly removes that assumption — the fade *is* [backgroundColor], not a bet on what's
 * behind it. Use [flashcardsGridScrollFade] instead over a background that isn't one flat color
 * (a gradient, an image) — there, only a genuine transparency reveal can look right.
 */
@Composable
fun Modifier.flashcardsListScrollFade(
    listState: LazyListState,
    fadeHeight: Dp = FlashcardsScrollFadeHeight,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
): Modifier {
    val canFadeTop by remember(listState) { derivedStateOf { listState.canScrollBackward } }
    val canFadeBottom by remember(listState) { derivedStateOf { listState.canScrollForward } }
    return edgeScrollFade(
        fadeHeight = fadeHeight,
        reveal = FlashcardsFadeReveal.Solid(backgroundColor),
        canFadeTop = canFadeTop,
        canFadeBottom = canFadeBottom,
        topScrolledPx = { fadeHeightPx ->
            if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else fadeHeightPx
        },
        bottomScrolledPx = { fadeHeightPx ->
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
            if (lastVisible != null && lastVisible.index == layoutInfo.totalItemsCount - 1) {
                (lastVisible.offset + lastVisible.size - layoutInfo.viewportEndOffset).toFloat()
            } else {
                fadeHeightPx
            }
        },
    )
}

/**
 * [flashcardsListScrollFade] for a plain [ScrollState]-driven `Modifier.verticalScroll` container
 * (not a lazy list) — e.g. [FlashcardCard][com.rossomak.flashcards.feature.study.chrome.FlashcardCard]'s
 * own content column, which scrolls internally when a card's question/answer text overflows its max
 * height. Same solid-color paint-over technique as [flashcardsListScrollFade]: pass the color the
 * scrollable content actually sits on (its own container's background), not the screen background.
 */
@Composable
fun Modifier.flashcardsScrollFade(
    scrollState: ScrollState,
    fadeHeight: Dp = FlashcardsScrollFadeHeight,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
): Modifier {
    val canFadeTop by remember(scrollState) { derivedStateOf { scrollState.canScrollBackward } }
    val canFadeBottom by remember(scrollState) { derivedStateOf { scrollState.canScrollForward } }
    return edgeScrollFade(
        fadeHeight = fadeHeight,
        reveal = FlashcardsFadeReveal.Solid(backgroundColor),
        canFadeTop = canFadeTop,
        canFadeBottom = canFadeBottom,
        topScrolledPx = { scrollState.value.toFloat() },
        bottomScrolledPx = { (scrollState.maxValue - scrollState.value).toFloat() },
    )
}

/**
 * [flashcardsListScrollFade] for a `LazyVerticalGrid`: same growth-with-scroll behaviour, but
 * reveals whatever is genuinely behind the grid ([FlashcardsFadeReveal.Transparent]) instead of
 * painting a flat color over it. The favorites step's grid sits over the onboarding screen's brand
 * gradient, not a solid color, so there is no single [Color] a solid fade could paint that would
 * stay correct across the whole strip — only a true alpha punch-through tracks a gradient (or any
 * other non-flat background) pixel-for-pixel.
 */
@Composable
fun Modifier.flashcardsGridScrollFade(
    gridState: LazyGridState,
    fadeHeight: Dp = FlashcardsScrollFadeHeight,
): Modifier {
    val canFadeTop by remember(gridState) { derivedStateOf { gridState.canScrollBackward } }
    val canFadeBottom by remember(gridState) { derivedStateOf { gridState.canScrollForward } }
    return edgeScrollFade(
        fadeHeight = fadeHeight,
        reveal = FlashcardsFadeReveal.Transparent,
        canFadeTop = canFadeTop,
        canFadeBottom = canFadeBottom,
        topScrolledPx = { fadeHeightPx ->
            if (gridState.firstVisibleItemIndex == 0) gridState.firstVisibleItemScrollOffset.toFloat() else fadeHeightPx
        },
        bottomScrolledPx = { fadeHeightPx ->
            val layoutInfo = gridState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
            if (lastVisible != null && lastVisible.index == layoutInfo.totalItemsCount - 1) {
                (lastVisible.offset.y + lastVisible.size.height - layoutInfo.viewportEndOffset).toFloat()
            } else {
                fadeHeightPx
            }
        },
    )
}
