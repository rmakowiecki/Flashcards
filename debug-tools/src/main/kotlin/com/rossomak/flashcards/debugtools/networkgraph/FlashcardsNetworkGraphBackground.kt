package com.rossomak.flashcards.feature.debug.networkgraph

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.networkgraph.ExperimentalNetworkGraphApi
import com.rossomak.networkgraph.NetworkGraphFrameStats
import com.rossomak.networkgraph.NetworkGraphLayerStats
import com.rossomak.networkgraph.NetworkGraphMorph
import com.rossomak.networkgraph.NetworkGraphPalette
import com.rossomak.networkgraph.networkGraphBackground

/**
 * The lib's network-graph background in Flashcards' colors: white on [FlashcardsComponentStyle.OnGradient]
 * (the gradient never flips), and each layer's own color, theme `onSurfaceVariant` when unset, on
 * [FlashcardsComponentStyle.OnSurface].
 */
@OptIn(ExperimentalNetworkGraphApi::class)
@Composable
fun Modifier.flashcardsNetworkGraphBackground(
    morph: NetworkGraphMorph,
    style: FlashcardsComponentStyle,
    stats: NetworkGraphFrameStats? = null,
    layerStats: NetworkGraphLayerStats? = null,
    showViolations: Boolean = false,
): Modifier = networkGraphBackground(morph, networkGraphPaletteFor(style), stats, layerStats, showViolations)

@Composable
private fun networkGraphPaletteFor(style: FlashcardsComponentStyle): NetworkGraphPalette {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(style, onSurfaceVariant) {
        when (style) {
            FlashcardsComponentStyle.OnGradient -> NetworkGraphPalette.uniform(Color.White)
            FlashcardsComponentStyle.OnSurface -> NetworkGraphPalette.fromSpec(fallback = onSurfaceVariant)
        }
    }
}
