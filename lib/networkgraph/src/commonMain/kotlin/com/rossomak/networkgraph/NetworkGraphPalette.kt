package com.rossomak.networkgraph

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

/**
 * Decides what each layer is painted with. The renderer asks once per layer and frame, and once per
 * end of a morph while one runs, so a palette may animate with [NetworkGraphLayerContext.timeSeconds]
 * or span the surface with [NetworkGraphLayerContext.size].
 *
 * A [SolidColor] paints every edge, halo and dot in that color, and two solid ends of a morph blend
 * color by color. Any other [Brush] paints the layer as a whole: its edges, halos and dots keep their
 * own alpha and take the brush's color at their position, and two ends of a morph that aren't both
 * solid crossfade into each other. Either way only the brush's colors count; the layer's own alpha
 * rules decide how opaque each element is.
 */
public fun interface NetworkGraphPalette {
    public fun brushFor(context: NetworkGraphLayerContext): Brush

    public companion object {
        /** Every layer in [color]. */
        public fun uniform(color: Color): NetworkGraphPalette {
            val brush = SolidColor(color)
            return NetworkGraphPalette { brush }
        }

        /** Each layer in its own [NetworkGraphLayerSpec.surfaceColor], or [fallback] where that is null. */
        public fun fromSpec(fallback: Color): NetworkGraphPalette {
            val fallbackBrush = SolidColor(fallback)
            return NetworkGraphPalette { context -> context.layer.surfaceColor?.let(::SolidColor) ?: fallbackBrush }
        }

        /** Every layer painted with [brush], laid out over the whole surface. */
        public fun gradient(brush: Brush): NetworkGraphPalette = NetworkGraphPalette { brush }
    }
}

/** Which end of a morph a [NetworkGraphLayerContext] describes; a still background is its [From] end. */
public enum class NetworkGraphMorphSide { From, To }

/**
 * One layer, as a [NetworkGraphPalette] sees it.
 *
 * [layerIndex] counts the drawn layers back to front, out of [layerCount]. [layer] is the spec of the
 * [side] the palette is asked about, so a palette can tell the two ends of a morph apart. [size] is
 * the surface the layer draws on, in pixels, which is also the coordinate space of the returned brush.
 */
public class NetworkGraphLayerContext internal constructor(
    public val layerIndex: Int,
    public val layerCount: Int,
    public val layer: NetworkGraphLayerSpec,
    public val side: NetworkGraphMorphSide,
    public val timeSeconds: Float,
    public val size: Size,
)
