package com.rossomak.flashcards.feature.study.summary

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsXpBreakdownRow
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsXpBreakdownTone
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.format.currentLocale
import com.rossomak.flashcards.core.ui.theme.spacing
import java.util.Locale

/**
 * The tiles revealed so far, one per [XpBreakdownLine], newest last. The list scrolls itself to keep
 * the newest tile in view, and switches to compact tiles above [COMPACT_TILE_THRESHOLD] lines. It is
 * measured by whatever it is placed in, never positioned by coordinates.
 */
@Composable
internal fun SummaryXpTiles(
    lines: List<XpBreakdownLine>,
    visibleCount: Int,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val scrollState = rememberScrollState()
    val size = if (lines.size > COMPACT_TILE_THRESHOLD) FlashcardsComponentSize.Small else FlashcardsComponentSize.Normal

    // A new tile grows the scroll range once it is laid out, so follow the range rather than the count.
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.maxValue }.collect { maxValue -> scrollState.animateScrollTo(maxValue) }
    }

    Column(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha() }
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
    ) {
        lines.take(visibleCount).forEach { line ->
            SummaryXpTile(line = line, size = size, locale = locale)
        }
    }
}

/** Slides up 24dp and fades in as it first appears. */
@Composable
private fun SummaryXpTile(line: XpBreakdownLine, size: FlashcardsComponentSize, locale: Locale) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(TILE_ENTER.toMillisInt(), easing = SlideEasing)) }
    val slidePx = with(LocalDensity.current) { TILE_SLIDE_DP.dp.toPx() }

    FlashcardsXpBreakdownRow(
        label = xpSourceLabel(line.source),
        value = xpLineValue(line, locale),
        icon = xpSourceIcon(line.source),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * slidePx
            },
        tone = if (line.isLoss) FlashcardsXpBreakdownTone.Loss else FlashcardsXpBreakdownTone.Gain,
        size = size,
    )
}
