@file:Suppress("MagicNumber")

package com.rossomak.flashcards.feature.debug.networkgraph

import androidx.compose.ui.graphics.Color
import com.rossomak.flashcards.feature.debug.networkgraph.ShapePrimitive.Bloom
import com.rossomak.flashcards.feature.debug.networkgraph.ShapePrimitive.Ribbon
import com.rossomak.flashcards.feature.debug.networkgraph.ShapePrimitive.Uniform

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
        shapes = listOf(Ribbon(center = 0.62f, width = 0.4f)),
        density = 24f,
        jitter = 0.4f,
        driftAmplitude = 0.3f,
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
        shapes = listOf(Ribbon(center = 0.55f, width = 0.7f, curve = 0.15f)),
        density = 12f,
        jitter = 0.45f,
        driftAmplitude = 0.35f,
        driftSpeed = 0.5f,
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
                edgeRule = EdgeRule.Proximity,
                fillChance = 0.88f,
                jitter = 0.5f,
                driftAmplitude = 0.6f,
                maxEdgeFactor = 1.45f,
                edgeKeepChance = 1f,
                strokeWidthDp = 1f,
                edgeAlpha = 0.55f,
                lengthFalloff = 0.6f,
                pulseDepth = 1f,
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
                density = 23f,
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
                shapes = listOf(Bloom()),
                density = 23f,
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
                density = 21f,
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

    /** The front layer every [morphFamily] variant shares its seed, density, jitter and edge rule with. */
    private val morphBase = NetworkGraphLayerSpec(
        density = 23f,
        driftAmplitude = 0.35f,
        maxEdgeFactor = 2.4f,
        minAngleDegrees = 14f,
        lengthFalloff = 0.5f,
        nodeSizeVariance = 0.4f,
    )

    private val morphRibbonShape = Ribbon(center = 0.62f, width = 0.42f)

    private val morphRibbon = morphBase.copy(
        shapes = listOf(morphRibbonShape),
        waveAmplitude = 0.03f,
        waveSpeed = 0.25f,
    )

    private val morphBloom = morphBase.copy(
        shapes = listOf(Bloom()),
        hotNodeShare = 0.15f,
        glowStrength = 0.35f,
    )

    private const val MORPH_SEED = 11

    private fun morphVariant(name: String, note: String, vararg layers: NetworkGraphLayerSpec) =
        NetworkGraphSpec(name = name, note = note, seed = MORPH_SEED, layers = layers.toList())

    /**
     * One morph family: every variant shares a seed and its layers' density, jitter and edge rule, so
     * any two morph into each other smoothly. Back layers count from the front, so a variant with a
     * ghost layer fades it in against one without.
     */
    val morphFamily: List<NetworkGraphSpec> = listOf(
        morphVariant("Uniform field", "Even mesh over the whole surface", morphBase),
        morphVariant("Ribbon", "Band across the lower middle", morphRibbon),
        morphVariant(
            "Ribbon, high and narrow",
            "The band moved up, thinned and tilted",
            morphRibbon.copy(shapes = listOf(Ribbon(center = 0.3f, width = 0.22f, tilt = -12f))),
        ),
        morphVariant("Corner bloom", "Dense at the top-end corner, hot nodes glowing", morphBloom),
        morphVariant(
            "Bloom, bottom-start",
            "The bloom moved to the opposite corner and tightened",
            morphBloom.copy(shapes = listOf(Bloom(x = 0f, y = 1f, reach = 0.6f))),
        ),
        morphVariant("Ribbon + bloom", "Both shapes at once", morphRibbon.copy(shapes = listOf(morphRibbonShape, Bloom(weight = 0.9f, reach = 0.5f)))),
        morphVariant(
            "Uniform + ghost",
            "Even mesh over a faint, sparser back layer",
            ribbonGhost.copy(shapes = listOf(Uniform())),
            morphBase,
        ),
        morphVariant(
            "Centre disc",
            "A hard-edged disc in the middle of the surface",
            morphBloom.copy(shapes = listOf(Bloom(x = 0.5f, y = 0.5f, reach = 0.28f, plateau = 0.6f))),
        ),
        morphVariant(
            "Corner pair",
            "Blooms in the top-end and bottom-start corners at once",
            morphBloom.copy(shapes = listOf(Bloom(reach = 0.55f), Bloom(x = 0f, y = 1f, reach = 0.55f))),
        ),
        morphVariant(
            "Double ribbon",
            "Two thin bands, one high, one low",
            morphRibbon.copy(
                shapes = listOf(
                    Ribbon(center = 0.25f, width = 0.18f, curve = 0.06f, tilt = 6f),
                    Ribbon(center = 0.75f, width = 0.18f, curve = 0.06f, tilt = -6f),
                ),
            ),
        ),
    )

    private fun morphVariantNamed(name: String) = morphFamily.first { it.name == name }

    /** The fake onboarding pager's backgrounds, one per page. */
    val onboardingPages: List<NetworkGraphSpec> = listOf(morphFamily[0], morphFamily[2], morphFamily[3])

    /**
     * The second fake onboarding pager's backgrounds: one shape splitting in two, two primitives of one
     * kind turning into two of another, two merging into one, and a corner bloom crossing the surface.
     */
    val onboardingCometPages: List<NetworkGraphSpec> = listOf(
        "Centre disc",
        "Corner pair",
        "Double ribbon",
        "Centre disc",
        "Corner bloom",
        "Bloom, bottom-start",
    ).map(::morphVariantNamed)

    /** What the tuning sheet inserts as a new back layer: [ribbonGhost]'s far-mesh look, over the whole surface. */
    val defaultBackLayer = ribbonGhost.copy(shapes = listOf(Uniform()))
}
