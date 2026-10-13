@file:Suppress("MagicNumber")

package com.rossomak.networkgraph

import com.rossomak.networkgraph.ShapePrimitive.Bloom
import com.rossomak.networkgraph.ShapePrimitive.Ribbon
import com.rossomak.networkgraph.ShapePrimitive.Uniform

/**
 * Curated starting points, each built around one idea; [baseline] is the plainest, a uniform field
 * with proximity edges. None of them sets a fill, so each is painted with whatever fill it inherits
 * (see [NetworkGraphFill]).
 */
public object NetworkGraphPresets {

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

    /** A far mesh for a back layer: sparser, bigger dimmer nodes, slower (parallax). */
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

    /** Uniform field, proximity edges, depth and pulse. */
    public val baseline: NetworkGraphSpec = NetworkGraphSpec(
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

    /** Wavy triangulated band, fixed topology, single layer. */
    public val ribbonMesh: NetworkGraphSpec = NetworkGraphSpec(
        seed = 2,
        layers = listOf(ribbonFront),
    )

    /** Ribbon over a faint, sparser, slower far mesh. */
    public val ribbonPlusGhost: NetworkGraphSpec = NetworkGraphSpec(
        seed = 3,
        layers = listOf(ribbonGhost, ribbonFront),
    )

    /** Ribbon with a hot node subset haloed; long edges fade. */
    public val glowConstellation: NetworkGraphSpec = NetworkGraphSpec(
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

    /** Dense at the top-end corner, thinning out; built for app bars. */
    public val cornerBloom: NetworkGraphSpec = NetworkGraphSpec(
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
            ),
        ),
    )

    /** Uniform single mesh; per-node depth dims and shrinks far nodes. */
    public val depthFog: NetworkGraphSpec = NetworkGraphSpec(
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

    /** Ribbon re-triangulated every frame; flipped edges fade in and out. */
    public val livingDelaunay: NetworkGraphSpec = NetworkGraphSpec(
        seed = 7,
        layers = listOf(
            ribbonFront.copy(
                edgeRule = EdgeRule.LivingDelaunay,
                driftAmplitude = 0.7f,
                driftSpeed = 1.3f,
            ),
        ),
    )

    public val all: List<NetworkGraphSpec> = listOf(
        baseline,
        ribbonMesh,
        ribbonPlusGhost,
        glowConstellation,
        cornerBloom,
        depthFog,
        livingDelaunay,
    )

    /** The front layer every [MorphFamily] variant shares its seed, density, jitter and edge rule with. */
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

    private fun morphVariant(vararg layers: NetworkGraphLayerSpec) = NetworkGraphSpec(seed = MORPH_SEED, layers = layers.toList())

    /**
     * One morph family: every variant shares a seed and its layers' density, jitter and edge rule, so
     * any two morph into each other without rebuilding a mesh. Back layers count from the front, so a
     * variant with a ghost layer fades it in against one without.
     */
    public object MorphFamily {
        /** Even mesh over the whole surface. */
        public val uniformField: NetworkGraphSpec = morphVariant(morphBase)

        /** Band across the lower middle. */
        public val ribbon: NetworkGraphSpec = morphVariant(morphRibbon)

        /** The band moved up, thinned and tilted. */
        public val highNarrowRibbon: NetworkGraphSpec =
            morphVariant(morphRibbon.copy(shapes = listOf(Ribbon(center = 0.3f, width = 0.22f, tilt = -12f))))

        /** Dense at the top-end corner, hot nodes glowing. */
        public val cornerBloom: NetworkGraphSpec = morphVariant(morphBloom)

        /** The bloom moved to the opposite corner and tightened. */
        public val bottomStartBloom: NetworkGraphSpec = morphVariant(morphBloom.copy(shapes = listOf(Bloom(x = 0f, y = 1f, reach = 0.6f))))

        /** Both shapes at once. */
        public val ribbonAndBloom: NetworkGraphSpec =
            morphVariant(morphRibbon.copy(shapes = listOf(morphRibbonShape, Bloom(weight = 0.9f, reach = 0.5f))))

        /** Even mesh over a faint, sparser back layer. */
        public val uniformWithGhost: NetworkGraphSpec = morphVariant(ribbonGhost.copy(shapes = listOf(Uniform())), morphBase)

        /** A hard-edged disc in the middle of the surface. */
        public val centreDisc: NetworkGraphSpec =
            morphVariant(morphBloom.copy(shapes = listOf(Bloom(x = 0.5f, y = 0.5f, reach = 0.28f, plateau = 0.6f))))

        /** Blooms in the top-end and bottom-start corners at once. */
        public val cornerPair: NetworkGraphSpec =
            morphVariant(morphBloom.copy(shapes = listOf(Bloom(reach = 0.55f), Bloom(x = 0f, y = 1f, reach = 0.55f))))

        /** Two thin bands, one high, one low. */
        public val doubleRibbon: NetworkGraphSpec = morphVariant(
            morphRibbon.copy(
                shapes = listOf(
                    Ribbon(center = 0.25f, width = 0.18f, curve = 0.06f, tilt = 6f),
                    Ribbon(center = 0.75f, width = 0.18f, curve = 0.06f, tilt = -6f),
                ),
            ),
        )

        public val all: List<NetworkGraphSpec> = listOf(
            uniformField,
            ribbon,
            highNarrowRibbon,
            cornerBloom,
            bottomStartBloom,
            ribbonAndBloom,
            uniformWithGhost,
            centreDisc,
            cornerPair,
            doubleRibbon,
        )
    }

    /** A ready-made back layer: [ribbonGhost]'s far-mesh look, over the whole surface. */
    public val defaultBackLayer: NetworkGraphLayerSpec = ribbonGhost.copy(shapes = listOf(Uniform()))
}
