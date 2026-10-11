package com.rossomak.flashcards.feature.study.summary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsXpBreakdownRow
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsXpBreakdownTone
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.format.currentLocale
import com.rossomak.flashcards.core.ui.theme.spacing
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest

/**
 * The first [visibleCount] tiles, one per [XpBreakdownLine], newest last. The list scrolls itself to keep
 * the newest tile in view, and switches to compact tiles above [COMPACT_TILE_THRESHOLD] lines.
 */
@Composable
internal fun SummaryXpTiles(
    lines: List<XpBreakdownLine>,
    visibleCount: Int,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val scrollState = rememberScrollState()
    val size = if (lines.size > COMPACT_TILE_THRESHOLD) FlashcardsComponentSize.Small else FlashcardsComponentSize.Normal

    // A new tile grows the scroll range once it is laid out, so follow the range rather than the count.
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.maxValue }.collectLatest { maxValue -> scrollState.animateScrollTo(maxValue) }
    }

    Column(
        modifier = modifier.verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
    ) {
        lines.take(visibleCount).forEach { line -> SummaryXpTile(line = line, size = size, locale = locale) }
    }
}

/** Slides up and fades in as it first appears. */
@Composable
private fun SummaryXpTile(line: XpBreakdownLine, size: FlashcardsComponentSize, locale: Locale) {
    val visibility = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visibleState = visibility, enter = fadeIn() + slideInVertically { height -> height / 2 }) {
        FlashcardsXpBreakdownRow(
            label = xpSourceLabel(line.source),
            value = xpLineValue(line, locale),
            icon = xpSourceIcon(line.source),
            modifier = Modifier.fillMaxWidth(),
            tone = if (line.isLoss) FlashcardsXpBreakdownTone.Loss else FlashcardsXpBreakdownTone.Gain,
            size = size,
        )
    }
}
