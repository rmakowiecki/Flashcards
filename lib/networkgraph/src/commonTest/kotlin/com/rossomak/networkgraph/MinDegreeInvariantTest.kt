package com.rossomak.flashcards.feature.debug.networkgraph

import com.rossomak.flashcards.feature.debug.networkgraph.ShapePrimitive.Bloom
import com.rossomak.flashcards.feature.debug.networkgraph.ShapePrimitive.Ribbon
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import kotlin.math.max
import org.junit.Test

/**
 * Sweeps real [LayerField]s over seeds, both surface shapes and every slider extreme, and checks the
 * network-graph invariants: every drawn node has at least two visible edges, its dot is at least as
 * opaque as every edge it anchors, and, under [EdgeRule.FixedTriangulation], the mesh and the graph as
 * drawn have no graph smaller than [MIN_GRAPH_NODES] and none hanging together by a single node or edge.
 */
class MinDegreeInvariantTest {

    private val seeds = listOf(1, 42, 9_001)
    private val surfaces = listOf(WIDE_STRIP_WIDTH to WIDE_STRIP_HEIGHT, TALL_HERO_WIDTH to TALL_HERO_HEIGHT)
    private val frameTimesSeconds = listOf(0f, 0.05f, 3.7f, 3.75f, 11.2f)

    private val shapes: List<Pair<String, (NetworkGraphLayerSpec) -> NetworkGraphLayerSpec>> = listOf(
        "uniform" to { it },
        "ribbon" to { it.copy(shapes = listOf(Ribbon())) },
        "bloom" to { it.copy(shapes = listOf(Bloom())) },
        "ribbon + bloom, tilted" to { it.copy(shapes = listOf(Ribbon(weight = 0.7f, tilt = 20f), Bloom(x = 0.2f))) },
        "two ribbons + centre disc" to {
            it.copy(shapes = listOf(Ribbon(center = 0.2f, width = 0.15f), Ribbon(center = 0.8f, width = 0.15f), Bloom(x = 0.5f, y = 0.5f, reach = 0.3f, plateau = 0.8f)))
        },
    )

    private val extremes: List<Pair<String, (NetworkGraphLayerSpec) -> NetworkGraphLayerSpec>> = listOf(
        "defaults" to { it },
        "sparse" to { it.copy(density = 4f, fillChance = 0.3f) },
        "dense" to { it.copy(density = 40f) },
        "low keep chance" to { it.copy(edgeKeepChance = 0.2f) },
        "short max edge" to { it.copy(maxEdgeFactor = 0.8f, repairReach = 1f) },
        "steep min angle" to { it.copy(minAngleDegrees = 40f) },
        "outliers" to { it.copy(envelopeFloor = 0.3f, fillChance = 0.3f) },
        "faint long edges" to { it.copy(lengthFalloff = 1f, edgeAlpha = 0.05f) },
        "deep fog" to { it.copy(depthDimming = 1f, alpha = 0.1f) },
        "wild drift" to { it.copy(driftAmplitude = 1.2f, driftSpeed = 4f, jitter = 0.5f) },
        "pulse, hot nodes, edges brighter than dots" to { it.copy(pulseDepth = 1f, hotNodeShare = 0.3f, edgeAlpha = 1f) },
        "everything low" to {
            it.copy(density = 4f, fillChance = 0.3f, edgeKeepChance = 0.2f, maxEdgeFactor = 0.8f, minAngleDegrees = 40f, repairReach = 1f)
        },
    )

    @Test
    fun `every drawn node has two visible edges, outshines them and joins a solid graph across slider extremes`() {
        val baseLayers = shapes.flatMap { (shapeName, shape) ->
            EdgeRule.entries.flatMap { edgeRule ->
                listOf(false, true).map { strictTriangles ->
                    shapeName to shape(NetworkGraphLayerSpec(edgeRule = edgeRule, strictTriangles = strictTriangles))
                }
            }
        }

        val violations = baseLayers.flatMap { (shapeName, base) ->
            extremes.flatMap { (extremeName, extreme) ->
                layerViolations("$shapeName/${base.edgeRule}/$extremeName/strict=${base.strictTriangles}", extreme(base))
            }
        }

        violations.shouldBeEmpty()
    }

    @Test
    fun `every preset layer keeps the invariant and keeps nodes on screen`() {
        val violations = mutableListOf<String>()
        for (preset in NetworkGraphPresets.all) {
            preset.layers.forEachIndexed { index, layer ->
                violations += layerViolations("${preset.name}/layer $index", layer)
                for (strictTriangles in listOf(false, true)) {
                    val field = LayerField(layer.copy(strictTriangles = strictTriangles), preset.seed, TALL_HERO_HEIGHT, PX_PER_DP)
                    field.advance(0f, TALL_HERO_WIDTH, TALL_HERO_HEIGHT)
                    field.visibleNodeCount shouldBeGreaterThan 0
                }
            }
        }

        violations.shouldBeEmpty()
    }

    private fun layerViolations(label: String, layer: NetworkGraphLayerSpec): List<String> {
        val violations = mutableListOf<String>()
        for (seed in seeds) {
            for ((width, height) in surfaces) {
                val field = LayerField(layer, seed, max(width, height), PX_PER_DP)
                for (timeSeconds in frameTimesSeconds) {
                    field.advance(timeSeconds, width, height)
                    val where = "$label seed=$seed ${width.toInt()}x${height.toInt()} t=$timeSeconds"
                    violations += frameViolations(where, field, layer.strictTriangles, timeSeconds)
                    violations += connectivityViolations(where, field, layer.edgeRule)
                }
            }
        }
        return violations
    }

    private companion object {
        const val PX_PER_DP = 2.75f
        const val WIDE_STRIP_WIDTH = 1080f
        const val WIDE_STRIP_HEIGHT = 420f
        const val TALL_HERO_WIDTH = 1080f
        const val TALL_HERO_HEIGHT = 1900f
    }
}

/**
 * Every network-graph invariant violation in [field]'s last frame, labelled with [where]: topology
 * (live nodes have two edges, pruned ones none, strict meshes have no open edges), visibility (a drawn
 * dot has two visible edges) and the alpha rule (no drawn edge outshines either of its dots).
 */
internal fun frameViolations(where: String, field: LayerField, strictTriangles: Boolean, timeSeconds: Float): List<String> =
    topologyViolations(where, field.topology, field.nodeCount, strictTriangles) +
        visualViolations(where, field) +
        alphaRuleViolations(where, field, timeSeconds)

private fun topologyViolations(where: String, topology: MeshTopology, nodeCount: Int, strictTriangles: Boolean): List<String> {
    val violations = mutableListOf<String>()
    if (!topology.converged) violations += "$where: repair rounds did not converge"
    val neighbours = Array(nodeCount) { HashSet<Int>() }
    for (key in topology.edges) {
        neighbours[edgeStart(key)] += edgeEnd(key)
        neighbours[edgeEnd(key)] += edgeStart(key)
    }
    for (node in 0 until nodeCount) {
        val degree = neighbours[node].size
        if (topology.alive[node] && degree < 2) violations += "$where: live node $node has degree $degree"
        if (!topology.alive[node] && degree > 0) violations += "$where: pruned node $node still has $degree edges"
    }
    if (strictTriangles) {
        for (key in topology.edges) {
            val start = edgeStart(key)
            val end = edgeEnd(key)
            if (neighbours[start].none { it != end && it in neighbours[end] }) violations += "$where: edge $start-$end is in no triangle"
        }
    }
    return violations
}

private fun visualViolations(where: String, field: LayerField): List<String> {
    val violations = mutableListOf<String>()
    for (node in 0 until field.nodeCount) {
        val alpha = field.nodeAlpha(node)
        if (alpha > 0f && field.secondEdgeAlpha(node) < EDGE_VISIBLE_ALPHA) {
            violations += "$where: node $node drawn at $alpha with second edge at ${field.secondEdgeAlpha(node)}"
        }
        if (alpha >= NODE_VISIBLE_ALPHA && field.visibleEdgeCount(node) < 2) {
            violations += "$where: node $node visible with ${field.visibleEdgeCount(node)} visible edges"
        }
    }
    return violations
}

private fun alphaRuleViolations(where: String, field: LayerField, timeSeconds: Float): List<String> {
    val violations = mutableListOf<String>()
    for (edge in 0 until field.drawnEdgeCount) {
        val key = field.drawnEdgeKey(edge)
        val edgeAlpha = field.drawnEdgeAlpha(edge, timeSeconds)
        for (node in listOf(edgeStart(key), edgeEnd(key))) {
            if (edgeAlpha > field.nodeAlpha(node)) {
                violations += "$where: node $node at ${field.nodeAlpha(node)} anchors an edge drawn at $edgeAlpha"
            }
        }
    }
    return violations
}

/**
 * Breaks of the connectivity rules in [field]'s mesh (any cut node, or graph smaller than
 * [MIN_GRAPH_NODES]) and the same in the graph as drawn, which only a mesh edge drawn too faint to see
 * can break. Meshes rebuilt every frame skip the connectivity rules (see LayerField's settleTopology),
 * so under any [edgeRule] but [EdgeRule.FixedTriangulation] there are none to break.
 */
private fun connectivityViolations(where: String, field: LayerField, edgeRule: EdgeRule): List<String> =
    if (edgeRule != EdgeRule.FixedTriangulation) {
        emptyList()
    } else {
        connectivityMessages("$where: mesh", connectivityViolations(field.nodeCount, field.topology.edges)) +
            connectivityMessages("$where: drawn", field.drawnConnectivityViolations())
    }

private fun connectivityMessages(prefix: String, violations: ConnectivityViolations): List<String> = listOfNotNull(
    "$prefix graphs too small ${violations.smallGraphNodes}".takeIf { violations.smallGraphNodes.isNotEmpty() },
    "$prefix cut nodes ${violations.cutNodes}".takeIf { violations.cutNodes.isNotEmpty() },
)
