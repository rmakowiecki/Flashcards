package com.rossomak.flashcards.feature.debug.networkgraph

import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Two stand-ins less different than this, as a share of the surface's diagonal (see [standInDifference]),
 * send no comet: a small nudge or a slight change of reach reads fine as the plain dissolve.
 */
internal const val COMET_MIN_DIFFERENCE = 0.15f

/**
 * PROTOTYPE — throwaway, see [NetworkGraphPrototypeScreen].
 *
 * One comet of a morph: an ellipse that leaves [source]'s stand-in and arrives at [target]'s, its
 * center, radii, angle and plateau all eased along the way, so the nodes between two shapes light up as
 * it passes. Its density is nothing at either end of the morph and peaks half way, so it never changes
 * a still frame, and the nodes it lights are ones neither end shows.
 */
internal class Comet(val source: ShapeStandIn, val target: ShapeStandIn) {

    /** The comet's density at the point at [fraction], before the layer's fill chance and comet strength. */
    fun densityAt(x: Float, y: Float, fraction: Float): Float {
        val strength = sin(PI.toFloat() * fraction.coerceIn(0f, 1f))
        if (strength <= 0f) return 0f
        val eased = smoothStep(0f, 1f, fraction)
        val ellipse = ShapeStandIn(
            centerX = lerp(source.centerX, target.centerX, eased),
            centerY = lerp(source.centerY, target.centerY, eased),
            majorRadius = lerp(source.majorRadius, target.majorRadius, eased),
            minorRadius = lerp(source.minorRadius, target.minorRadius, eased),
            angle = source.angle + halfTurnDelta(source.angle, target.angle) * eased,
            plateau = lerp(source.plateau, target.plateau, eased),
            weight = lerp(source.weight, target.weight, eased),
        )
        return strength * ellipse.weight * ellipse.falloffAt(x, y)
    }
}

/**
 * The comets a morph from [from] to [to] sends: each stand-in at one end goes to or comes from its least
 * different counterpart at the other, so a shape splitting into two sends a comet to each, two merging
 * into one each send theirs, and the reverse morph sends the same comets back. A pair less different
 * than [COMET_MIN_DIFFERENCE] sends none.
 */
internal fun cometsBetween(from: List<ShapeStandIn>, to: List<ShapeStandIn>, width: Float, height: Float): List<Comet> {
    if (from.isEmpty() || to.isEmpty()) return emptyList()
    val diagonal = hypot(width, height)
    val pairs = LinkedHashSet<Pair<Int, Int>>()
    from.forEachIndexed { source, standIn -> pairs += source to to.indices.minBy { standInDifference(standIn, to[it], diagonal) } }
    to.forEachIndexed { target, standIn -> pairs += from.indices.minBy { standInDifference(from[it], standIn, diagonal) } to target }
    return pairs
        .filter { (source, target) -> standInDifference(from[source], to[target], diagonal) >= COMET_MIN_DIFFERENCE }
        .map { (source, target) -> Comet(from[source], to[target]) }
}

/**
 * How different two stand-ins are, as a share of [diagonal]: how far the center moves plus how much
 * each radius changes, plus a turn of the major axis, counted only as far as the ellipse is elongated
 * enough for a turn to show.
 */
private fun standInDifference(first: ShapeStandIn, second: ShapeStandIn, diagonal: Float): Float {
    val travel = hypot(second.centerX - first.centerX, second.centerY - first.centerY)
    val radiusChange = abs(second.majorRadius - first.majorRadius) + abs(second.minorRadius - first.minorRadius)
    val elongation = min(elongationOf(first), elongationOf(second))
    val turn = abs(sin(halfTurnDelta(first.angle, second.angle))) * elongation * max(first.majorRadius, second.majorRadius)
    return (travel + radiusChange + turn) / diagonal
}

/** 0 for a circle, approaching 1 for a long thin ellipse. */
private fun elongationOf(standIn: ShapeStandIn): Float = 1f - standIn.minorRadius / standIn.majorRadius

/** The shortest turn from [from] to [to] for an axis, which looks the same half a turn round. */
private fun halfTurnDelta(from: Float, to: Float): Float {
    val halfTurn = PI.toFloat()
    var delta = (to - from) % halfTurn
    if (delta > halfTurn / 2f) delta -= halfTurn
    if (delta < -halfTurn / 2f) delta += halfTurn
    return delta
}
