package com.rossomak.networkgraph.playground

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.rossomak.networkgraph.NetworkGraphPalette
import com.rossomak.networkgraph.tuner.LabeledNetworkGraphPresets
import com.rossomak.networkgraph.tuner.NetworkGraphPlayground
import com.rossomak.networkgraph.tuner.NetworkGraphPlaygroundStyle

/** Opens the playground in a phone-shaped window, on the lib's own presets in stock Material colors. */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Network graph playground",
        state = rememberWindowState(width = WINDOW_WIDTH, height = WINDOW_HEIGHT),
    ) {
        MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
            Surface {
                val styles = rememberPlaygroundStyles(MaterialTheme.colorScheme)
                NetworkGraphPlayground(
                    presets = LabeledNetworkGraphPresets.all,
                    morphFamily = LabeledNetworkGraphPresets.morphFamily,
                    decks = listOf(LabeledNetworkGraphPresets.morphFamily),
                    styles = styles.all,
                    deckStyle = styles.onGradient,
                    onSpecExport = ::println,
                )
            }
        }
    }
}

/** A plain graph on the themed surface, a white one on a gradient, and a gradient-filled one on the surface. */
private class PlaygroundStyles(
    val onSurface: NetworkGraphPlaygroundStyle,
    val onGradient: NetworkGraphPlaygroundStyle,
    val gradientGraph: NetworkGraphPlaygroundStyle,
) {
    val all = listOf(onSurface, onGradient, gradientGraph)
}

@Composable
private fun rememberPlaygroundStyles(colorScheme: ColorScheme): PlaygroundStyles = remember(colorScheme) {
    val surface = SolidColor(colorScheme.surfaceContainerHigh)
    val gradient = Brush.linearGradient(listOf(colorScheme.primary, colorScheme.tertiary))
    PlaygroundStyles(
        onSurface = NetworkGraphPlaygroundStyle(
            name = "On surface",
            palette = NetworkGraphPalette.fromSpec(fallback = colorScheme.onSurfaceVariant),
            barBackground = surface,
            heroBackground = surface,
            contentColor = colorScheme.onSurface,
        ),
        onGradient = NetworkGraphPlaygroundStyle(
            name = "On gradient",
            palette = NetworkGraphPalette.uniform(Color.White),
            barBackground = gradient,
            heroBackground = gradient,
            contentColor = colorScheme.onPrimary,
        ),
        gradientGraph = NetworkGraphPlaygroundStyle(
            name = "Gradient graph",
            palette = NetworkGraphPalette.gradient(gradient),
            barBackground = surface,
            heroBackground = surface,
            contentColor = colorScheme.onSurface,
        ),
    )
}

private val WINDOW_WIDTH = 420.dp
private val WINDOW_HEIGHT = 860.dp
