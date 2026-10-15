package com.rossomak.flashcards.feature.study.summary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsXpBreakdownRow
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsXpBreakdownTone
import com.rossomak.flashcards.core.ui.theme.spacing
import kotlinx.coroutines.flow.collectLatest

/** Follows the newest tile. Bottom padding keeps the scroll's clip off a tile while it slides in. */
@Composable
internal fun SummaryXpTiles(
    lines: List<XpBreakdownLine>,
    visibleCount: Int,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val slideDistance = MaterialTheme.spacing.medium

    // A new tile grows the scroll range once it is laid out, so follow the range rather than the count.
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.maxValue }.collectLatest { maxValue -> scrollState.animateScrollTo(maxValue) }
    }

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(bottom = slideDistance),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
    ) {
        lines.take(visibleCount).forEach { line -> SummaryXpTile(line = line, slideDistance = slideDistance) }
    }
}

/** Explicit no-op exit: the default shrink would clip the tile during its enter too. */
@Composable
private fun SummaryXpTile(line: XpBreakdownLine, slideDistance: Dp) {
    val slideDistancePx = with(LocalDensity.current) { slideDistance.roundToPx() }
    // A static preview never runs the enter, so it starts visible there.
    val isInspecting = LocalInspectionMode.current
    val visibility = remember { MutableTransitionState(isInspecting).apply { targetState = true } }
    AnimatedVisibility(visibleState = visibility, enter = fadeIn() + slideInVertically { slideDistancePx }, exit = ExitTransition.None) {
        FlashcardsXpBreakdownRow(
            label = xpSourceLabel(line.source),
            value = xpLineValue(line),
            icon = xpSourceIcon(line.source),
            modifier = Modifier.fillMaxWidth(),
            tone = if (line.isLoss) FlashcardsXpBreakdownTone.Loss else FlashcardsXpBreakdownTone.Gain,
        )
    }
}
