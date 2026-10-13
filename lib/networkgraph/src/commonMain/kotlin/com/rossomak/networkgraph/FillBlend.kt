package com.rossomak.networkgraph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp as lerpOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.util.lerp
import com.rossomak.networkgraph.NetworkGraphFill.Custom
import com.rossomak.networkgraph.NetworkGraphFill.Linear
import com.rossomak.networkgraph.NetworkGraphFill.Radial
import com.rossomak.networkgraph.NetworkGraphFill.Solid
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val HALF_TURN_DEGREES = 180f
private const val FULL_TURN_DEGREES = 360f
private const val HALF_WAY = 0.5f

/**
 * A layer's fill part way through a morph: one [Of] fill, or a [Crossfade] of the fill a morph left
 * on screen into the one it is heading for, which the renderer paints by laying [Crossfade.to]'s brush
 * over [Crossfade.from]'s at [Crossfade.fraction]. Two plain-value fills never crossfade: [blendFills]
 * interpolates them into one.
 */
internal sealed interface FillState {
    /** [fill] as the layer [layer] at morph side [side] paints it; a [Custom] fill is asked about exactly these. */
    data class Of(val fill: NetworkGraphFill, val layer: NetworkGraphLayerSpec, val side: NetworkGraphMorphSide) : FillState

    data class Crossfade(val from: FillState, val to: Of, val fraction: Float) : FillState
}

/** [layer]'s own fill, else [spec]'s, else [inherited]. */
internal fun resolveFill(layer: NetworkGraphLayerSpec, spec: NetworkGraphSpec, inherited: NetworkGraphFill): NetworkGraphFill =
    layer.fill ?: spec.fill ?: inherited

/** [from] part way to [to]: interpolated when both are plain values, crossfaded otherwise. */
internal fun blendFills(from: FillState, to: FillState.Of, fraction: Float): FillState = when {
    fraction <= 0f -> from
    fraction >= 1f -> to
    from is FillState.Of && from.fill == to.fill && from.fill !is Custom -> to
    from is FillState.Of && from.fill !is Custom && to.fill !is Custom -> to.copy(fill = lerpFill(from.fill, to.fill, fraction))
    else -> FillState.Crossfade(from, to, fraction)
}

/**
 * Two plain-value fills interpolated. A [Solid] end becomes the other end's kind with a single color,
 * and a [Linear] end and a [Radial] end each first ease into their average color, meeting there half way.
 */
internal fun lerpFill(from: NetworkGraphFill, to: NetworkGraphFill, fraction: Float): NetworkGraphFill = when {
    from is Custom || to is Custom -> if (fraction < HALF_WAY) from else to
    from is Solid && to is Solid -> Solid(lerpColor(from.color, to.color, fraction))
    from is Linear && to is Radial || from is Radial && to is Linear -> {
        val middle = Solid(lerpColor(averageColor(from), averageColor(to), HALF_WAY))
        if (fraction < HALF_WAY) lerpFill(from, middle, fraction / HALF_WAY) else lerpFill(middle, to, (fraction - HALF_WAY) / HALF_WAY)
    }
    else -> lerpGradient(from.asKindOf(to), to.asKindOf(from), fraction)
}

/** A [Solid] as a gradient of one color shaped like [other]; any other fill unchanged. */
private fun NetworkGraphFill.asKindOf(other: NetworkGraphFill): NetworkGraphFill {
    if (this !is Solid) return this
    val stops = listOf(GradientStop(0f, color), GradientStop(1f, color))
    return when (other) {
        is Linear -> Linear(stops, other.angleDegrees)
        is Radial -> Radial(stops, other.center, other.radius)
        else -> this
    }
}

/** Two gradients of one kind, stop by stop over the union of their stop positions. */
private fun lerpGradient(from: NetworkGraphFill, to: NetworkGraphFill, fraction: Float): NetworkGraphFill {
    val fromStops = from.gradientStops()
    val toStops = to.gradientStops()
    val positions = (fromStops.map { it.position } + toStops.map { it.position }).distinct().sorted()
    val stops = positions.map { position -> GradientStop(position, lerpColor(fromStops.colorAt(position), toStops.colorAt(position), fraction)) }
    return when {
        from is Linear && to is Linear -> Linear(stops, from.angleDegrees + shortestTurn(from.angleDegrees, to.angleDegrees) * fraction)
        from is Radial && to is Radial -> Radial(stops, lerpOffset(from.center, to.center, fraction), lerp(from.radius, to.radius, fraction))
        else -> if (fraction < HALF_WAY) from else to
    }
}

private fun shortestTurn(fromDegrees: Float, toDegrees: Float): Float {
    val delta = (toDegrees - fromDegrees) % FULL_TURN_DEGREES
    return when {
        delta > HALF_TURN_DEGREES -> delta - FULL_TURN_DEGREES
        delta < -HALF_TURN_DEGREES -> delta + FULL_TURN_DEGREES
        else -> delta
    }
}

private fun NetworkGraphFill.gradientStops(): List<GradientStop> = when (this) {
    is Linear -> stops.sortedBy { it.position }
    is Radial -> stops.sortedBy { it.position }
    is Solid -> listOf(GradientStop(0f, color))
    is Custom -> emptyList()
}

/**
 * The color a gradient through these stops (sorted by position) draws at [position]: the nearest stop's
 * outside them, and in between the two stops mixed channel by channel, the way the gradient shader mixes
 * them, so resampling a gradient at extra positions doesn't change how it draws.
 */
private fun List<GradientStop>.colorAt(position: Float): Color {
    if (position <= first().position) return first().color
    if (position >= last().position) return last().color
    val upper = indexOfFirst { it.position >= position }
    val start = this[upper - 1]
    val end = this[upper]
    val span = end.position - start.position
    return if (span <= 0f) end.color else mixChannels(start.color, end.color, (position - start.position) / span)
}

private fun mixChannels(start: Color, end: Color, fraction: Float): Color = Color(
    red = lerp(start.red, end.red, fraction),
    green = lerp(start.green, end.green, fraction),
    blue = lerp(start.blue, end.blue, fraction),
    alpha = lerp(start.alpha, end.alpha, fraction),
)

/** The gradient's mean color along its length from 0 to 1, channel by channel. */
private fun averageColor(fill: NetworkGraphFill): Color {
    val stops = fill.gradientStops()
    val positions = (listOf(0f, 1f) + stops.map { it.position.coerceIn(0f, 1f) }).distinct().sorted()
    var red = 0f
    var green = 0f
    var blue = 0f
    var alpha = 0f
    positions.zipWithNext { start, end ->
        val middle = mixChannels(stops.colorAt(start), stops.colorAt(end), HALF_WAY)
        val share = end - start
        red += middle.red * share
        green += middle.green * share
        blue += middle.blue * share
        alpha += middle.alpha * share
    }
    return Color(red = red, green = green, blue = blue, alpha = alpha)
}

/** The brush a plain-value fill paints a [size] surface with. */
internal fun NetworkGraphFill.brushOver(size: Size): Brush? = when (this) {
    is Solid -> SolidColor(color)
    is Linear -> if (stops.size == 1) {
        SolidColor(stops.single().color)
    } else {
        val radians = angleDegrees * PI.toFloat() / HALF_TURN_DEGREES
        val directionX = cos(radians)
        val directionY = sin(radians)
        val halfLength = abs(size.width / 2f * directionX) + abs(size.height / 2f * directionY)
        val center = Offset(size.width / 2f, size.height / 2f)
        val reach = Offset(directionX * halfLength, directionY * halfLength)
        Brush.linearGradient(colorStops = gradientStops().toColorStops(), start = center - reach, end = center + reach)
    }
    is Radial -> if (stops.size == 1) {
        SolidColor(stops.single().color)
    } else {
        Brush.radialGradient(
            colorStops = gradientStops().toColorStops(),
            center = Offset(center.x * size.width, center.y * size.height),
            radius = max(radius * max(size.width, size.height), 1f),
        )
    }
    is Custom -> null
}

private fun List<GradientStop>.toColorStops(): Array<Pair<Float, Color>> = Array(size) { index -> this[index].position.coerceIn(0f, 1f) to this[index].color }

/**
 * How [LayerField.draw] paints layer [layerIndex] of [layerCount] with [state] at [timeSeconds] on a
 * [size] surface: a solid color drawn directly when every brush involved is one, else a masked stack.
 * A brush laid over nothing but itself collapses, so a custom fill that reads the same at both ends of
 * a morph draws once, and an opaque overlay hides everything under it.
 */
internal fun FillState.toLayerFill(layerIndex: Int, layerCount: Int, timeSeconds: Float, size: Size): LayerFill {
    val brushes = ArrayList<Pair<Brush, Float>>()
    fun collect(state: FillState) {
        when (state) {
            is FillState.Of -> brushes += state.brush(layerIndex, layerCount, timeSeconds, size) to 1f
            is FillState.Crossfade -> {
                collect(state.from)
                brushes += state.to.brush(layerIndex, layerCount, timeSeconds, size) to state.fraction
            }
        }
    }
    collect(this)
    val distinct = ArrayList<Pair<Brush, Float>>()
    for ((brush, alpha) in brushes) {
        when {
            distinct.isEmpty() || alpha >= 1f -> {
                distinct.clear()
                distinct += brush to 1f
            }
            alpha <= 0f || distinct.all { (kept, _) -> kept == brush } -> Unit
            else -> distinct += brush to alpha
        }
    }
    if (distinct.all { (brush, _) -> brush is SolidColor }) {
        val color = distinct.drop(1).fold((distinct.first().first as SolidColor).value) { color, (brush, alpha) ->
            lerpColor(color, (brush as SolidColor).value, alpha)
        }
        return LayerFill.Solid(color)
    }
    return LayerFill.Masked(distinct.first().first, distinct.drop(1).map { (brush, alpha) -> LayerFill.Overlay(brush, alpha) })
}

private fun FillState.Of.brush(layerIndex: Int, layerCount: Int, timeSeconds: Float, size: Size): Brush =
    fill.brushOver(size) ?: (fill as Custom).brushFor(NetworkGraphLayerContext(layerIndex, layerCount, layer, side, timeSeconds, size))
