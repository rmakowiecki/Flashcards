package com.rossomak.flashcards.debugtools.networkgraph

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.networkgraph.NetworkGraphFill
import com.rossomak.networkgraph.tuner.NetworkGraphPlaygroundStyle

/**
 * The playground surface for [style] in Flashcards' colors. Graphs inherit white on
 * [FlashcardsComponentStyle.OnGradient] (the gradient never flips) over the brand gradients, and theme
 * `onSurfaceVariant` on [FlashcardsComponentStyle.OnSurface] over the plain themed surface; a layer
 * with a fill of its own keeps it on both.
 */
@Composable
fun flashcardsPlaygroundStyle(style: FlashcardsComponentStyle): NetworkGraphPlaygroundStyle {
    val colorScheme = MaterialTheme.colorScheme
    val brandColors = MaterialTheme.brandColors
    return remember(style, colorScheme, brandColors) {
        when (style) {
            FlashcardsComponentStyle.OnSurface -> NetworkGraphPlaygroundStyle(
                name = "On surface",
                fill = NetworkGraphFill.Solid(colorScheme.onSurfaceVariant),
                barBackground = SolidColor(colorScheme.surfaceContainerHigh),
                heroBackground = SolidColor(colorScheme.surfaceContainerHigh),
                contentColor = colorScheme.onSurface,
            )
            FlashcardsComponentStyle.OnGradient -> NetworkGraphPlaygroundStyle(
                name = "On gradient",
                fill = NetworkGraphFill.Solid(Color.White),
                barBackground = brandColors.topBarGradient,
                // The hero stands in for a full-screen surface, so it gets the full-screen gradient.
                heroBackground = brandColors.screenGradient,
                contentColor = brandColors.onGradientContent,
            )
        }
    }
}
