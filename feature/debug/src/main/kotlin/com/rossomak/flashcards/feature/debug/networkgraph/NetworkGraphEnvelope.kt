@file:Suppress("MagicNumber")

package com.rossomak.flashcards.feature.debug.networkgraph

import com.rossomak.flashcards.feature.debug.networkgraph.ShapePrimitive.Bloom
import com.rossomak.flashcards.feature.debug.networkgraph.ShapePrimitive.Ribbon
import com.rossomak.flashcards.feature.debug.networkgraph.ShapePrimitive.Uniform
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

// The ribbon's center line and thickness each follow one slow sine across the width, at cycle counts
// far enough apart that the band never looks like a regular wave.
private const val RIBBON_CURVE_CYCLES = 0.8f
private const val RIBBON_THICKNESS_CYCLES = 1.3f
private const val RIBBON_THICKNESS_VARIATION = 0.35f

// Density holds at full until this fraction of the ribbon's half-thickness, then falls off, so the
// band's border is ragged rather than a ruled line.
private const val RIBBON_SOFT_EDGE = 0.55f
private const val WAVE_CYCLES = 1.1f
private const val DEGREES_TO_RADIANS = 0.017453292f

/** Seeds of the second and later ribbons' phases are this far apart, so two ribbons never meander alike. */
private const val RIBBON_PHASE_SEED_STRIDE = 104_729

/**
 * How far past the surface's half width a ribbon's stand-in ellipse reaches, so its ends still cover
 * the band at the surface's edges rather than pinching to a point there.
 */
private const val RIBBON_STAND_IN_SPAN = 1.15f

/**
 * PROTOTYPE — throwaway, see [NetworkGraphPrototypeScreen].
 *
 * The overall shape of one layer, from its [ShapePrimitive]s, in four parts:
 * - [densityAt] decides where nodes are *present*: a node shows in proportion to the density at its
 *   home, so a shape is just a density field over the surface, from 0 to 1. It is evaluated every
 *   frame, against the current spec and surface size, which is what lets shapes morph and follow a
 *   resizing surface: nodes fade in and out where the field changes, but never move.
 * - [closenessAt] orders the nodes a morph switches: how near a point is to the shape, falling off
 *   over the whole surface instead of stopping at the shape's border, so even points far outside it
 *   still rank by distance.
 * - [standIns] stands each primitive in for an ellipse, which is what a morph's comets travel between.
 * - [waveOffsetY] *moves* every node's home over time, uniformly for neighbors, so the shape itself
 *   breathes while the mesh topology stays intact.
 *
 * Only the random phases live here; every shape value comes from the spec passed in, so one envelope
 * serves every frame of a morph. Each ribbon takes its meander phases by its place among the spec's
 * ribbons, so two ribbons in one spec meander differently, and the first keeps the phases a single
 * ribbon always had.
 */
internal class NetworkGraphEnvelope(private val seed: Int) {
    private val firstRibbonPhases: RibbonPhases
    private val wavePhase: Float
    private val laterRibbonPhases = ArrayList<RibbonPhases>()

    init {
        val random = Random(seed)
        firstRibbonPhases = RibbonPhases(curve = random.nextFloat() * TWO_PI, thickness = random.nextFloat() * TWO_PI)
        wavePhase = random.nextFloat() * TWO_PI
    }

    /** The strongest weighted primitive at the point, never under the spec's outlier floor. */
    fun densityAt(spec: NetworkGraphLayerSpec, width: Float, height: Float, x: Float, y: Float): Float {
        var density = 0f
        var ribbonOrdinal = 0
        for (primitive in spec.shapes) {
            val weight = primitive.weight.coerceIn(0f, 1f)
            val ordinal = if (primitive is Ribbon) ribbonOrdinal++ else 0
            if (weight <= density) continue
            val falloff = when (primitive) {
                is Uniform -> 1f
                is Ribbon -> ribbonDensity(primitive, ribbonPhases(ordinal), width, height, x, y)
                is Bloom -> bloomDensity(primitive, width, height, x, y)
            }
            density = max(density, weight * falloff)
        }
        return max(density, spec.envelopeFloor)
    }

    /**
     * How near the point is to the shape, from 0 (the far side of the surface) to 1 (on a ribbon's
     * center line or a bloom's center), as the strongest weighted primitive like [densityAt]. Uniform
     * is equally near everywhere.
     */
    fun closenessAt(spec: NetworkGraphLayerSpec, width: Float, height: Float, x: Float, y: Float): Float {
        var closeness = 0f
        var ribbonOrdinal = 0
        for (primitive in spec.shapes) {
            val weight = primitive.weight.coerceIn(0f, 1f)
            val ordinal = if (primitive is Ribbon) ribbonOrdinal++ else 0
            if (weight <= closeness) continue
            val nearness = when (primitive) {
                is Uniform -> 1f
                is Ribbon -> 1f - (abs(y - ribbonCenterAt(primitive, ribbonPhases(ordinal), width, height, x)) / height).coerceIn(0f, 1f)
                is Bloom -> 1f - (hypot(primitive.x * width - x, primitive.y * height - y) / hypot(width, height)).coerceIn(0f, 1f)
            }
            closeness = max(closeness, weight * nearness)
        }
        return closeness
    }

    /**
     * One ellipse per ribbon and bloom of [spec] with any weight, in pixels: a bloom is its own circle,
     * falloff included, and a ribbon a long flat ellipse along its band. Uniform has no place to stand
     * in for, so it has none.
     */
    fun standIns(spec: NetworkGraphLayerSpec, width: Float, height: Float): List<ShapeStandIn> {
        var ribbonOrdinal = 0
        return spec.shapes.mapNotNull { primitive ->
            val weight = primitive.weight.coerceIn(0f, 1f)
            when (primitive) {
                is Uniform -> null
                is Ribbon -> {
                    val phases = ribbonPhases(ribbonOrdinal++)
                    val slope = ribbonSlope(primitive)
                    ShapeStandIn(
                        centerX = width / 2f,
                        centerY = ribbonCenterAt(primitive, phases, width, height, width / 2f),
                        majorRadius = RIBBON_STAND_IN_SPAN * width / 2f * sqrt(1f + slope * slope),
                        minorRadius = (primitive.width * height * 0.5f).coerceAtLeast(1f),
                        angle = atan(slope),
                        plateau = RIBBON_SOFT_EDGE,
                        weight = weight,
                    )
                }
                is Bloom -> {
                    val radius = primitive.reach.coerceAtLeast(MIN_BLOOM_REACH) * max(width, height)
                    ShapeStandIn(
                        centerX = primitive.x * width,
                        centerY = primitive.y * height,
                        majorRadius = radius,
                        minorRadius = radius,
                        angle = 0f,
                        plateau = primitive.plateau.coerceIn(0f, MAX_BLOOM_PLATEAU),
                        weight = weight,
                    )
                }
            }.takeIf { weight > 0f }
        }
    }

    /** Vertical wave offset at [x]; [waveTravel] is the wave's accumulated travel, speed already applied. */
    fun waveOffsetY(spec: NetworkGraphLayerSpec, width: Float, height: Float, x: Float, waveTravel: Float): Float {
        if (spec.waveAmplitude == 0f) return 0f
        val phase = x / width * TWO_PI * WAVE_CYCLES - waveTravel + wavePhase
        return spec.waveAmplitude * height * sin(phase)
    }

    private fun ribbonPhases(ordinal: Int): RibbonPhases {
        if (ordinal == 0) return firstRibbonPhases
        while (laterRibbonPhases.size < ordinal) {
            val random = Random(seed + (laterRibbonPhases.size + 1) * RIBBON_PHASE_SEED_STRIDE)
            laterRibbonPhases += RibbonPhases(curve = random.nextFloat() * TWO_PI, thickness = random.nextFloat() * TWO_PI)
        }
        return laterRibbonPhases[ordinal - 1]
    }

    private fun ribbonDensity(ribbon: Ribbon, phases: RibbonPhases, width: Float, height: Float, x: Float, y: Float): Float {
        val across = x / width
        val center = ribbonCenterAt(ribbon, phases, width, height, x)
        val thicknessScale = 1f + RIBBON_THICKNESS_VARIATION * sin(across * TWO_PI * RIBBON_THICKNESS_CYCLES + phases.thickness)
        val halfThickness = (ribbon.width * height * 0.5f * thicknessScale).coerceAtLeast(1f)
        return 1f - smoothStep(RIBBON_SOFT_EDGE, 1f, abs(y - center) / halfThickness)
    }

    /** The ribbon's center line at [x], meander and tilt included. */
    private fun ribbonCenterAt(ribbon: Ribbon, phases: RibbonPhases, width: Float, height: Float, x: Float): Float {
        val meander = ribbon.curve * sin(x / width * TWO_PI * RIBBON_CURVE_CYCLES + phases.curve)
        return (ribbon.center + meander) * height + ribbonSlope(ribbon) * (x - width / 2f)
    }

    private fun ribbonSlope(ribbon: Ribbon): Float = tan(ribbon.tilt.coerceIn(-MAX_RIBBON_TILT, MAX_RIBBON_TILT) * DEGREES_TO_RADIANS)

    /** Full density out to [Bloom.plateau] of the reach, thinning out to nothing at [Bloom.reach]. */
    private fun bloomDensity(bloom: Bloom, width: Float, height: Float, x: Float, y: Float): Float {
        val deltaX = bloom.x * width - x
        val deltaY = bloom.y * height - y
        val distance = sqrt(deltaX * deltaX + deltaY * deltaY) / max(width, height)
        val reach = bloom.reach.coerceAtLeast(MIN_BLOOM_REACH)
        return 1f - smoothStep(reach * bloom.plateau.coerceIn(0f, MAX_BLOOM_PLATEAU), reach, distance)
    }
}

private class RibbonPhases(val curve: Float, val thickness: Float)

private const val MIN_BLOOM_REACH = 0.01f
private const val MAX_BLOOM_PLATEAU = 0.95f
private const val MAX_RIBBON_TILT = 80f

/**
 * An ellipse standing in for one shape primitive, in pixels: what a morph's comet travels from and to.
 * Its density is [weight] out to [plateau] of the way to its rim, thinning out to nothing at the rim,
 * which for a bloom is exactly the bloom's own density.
 */
internal data class ShapeStandIn(
    val centerX: Float,
    val centerY: Float,
    val majorRadius: Float,
    val minorRadius: Float,
    /** Direction of the major axis, in radians. */
    val angle: Float,
    val plateau: Float,
    val weight: Float,
) {
    /** The density at the point, before [weight]. */
    fun falloffAt(x: Float, y: Float): Float {
        val deltaX = x - centerX
        val deltaY = y - centerY
        val cosine = cos(angle)
        val sine = sin(angle)
        val along = (deltaX * cosine + deltaY * sine) / majorRadius
        val across = (-deltaX * sine + deltaY * cosine) / minorRadius
        return 1f - smoothStep(plateau, 1f, sqrt(along * along + across * across))
    }
}

internal const val TWO_PI = 6.2831855f

internal fun smoothStep(edgeStart: Float, edgeEnd: Float, value: Float): Float {
    val progress = ((value - edgeStart) / (edgeEnd - edgeStart)).coerceIn(0f, 1f)
    return progress * progress * (3f - 2f * progress)
}
