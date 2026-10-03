@file:Suppress("MagicNumber")

package com.rossomak.flashcards.feature.debug.networkgraph

import kotlin.math.abs
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

/**
 * PROTOTYPE — throwaway, see [NetworkGraphPrototypeScreen].
 *
 * The overall shape of one layer, in three parts:
 * - [densityAt] decides where nodes are *present*: a node shows in proportion to the density at its
 *   home, so a shape is just a density field over the surface, from 0 to 1. It is evaluated every
 *   frame, against the current spec and surface size, which is what lets shapes morph and follow a
 *   resizing surface: nodes fade in and out where the field changes, but never move.
 * - [closenessAt] orders the nodes a morph switches: how near a point is to the shape, falling off
 *   over the whole surface instead of stopping at the shape's border, so even points far outside it
 *   still rank by distance.
 * - [waveOffsetY] *moves* every node's home over time, uniformly for neighbors, so the shape itself
 *   breathes while the mesh topology stays intact.
 *
 * Only the random phases live here; every shape value comes from the spec passed in, so one envelope
 * serves every frame of a morph.
 */
internal class NetworkGraphEnvelope(random: Random) {
    private val curvePhase = random.nextFloat() * TWO_PI
    private val thicknessPhase = random.nextFloat() * TWO_PI
    private val wavePhase = random.nextFloat() * TWO_PI

    /** The strongest weighted shape at the point, never under the spec's outlier floor. */
    fun densityAt(spec: NetworkGraphLayerSpec, width: Float, height: Float, x: Float, y: Float): Float {
        var density = spec.uniformWeight.coerceIn(0f, 1f)
        val ribbonWeight = spec.ribbonWeight.coerceIn(0f, 1f)
        if (ribbonWeight > density) density = max(density, ribbonWeight * ribbonDensity(spec, width, height, x, y))
        val bloomWeight = spec.bloomWeight.coerceIn(0f, 1f)
        if (bloomWeight > density) density = max(density, bloomWeight * bloomDensity(spec, width, height, x, y))
        return max(density, spec.envelopeFloor)
    }

    /**
     * How near the point is to the shape, from 0 (the far side of the surface) to 1 (on the ribbon's
     * center line or the bloom's center), as the strongest weighted shape like [densityAt]. Uniform
     * is equally near everywhere.
     */
    fun closenessAt(spec: NetworkGraphLayerSpec, width: Float, height: Float, x: Float, y: Float): Float {
        var closeness = spec.uniformWeight.coerceIn(0f, 1f)
        val ribbonWeight = spec.ribbonWeight.coerceIn(0f, 1f)
        if (ribbonWeight > closeness) {
            val fromCenterLine = abs(y - ribbonCenterAt(spec, width, height, x)) / height
            closeness = max(closeness, ribbonWeight * (1f - fromCenterLine.coerceIn(0f, 1f)))
        }
        val bloomWeight = spec.bloomWeight.coerceIn(0f, 1f)
        if (bloomWeight > closeness) {
            val fromCenter = hypot(spec.bloomX * width - x, spec.bloomY * height - y) / hypot(width, height)
            closeness = max(closeness, bloomWeight * (1f - fromCenter.coerceIn(0f, 1f)))
        }
        return closeness
    }

    /** Vertical wave offset at [x]; [waveTravel] is the wave's accumulated travel, speed already applied. */
    fun waveOffsetY(spec: NetworkGraphLayerSpec, width: Float, height: Float, x: Float, waveTravel: Float): Float {
        if (spec.waveAmplitude == 0f) return 0f
        val phase = x / width * TWO_PI * WAVE_CYCLES - waveTravel + wavePhase
        return spec.waveAmplitude * height * sin(phase)
    }

    private fun ribbonDensity(spec: NetworkGraphLayerSpec, width: Float, height: Float, x: Float, y: Float): Float {
        val across = x / width
        val center = ribbonCenterAt(spec, width, height, x)
        val thicknessScale = 1f + RIBBON_THICKNESS_VARIATION * sin(across * TWO_PI * RIBBON_THICKNESS_CYCLES + thicknessPhase)
        val halfThickness = (spec.ribbonWidth * height * 0.5f * thicknessScale).coerceAtLeast(1f)
        return 1f - smoothStep(RIBBON_SOFT_EDGE, 1f, abs(y - center) / halfThickness)
    }

    /** The ribbon's center line at [x], meander and tilt included. */
    private fun ribbonCenterAt(spec: NetworkGraphLayerSpec, width: Float, height: Float, x: Float): Float {
        val slope = tan(spec.ribbonTilt.coerceIn(-80f, 80f) * DEGREES_TO_RADIANS)
        val meander = spec.ribbonCurve * sin(x / width * TWO_PI * RIBBON_CURVE_CYCLES + curvePhase)
        return (spec.ribbonCenter + meander) * height + slope * (x - width / 2f)
    }

    /** Dense at the bloom center, thinning out over [NetworkGraphLayerSpec.bloomReach]. */
    private fun bloomDensity(spec: NetworkGraphLayerSpec, width: Float, height: Float, x: Float, y: Float): Float {
        val deltaX = spec.bloomX * width - x
        val deltaY = spec.bloomY * height - y
        val distance = sqrt(deltaX * deltaX + deltaY * deltaY) / max(width, height)
        return 1f - smoothStep(0f, spec.bloomReach.coerceAtLeast(0.01f), distance)
    }
}

internal const val TWO_PI = 6.2831855f

internal fun smoothStep(edgeStart: Float, edgeEnd: Float, value: Float): Float {
    val progress = ((value - edgeStart) / (edgeEnd - edgeStart)).coerceIn(0f, 1f)
    return progress * progress * (3f - 2f * progress)
}
