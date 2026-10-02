@file:Suppress("MagicNumber")

package com.rossomak.flashcards.feature.debug.networkgraph

import androidx.compose.ui.graphics.Color

private val BrandIndigo = Color(0xFF2A2E8F)

/**
 * PROTOTYPE — throwaway, see [NetworkGraphPrototypeScreen].
 *
 * The curated variants, one pager page each. Each tests one idea against the others; [baseline] is
 * the control, a port of the look the single-renderer revision shipped. New presets come from the
 * tuning sheet's "copy spec" button, pasted here.
 */
object NetworkGraphPresets {

    private val ribbonFront = NetworkGraphLayerSpec(
        envelope = EnvelopeKind.Ribbon,
        density = 16f,
        jitter = 0.4f,
        driftAmplitude = 0.3f,
        ribbonCenter = 0.62f,
        ribbonWidth = 0.4f,
        ribbonCurve = 0.1f,
        waveAmplitude = 0.03f,
        waveSpeed = 0.25f,
        maxEdgeFactor = 2.4f,
        minAngleDegrees = 14f,
        edgeKeepChance = 0.92f,
        edgeAlpha = 0.6f,
        nodeSizeDp = 2f,
    )

    /** The far mesh of the two-layer references: sparser, bigger dimmer nodes, slower (parallax). */
    private val ribbonGhost = NetworkGraphLayerSpec(
        envelope = EnvelopeKind.Ribbon,
        density = 8f,
        jitter = 0.45f,
        driftAmplitude = 0.35f,
        driftSpeed = 0.5f,
        ribbonCenter = 0.55f,
        ribbonWidth = 0.7f,
        ribbonCurve = 0.15f,
        envelopeFloor = 0.05f,
        waveAmplitude = 0.02f,
        waveSpeed = 0.15f,
        maxEdgeFactor = 2.6f,
        edgeKeepChance = 0.8f,
        strokeWidthDp = 1f,
        edgeAlpha = 0.5f,
        lengthFalloff = 0.3f,
        nodeSizeDp = 4f,
        nodeSizeVariance = 0.2f,
        alpha = 0.35f,
    )

    val baseline = NetworkGraphSpec(
        name = "Baseline",
        note = "Control: the previous look — uniform field, proximity edges, depth, pulse",
        seed = 1,
        layers = listOf(
            NetworkGraphLayerSpec(
                envelope = EnvelopeKind.Uniform,
                edgeRule = EdgeRule.Proximity,
                fillChance = 0.88f,
                jitter = 0.5f,
                driftAmplitude = 0.6f,
                maxEdgeFactor = 1.45f,
                edgeKeepChance = 1f,
                strokeWidthDp = 1f,
                edgeAlpha = 0.55f,
                lengthFalloff = 0.6f,
                pulse = true,
                nodeSizeDp = 2.2f,
                hotNodeShare = 0.2f,
                glowStrength = 0.16f,
                depthDimming = 0.45f,
            ),
        ),
    )

    val ribbonMesh = NetworkGraphSpec(
        name = "Ribbon mesh",
        note = "Wavy triangulated band, fixed topology, single layer",
        seed = 2,
        layers = listOf(ribbonFront),
    )

    val ribbonPlusGhost = NetworkGraphSpec(
        name = "Ribbon + ghost",
        note = "Ribbon over a faint, sparser, slower far mesh — refs 1 and 4",
        seed = 3,
        layers = listOf(ribbonGhost, ribbonFront),
    )

    val glowConstellation = NetworkGraphSpec(
        name = "Glow constellation",
        note = "Ribbon with a hot node subset haloed; long edges fade — ref 3",
        seed = 4,
        layers = listOf(
            ribbonFront.copy(
                density = 15f,
                lengthFalloff = 0.8f,
                hotNodeShare = 0.3f,
                glowRadiusDp = 18f,
                glowStrength = 0.5f,
                nodeSizeVariance = 0.45f,
            ),
        ),
    )

    val cornerBloom = NetworkGraphSpec(
        name = "Corner bloom",
        note = "Dense at the top-end corner, thinning out — built for app bars; brand indigo on surface",
        seed = 5,
        layers = listOf(
            NetworkGraphLayerSpec(
                envelope = EnvelopeKind.CornerBloom,
                density = 15f,
                bloomReach = 0.8f,
                envelopeFloor = 0.02f,
                maxEdgeFactor = 2.4f,
                minAngleDegrees = 14f,
                lengthFalloff = 0.6f,
                nodeSizeVariance = 0.4f,
                hotNodeShare = 0.1f,
                glowStrength = 0.3f,
                surfaceColor = BrandIndigo,
            ),
        ),
    )

    val depthFog = NetworkGraphSpec(
        name = "Depth fog",
        note = "Uniform single mesh, per-node depth dims and shrinks far nodes",
        seed = 6,
        layers = listOf(
            NetworkGraphLayerSpec(
                envelope = EnvelopeKind.Uniform,
                density = 14f,
                driftAmplitude = 0.45f,
                maxEdgeFactor = 2.2f,
                minAngleDegrees = 14f,
                edgeKeepChance = 0.85f,
                lengthFalloff = 0.4f,
                nodeSizeDp = 2.6f,
                nodeSizeVariance = 0.6f,
                depthDimming = 0.8f,
            ),
        ),
    )

    val livingDelaunay = NetworkGraphSpec(
        name = "Living Delaunay",
        note = "Ribbon re-triangulated every frame; flipped edges fade in and out",
        seed = 7,
        layers = listOf(
            ribbonFront.copy(
                edgeRule = EdgeRule.LivingDelaunay,
                driftAmplitude = 0.7f,
                driftSpeed = 1.3f,
            ),
        ),
    )

    val all: List<NetworkGraphSpec> = listOf(
        baseline,
        ribbonMesh,
        ribbonPlusGhost,
        glowConstellation,
        cornerBloom,
        depthFog,
        livingDelaunay,
    )

    /** What the tuning sheet inserts as a new back layer: [ribbonGhost]'s far-mesh look, over the whole surface. */
    val defaultBackLayer = ribbonGhost.copy(envelope = EnvelopeKind.Uniform)
}
