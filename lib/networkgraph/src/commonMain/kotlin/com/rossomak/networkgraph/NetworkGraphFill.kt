package com.rossomak.networkgraph

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * What a layer's edges, halos and dots are painted with: only its colors count, since the layer's own
 * alpha rules decide how opaque each element is. A fill's alpha, a stop's included, multiplies the
 * layer's [NetworkGraphLayerSpec.alpha].
 *
 * A layer paints with its own [NetworkGraphLayerSpec.fill], else its spec's [NetworkGraphSpec.fill],
 * else the fill passed to [networkGraphBackground], which defaults to [LocalNetworkGraphFill]. So one
 * fill set once colors every graph, and a single spec or layer can still override it.
 *
 * [Solid], [Linear] and [Radial] are plain values, and a morph between any two of them interpolates:
 * colors in Oklab, gradient stops matched by position, angle, center and radius eased between the
 * ends. A [Solid] end acts as a gradient of one color, and a [Linear] end morphing into a [Radial] one
 * passes through a single color half way. A [Custom] fill is a brush of the caller's own, possibly
 * animated, so a morph involving one crossfades the two ends instead, both still animating.
 *
 * Gradient geometry is in fractions of the surface, so one fill covers an app bar and a full screen
 * alike. It follows the surface's own axes and is not mirrored for right-to-left layouts.
 */
@Immutable
public sealed interface NetworkGraphFill {

    /** Every element in [color]. */
    public data class Solid(val color: Color) : NetworkGraphFill

    /**
     * A linear gradient across the whole surface along [angleDegrees]: 0 runs from the start edge to the
     * end edge, 90 from top to bottom. The gradient line passes through the surface's center and is just
     * long enough for its first and last stop to touch opposite corners.
     */
    public data class Linear(val stops: List<GradientStop>, val angleDegrees: Float = 0f) : NetworkGraphFill {
        init {
            require(stops.isNotEmpty()) { "A gradient needs at least one stop" }
        }
    }

    /**
     * A radial gradient around [center], given as fractions of the surface's width and height, out to
     * [radius], as a fraction of its longer side; past the last stop the last color holds.
     */
    public data class Radial(
        val stops: List<GradientStop>,
        val center: Offset = Offset(CENTER_FRACTION, CENTER_FRACTION),
        val radius: Float = DEFAULT_RADIAL_RADIUS,
    ) : NetworkGraphFill {
        init {
            require(stops.isNotEmpty()) { "A gradient needs at least one stop" }
        }
    }

    /**
     * Any brush, asked for every frame: once per layer, and once per end of a morph while one runs, so
     * it may animate with [NetworkGraphLayerContext.timeSeconds] or with snapshot state of its own. The
     * brush is laid out over the surface, in pixels ([NetworkGraphLayerContext.size]). A [SolidColor]
     * brush paints exactly like a [Solid] fill.
     *
     * Fills are compared with `equals`, so keep a custom fill stable: an `object`, a data class, or an
     * instance held with `remember`. A new lambda every composition is a new fill every time.
     *
     * [SolidColor]: androidx.compose.ui.graphics.SolidColor
     */
    public fun interface Custom : NetworkGraphFill {
        public fun brushFor(context: NetworkGraphLayerContext): Brush
    }

    public companion object {
        /** A [Linear] gradient through [colors], evenly spaced. */
        public fun linear(colors: List<Color>, angleDegrees: Float = 0f): Linear = Linear(evenStops(colors), angleDegrees)

        /** A [Radial] gradient through [colors], evenly spaced from [center] outward. */
        public fun radial(
            colors: List<Color>,
            center: Offset = Offset(CENTER_FRACTION, CENTER_FRACTION),
            radius: Float = DEFAULT_RADIAL_RADIUS,
        ): Radial = Radial(evenStops(colors), center, radius)

        private fun evenStops(colors: List<Color>): List<GradientStop> = when (colors.size) {
            0 -> throw IllegalArgumentException("A gradient needs at least one color")
            1 -> listOf(GradientStop(0f, colors.single()))
            else -> colors.mapIndexed { index, color -> GradientStop(index / colors.lastIndex.toFloat(), color) }
        }
    }
}

/** One color of a gradient fill, at [position] from 0 (the gradient's start) to 1 (its end). */
@Immutable
public data class GradientStop(val position: Float, val color: Color)

/** The fill every network graph below inherits when neither its layer nor its spec sets one. */
public val LocalNetworkGraphFill: ProvidableCompositionLocal<NetworkGraphFill> =
    staticCompositionLocalOf { NetworkGraphFill.Solid(DefaultFillColor) }

/** A mid grey that reads on light and dark surfaces alike, for a graph no caller has colored. */
private val DefaultFillColor = Color(0xFF8A8A8A)

private const val CENTER_FRACTION = 0.5f
private const val DEFAULT_RADIAL_RADIUS = 0.5f

/** Which end of a morph a [NetworkGraphLayerContext] describes; a still background is its [From] end. */
public enum class NetworkGraphMorphSide { From, To }

/**
 * One layer, as a [NetworkGraphFill.Custom] sees it.
 *
 * [layerIndex] counts the spec's layers back to front, out of [layerCount]. [layer] is the spec of the
 * [side] the fill is asked about, so a fill can tell the two ends of a morph apart. [size] is the
 * surface the layer draws on, in pixels, which is also the coordinate space of the returned brush.
 */
public class NetworkGraphLayerContext internal constructor(
    public val layerIndex: Int,
    public val layerCount: Int,
    public val layer: NetworkGraphLayerSpec,
    public val side: NetworkGraphMorphSide,
    public val timeSeconds: Float,
    public val size: Size,
)
