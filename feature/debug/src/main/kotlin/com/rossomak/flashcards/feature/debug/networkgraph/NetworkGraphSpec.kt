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
     * Where nodes sit, as three shape weights from 0 to 1, combined by taking the strongest weighted
     * shape at each point: an even field, a meandering band, and a radial bloom. See [NetworkGraphEnvelope].
     */
    val uniformWeight: Float = 1f,
    val ribbonWeight: Float = 0f,
    val bloomWeight: Float = 0f,
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
    /** Ribbon center line, as a fraction of height. */
    val ribbonCenter: Float = 0.6f,
    /** Ribbon thickness, as a fraction of height. */
    val ribbonWidth: Float = 0.45f,
    /** How far the ribbon's center line meanders, as a fraction of height. */
    val ribbonCurve: Float = 0.1f,
    /** Ribbon slope around the surface's center, in degrees; positive runs downhill toward the end side. */
    val ribbonTilt: Float = 0f,
    /** Bloom radius, as a fraction of the surface's longer side. */
    val bloomReach: Float = 0.8f,
    /** Bloom center, as fractions of width and height; the default is the top-end corner. */
    val bloomX: Float = 1f,
    val bloomY: Float = 0f,
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
    /** Every edge must sit in a drawn triangle: no strings of nodes, no bridges, only faces. */
    val strictTriangles: Boolean = false,
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
    "uniformWeight" to uniformWeight.toSource(),
    "ribbonWeight" to ribbonWeight.toSource(),
    "bloomWeight" to bloomWeight.toSource(),
    "edgeRule" to "EdgeRule.$edgeRule",
    "density" to density.toSource(),
    "fillChance" to fillChance.toSource(),
    "jitter" to jitter.toSource(),
    "driftAmplitude" to driftAmplitude.toSource(),
    "driftSpeed" to driftSpeed.toSource(),
    "ribbonCenter" to ribbonCenter.toSource(),
    "ribbonWidth" to ribbonWidth.toSource(),
    "ribbonCurve" to ribbonCurve.toSource(),
    "ribbonTilt" to ribbonTilt.toSource(),
    "bloomReach" to bloomReach.toSource(),
    "bloomX" to bloomX.toSource(),
    "bloomY" to bloomY.toSource(),
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
    "alpha" to alpha.toSource(),
    "surfaceColor" to (surfaceColor?.let { color -> "Color(0x%08X)".format(Locale.US, color.toArgb()) } ?: "null"),
)

private fun Float.toSource(): String = "%.3f".format(Locale.US, this).trimEnd('0').let { trimmed ->
    if (trimmed.endsWith('.')) "${trimmed}0f" else "${trimmed}f"
}
