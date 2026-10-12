package com.rossomak.flashcards.feature.debug.networkgraph

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.Locale

/**
 * PROTOTYPE — throwaway, see [NetworkGraphPrototypeScreen].
 *
 * Everything that makes one network-graph background look different from another. A variant is a
 * value of this class, not a renderer of its own: [NetworkGraphPresets] holds the curated ones, the
 * tuning sheet edits a copy live, and [toKotlinSource] turns a tuned copy back into a preset.
 *
 * [layers] draw back to front, each an independent mesh with its own nodes, envelope and seed, which
 * is what gives the "sharp foreground over a faint, larger background mesh" depth of the references.
 * Layer seeds count from the front layer, so adding a back layer leaves the front one's layout alone.
 *
 * Two specs morph into each other smoothly (see [morphLayers]) when they share [seed] and each
 * layer's morph family: [NetworkGraphLayerSpec.density], [NetworkGraphLayerSpec.jitter],
 * [NetworkGraphLayerSpec.edgeRule] and [NetworkGraphLayerSpec.strictTriangles]. Every other field
 * morphs.
 */
data class NetworkGraphSpec(
    val name: String,
    val note: String,
    val seed: Int,
    val layers: List<NetworkGraphLayerSpec>,
)

/** Which pairs of nodes get an edge. */
enum class EdgeRule {
    /** Every pair within reach, re-evaluated each frame — the original look. */
    Proximity,

    /** Delaunay over the nodes' rest positions, triangulated once and kept while they drift. */
    FixedTriangulation,

    /** Delaunay re-triangulated every frame; edges that flip in or out fade rather than pop. */
    LivingDelaunay,
}

/** A layer holds at most this many [ShapePrimitive]s; each one costs every node a density and closeness check per frame. */
const val MAX_SHAPE_PRIMITIVES = 6

private const val RIBBON_DEFAULT_CENTER = 0.6f
private const val RIBBON_DEFAULT_WIDTH = 0.45f
private const val RIBBON_DEFAULT_CURVE = 0.1f
private const val BLOOM_DEFAULT_REACH = 0.8f

/**
 * One part of a layer's shape: a density field over the surface, from 0 to 1, scaled by [weight]. A
 * layer combines its primitives by taking the strongest at each point, so any mix of them is a union.
 * Two layers' primitives never need to match up for a morph: each node takes its presence from the two
 * ends, and comets travel between primitives that moved (see [LayerField]).
 */
sealed interface ShapePrimitive {
    val weight: Float

    /** Even density over the whole surface. */
    data class Uniform(override val weight: Float = 1f) : ShapePrimitive

    /** A meandering band across the width. */
    data class Ribbon(
        override val weight: Float = 1f,
        /** Center line, as a fraction of height. */
        val center: Float = RIBBON_DEFAULT_CENTER,
        /** Thickness, as a fraction of height. */
        val width: Float = RIBBON_DEFAULT_WIDTH,
        /** How far the center line meanders, as a fraction of height. */
        val curve: Float = RIBBON_DEFAULT_CURVE,
        /** Slope around the surface's center, in degrees; positive runs downhill toward the end side. */
        val tilt: Float = 0f,
    ) : ShapePrimitive

    /** Dense around a center, thinning out; the default sits on the top-end corner. */
    data class Bloom(
        override val weight: Float = 1f,
        /** Center, as fractions of width and height. */
        val x: Float = 1f,
        val y: Float = 0f,
        /** Radius, as a fraction of the surface's longer side. */
        val reach: Float = BLOOM_DEFAULT_REACH,
        /** Share of [reach] held at full density before the falloff starts: 0 = a soft bloom, near 1 = a hard disc. */
        val plateau: Float = 0f,
    ) : ShapePrimitive
}

/**
 * One mesh layer. Geometry is in grid cells (a cell is the window's longer side over [density]), so
 * the mesh is equally fine on every surface of a screen and never reshuffles when one resizes; stroke,
 * node and glow sizes are in dp, so they read the same on a 64dp app bar and a full-screen hero.
 * Fractions marked "of height"/"of width" are of the surface.
 *
 * Invariants, whatever the values:
 * - every drawn node has at least two visible edges. The edge filters below shape the mesh, then
 *   [enforceMinDegree] repairs short nodes or prunes them, and a node's dot dims with its
 *   second-brightest edge, so neither a dangling tail nor a stray dot can appear;
 * - a node's dot is at least as opaque as every edge it anchors, and its pixels are exactly its own
 *   alpha: edges are limited by the dimmer of their two dots, and dots replace rather than blend with
 *   what the layer drew under them.
 */
data class NetworkGraphLayerSpec(
    /**
     * Where nodes sit: up to [MAX_SHAPE_PRIMITIVES] primitives, combined by taking the strongest weighted
     * one at each point. See [ShapePrimitive] and [NetworkGraphEnvelope].
     */
    val shapes: List<ShapePrimitive> = listOf(ShapePrimitive.Uniform()),
    val edgeRule: EdgeRule = EdgeRule.FixedTriangulation,
    /** Grid cells along the window's longer side; the node-count knob. */
    val density: Float = 20f,
    /** Chance a cell inside the envelope holds a node at all. */
    val fillChance: Float = 0.9f,
    /** How far a node's rest position sits from its cell's center, in cells. */
    val jitter: Float = 0.45f,
    /** How far a node wanders from its rest position, in cells. Kept under 1 to avoid clumping. */
    val driftAmplitude: Float = 0.4f,
    val driftSpeed: Float = 1f,
    /** Node density outside the envelope, so the shape has stray outliers instead of a hard border. */
    val envelopeFloor: Float = 0.03f,
    /** Travelling vertical wave applied to every rest position, as a fraction of height. 0 = static. */
    val waveAmplitude: Float = 0f,
    val waveSpeed: Float = 0.3f,
    /** Longest edge kept, in cells. For [EdgeRule.Proximity] this is the reach itself. */
    val maxEdgeFactor: Float = 2.2f,
    /** Triangles with a smaller corner than this are slivers and contribute no edges. */
    val minAngleDegrees: Float = 12f,
    /** Chance each edge survives, so the mesh is not a perfect lattice. Stable per pair. */
    val edgeKeepChance: Float = 0.9f,
    /** Longest edge a node short of two edges may be repaired with, in multiples of [maxEdgeFactor]. */
    val repairReach: Float = 1.6f,
    /**
     * Every face of the mesh is a triangle: no strings of nodes, no bridges, no polygon holes left by
     * dropped diagonals, and no diagonal drawn fainter than its triangle's other sides.
     */
    val strictTriangles: Boolean = true,
    val strokeWidthDp: Float = 0.8f,
    val edgeAlpha: Float = 0.55f,
    /** How much dimmer an edge at max length is than a very short one. */
    val lengthFalloff: Float = 0.5f,
    /** How deep edges pulse: 0 = steady, 1 = down to the pulse floor. */
    val pulseDepth: Float = 0f,
    val nodeSizeDp: Float = 2.2f,
    /** Spread of node radii around [nodeSizeDp], 0 = all equal. */
    val nodeSizeVariance: Float = 0.3f,
    /** Share of nodes that are "hot": larger, fully opaque, and haloed. Nodes heat up and cool down gradually as it changes. */
    val hotNodeShare: Float = 0f,
    val glowRadiusDp: Float = 14f,
    val glowStrength: Float = 0.35f,
    /** Per-node depth inside this one mesh: far nodes and their edges dim and shrink. 0 = off. */
    val depthDimming: Float = 0f,
    /**
     * Scales the comets a morph sends between this layer's moving shapes, averaged over the morph's two
     * ends: 1 lights a patch about the size of the shapes it travels between, under about 0.5 almost
     * nothing, 0 none at all. See [Comet].
     */
    val cometStrength: Float = 1f,
    /** Comet nodes heat up as the comet reaches them and cool down once it has passed. */
    val cometSpark: Boolean = false,
    /** Multiplies every alpha in the layer. */
    val alpha: Float = 1f,
    /** Line/node color on a plain surface; null = theme `onSurfaceVariant`. Ignored on the gradient, which is always white. */
    val surfaceColor: Color? = null,
)

/** The spec as Kotlin source for [NetworkGraphPresets], listing only layer fields that differ from the defaults. */
fun NetworkGraphSpec.toKotlinSource(): String {
    val defaults = NetworkGraphLayerSpec().sourceArguments()
    val layerSources = layers.joinToString(separator = ",\n") { layer ->
        val arguments = layer.sourceArguments().filter { (name, value) -> defaults[name] != value }
        if (arguments.isEmpty()) {
            "        NetworkGraphLayerSpec()"
        } else {
            arguments.entries.joinToString(
                separator = ",\n",
                prefix = "        NetworkGraphLayerSpec(\n",
                postfix = ",\n        )",
            ) { (name, value) -> "            $name = $value" }
        }
    }
    return """
        |NetworkGraphSpec(
        |    name = "$name",
        |    note = "$note",
        |    seed = $seed,
        |    layers = listOf(
        |$layerSources,
        |    ),
        |)
    """.trimMargin()
}

private fun NetworkGraphLayerSpec.sourceArguments(): Map<String, String> = linkedMapOf(
    "shapes" to shapes.joinToString(separator = ", ", prefix = "listOf(", postfix = ")") { it.toSource() },
    "edgeRule" to "EdgeRule.$edgeRule",
    "density" to density.toSource(),
    "fillChance" to fillChance.toSource(),
    "jitter" to jitter.toSource(),
    "driftAmplitude" to driftAmplitude.toSource(),
    "driftSpeed" to driftSpeed.toSource(),
    "envelopeFloor" to envelopeFloor.toSource(),
    "waveAmplitude" to waveAmplitude.toSource(),
    "waveSpeed" to waveSpeed.toSource(),
    "maxEdgeFactor" to maxEdgeFactor.toSource(),
    "minAngleDegrees" to minAngleDegrees.toSource(),
    "edgeKeepChance" to edgeKeepChance.toSource(),
    "repairReach" to repairReach.toSource(),
    "strictTriangles" to strictTriangles.toString(),
    "strokeWidthDp" to strokeWidthDp.toSource(),
    "edgeAlpha" to edgeAlpha.toSource(),
    "lengthFalloff" to lengthFalloff.toSource(),
    "pulseDepth" to pulseDepth.toSource(),
    "nodeSizeDp" to nodeSizeDp.toSource(),
    "nodeSizeVariance" to nodeSizeVariance.toSource(),
    "hotNodeShare" to hotNodeShare.toSource(),
    "glowRadiusDp" to glowRadiusDp.toSource(),
    "glowStrength" to glowStrength.toSource(),
    "depthDimming" to depthDimming.toSource(),
    "cometStrength" to cometStrength.toSource(),
    "cometSpark" to cometSpark.toString(),
    "alpha" to alpha.toSource(),
    "surfaceColor" to (surfaceColor?.let { color -> "Color(0x%08X)".format(Locale.US, color.toArgb()) } ?: "null"),
)

/** The primitive as a constructor call, listing only the arguments that differ from its defaults. */
private fun ShapePrimitive.toSource(): String {
    val arguments = when (this) {
        is ShapePrimitive.Uniform -> listOf("weight" to (weight to 1f))
        is ShapePrimitive.Ribbon -> listOf(
            "weight" to (weight to 1f),
            "center" to (center to RIBBON_DEFAULT_CENTER),
            "width" to (width to RIBBON_DEFAULT_WIDTH),
            "curve" to (curve to RIBBON_DEFAULT_CURVE),
            "tilt" to (tilt to 0f),
        )
        is ShapePrimitive.Bloom -> listOf(
            "weight" to (weight to 1f),
            "x" to (x to 1f),
            "y" to (y to 0f),
            "reach" to (reach to BLOOM_DEFAULT_REACH),
            "plateau" to (plateau to 0f),
        )
    }
    val changed = arguments.filter { (_, values) -> values.first != values.second }
    return changed.joinToString(separator = ", ", prefix = "ShapePrimitive.${this::class.simpleName}(", postfix = ")") { (name, values) ->
        "$name = ${values.first.toSource()}"
    }
}

private fun Float.toSource(): String = "%.3f".format(Locale.US, this).trimEnd('0').let { trimmed ->
    if (trimmed.endsWith('.')) "${trimmed}0f" else "${trimmed}f"
}
