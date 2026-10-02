@file:Suppress("MagicNumber")

package com.rossomak.flashcards.feature.debug.networkgraph

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
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

/**
 * PROTOTYPE — throwaway, see [NetworkGraphPrototypeScreen].
 *
 * The overall shape of one layer, in two halves:
 * - [densityAt] decides where nodes are *placed*: a cell keeps its node with this probability, so a
 *   shape is just a density field over the surface, from 0 to 1.
 * - [waveOffsetY] *moves* every node's home over time, uniformly for neighbors, so the shape itself
 *   breathes while the mesh topology stays intact.
 *
 * This is the seam future shape steering hangs off (sphere, corner clusters, morphs between shapes):
 * a node is always "its home plus its own drift", so steering only ever has to move homes — a morph
 * would spring each home toward a position sampled from the next envelope. Not built yet on purpose.
 */
internal class NetworkGraphEnvelope(
    private val spec: NetworkGraphLayerSpec,
    random: Random,
    private val width: Float,
    private val height: Float,
) {
    private val curvePhase = random.nextFloat() * TWO_PI
    private val thicknessPhase = random.nextFloat() * TWO_PI
    private val wavePhase = random.nextFloat() * TWO_PI

    fun densityAt(x: Float, y: Float): Float = when (spec.envelope) {
        EnvelopeKind.Uniform -> 1f
        EnvelopeKind.Ribbon -> max(spec.envelopeFloor, ribbonDensity(x, y))
        EnvelopeKind.CornerBloom -> max(spec.envelopeFloor, cornerDensity(x, y))
    }

    fun waveOffsetY(x: Float, timeSeconds: Float): Float {
        if (spec.waveAmplitude == 0f) return 0f
        val phase = x / width * TWO_PI * WAVE_CYCLES - timeSeconds * spec.waveSpeed + wavePhase
        return spec.waveAmplitude * height * sin(phase)
    }

    private fun ribbonDensity(x: Float, y: Float): Float {
        val across = x / width
        val center = (spec.ribbonCenter + spec.ribbonCurve * sin(across * TWO_PI * RIBBON_CURVE_CYCLES + curvePhase)) * height
        val thicknessScale = 1f + RIBBON_THICKNESS_VARIATION * sin(across * TWO_PI * RIBBON_THICKNESS_CYCLES + thicknessPhase)
        val halfThickness = (spec.ribbonWidth * height * 0.5f * thicknessScale).coerceAtLeast(1f)
        return 1f - smoothStep(RIBBON_SOFT_EDGE, 1f, abs(y - center) / halfThickness)
    }

    /** Dense at the top-end corner, thinning out over [NetworkGraphLayerSpec.bloomReach]. */
    private fun cornerDensity(x: Float, y: Float): Float {
        val deltaX = width - x
        val distance = sqrt(deltaX * deltaX + y * y) / max(width, height)
        return 1f - smoothStep(0f, spec.bloomReach.coerceAtLeast(0.01f), distance)
    }
}

internal const val TWO_PI = 6.2831855f

internal fun smoothStep(edgeStart: Float, edgeEnd: Float, value: Float): Float {
    val progress = ((value - edgeStart) / (edgeEnd - edgeStart)).coerceIn(0f, 1f)
    return progress * progress * (3f - 2f * progress)
}
