@file:Suppress("LoopWithTooManyJumpStatements")

package com.rossomak.flashcards.feature.debug.networkgraph

/**
 * Safety cap on [enforceMinDegree]'s repair rounds. Every pair can be added and removed at most once
 * per call, so the rounds converge on their own; the cap only bounds a bug.
 */
internal const val MAX_ENFORCEMENT_ROUNDS = 8

private const val TRIANGLE_CORNERS = 3

/**
 * The edge set a layer actually draws, after [enforceMinDegree].
 *
 * [alive] is false for every pruned node. [converged] is false only when the repair rounds hit
 * [MAX_ENFORCEMENT_ROUNDS] and the removal-only terminal pass had to finish the job.
 */
internal class MeshTopology(
    val edges: LongArray,
    val alive: BooleanArray,
    val repairedEdgeCount: Int,
    val prunedNodeCount: Int,
    val converged: Boolean,
)

/**
 * PROTOTYPE — throwaway, see [NetworkGraphPrototypeScreen].
 *
 * Enforces the network-graph invariant: every node that is drawn has at least two edges. A node with
 * one edge reads as a dangling tail, a node with none as a stray dot, and no slider setting is
 * allowed to produce either.
 *
 * Starting from [baseEdges] (what the edge rule and its filters kept), each round:
 * 1. with [strictTriangles], every edge whose ends share no neighbour is closed into a triangle by
 *    adding a third edge from [candidateEdges], or dropped when none fits, so the mesh has no strings
 *    or bridges, only faces;
 * 2. every node short of two edges is repaired from [candidateEdges] no longer than
 *    [maxRepairLength], shortest first but preferring an edge that closes a triangle (with
 *    [strictTriangles], only triangle-closing repairs are allowed);
 * 3. every node still short is pruned, along with its edges.
 *
 * Repair edges bypass the rule's keep chance and minimum-angle filters but not the length cap, so a
 * sparse area keeps its nodes connected while a far outlier disappears instead of growing a long line.
 * A pair removed during the call is never re-added, which is what makes the rounds converge. A final
 * removal-only pass guarantees the invariant even if they did not.
 */
internal fun enforceMinDegree(
    count: Int,
    xs: FloatArray,
    ys: FloatArray,
    baseEdges: LongArray,
    candidateEdges: LongArray,
    maxRepairLength: Float,
    strictTriangles: Boolean,
): MeshTopology {
    val graph = EnforcementGraph(count, xs, ys, candidateEdges, maxRepairLength)
    for (key in baseEdges) graph.addBase(key)
    var converged = false
    var rounds = 0
    while (!converged && rounds < MAX_ENFORCEMENT_ROUNDS) {
        var changed = strictTriangles && graph.closeOrDropOpenEdges()
        changed = graph.repairShortNodes(strictTriangles) || changed
        changed = graph.pruneShortNodes() || changed
        converged = !changed
        rounds++
    }
    do {
        var changed = strictTriangles && graph.dropOpenEdges()
        changed = graph.pruneShortNodes() || changed
    } while (changed)
    return graph.toTopology(converged)
}

/** Every unique edge of [triangles] (vertex index triples), unfiltered. Repair candidates for Delaunay rules. */
internal fun triangleEdgeKeys(triangles: IntArray): LongArray {
    val keys = LongArray(triangles.size)
    for (base in triangles.indices step TRIANGLE_CORNERS) {
        keys[base] = edgeKey(triangles[base], triangles[base + 1])
        keys[base + 1] = edgeKey(triangles[base + 1], triangles[base + 2])
        keys[base + 2] = edgeKey(triangles[base + 2], triangles[base])
    }
    return keys.distinct().toLongArray()
}

/** Each node's [neighbours] nearest other nodes, as unique edges. Repair candidates for [EdgeRule.Proximity]. */
internal fun nearestNeighbourEdgeKeys(count: Int, xs: FloatArray, ys: FloatArray, neighbours: Int): LongArray {
    val keys = HashSet<Long>()
    val bestIndices = IntArray(neighbours)
    val bestDistances = FloatArray(neighbours)
    for (node in 0 until count) {
        var found = 0
        for (other in 0 until count) {
            if (other == node) continue
            val distance = squaredDistance(node, other, xs, ys)
            if (found == neighbours && distance >= bestDistances[found - 1]) continue
            // Insertion into the short sorted list of the best so far.
            var slot = if (found < neighbours) found++ else found - 1
            while (slot > 0 && bestDistances[slot - 1] > distance) {
                bestDistances[slot] = bestDistances[slot - 1]
                bestIndices[slot] = bestIndices[slot - 1]
                slot--
            }
            bestDistances[slot] = distance
            bestIndices[slot] = other
        }
        for (index in 0 until found) keys += edgeKey(node, bestIndices[index])
    }
    return keys.toLongArray()
}

private fun squaredDistance(first: Int, second: Int, xs: FloatArray, ys: FloatArray): Float {
    val deltaX = xs[first] - xs[second]
    val deltaY = ys[first] - ys[second]
    return deltaX * deltaX + deltaY * deltaY
}

/** The mutable graph [enforceMinDegree] works on: adjacency, repair candidates per node, and removed pairs. */
private class EnforcementGraph(
    private val count: Int,
    private val xs: FloatArray,
    private val ys: FloatArray,
    candidateEdges: LongArray,
    maxRepairLength: Float,
) {
    private val neighbours = Array(count) { HashSet<Int>() }
    private val baseKeys = HashSet<Long>()
    private val removedKeys = HashSet<Long>()
    private val alive = BooleanArray(count) { true }

    /** Each node's repair candidates within reach, nearest first. */
    private val candidates: Array<IntArray>
    private val candidateKeys = HashSet<Long>()

    init {
        val maxLengthSquared = maxRepairLength * maxRepairLength
        val lists = Array(count) { ArrayList<Int>() }
        for (key in candidateEdges) {
            val start = edgeStart(key)
            val end = edgeEnd(key)
            if (squaredDistance(start, end, xs, ys) > maxLengthSquared) continue
            if (!candidateKeys.add(key)) continue
            lists[start] += end
            lists[end] += start
        }
        candidates = Array(count) { node ->
            lists[node].sortedBy { other -> squaredDistance(node, other, xs, ys) }.toIntArray()
        }
    }

    fun addBase(key: Long) {
        baseKeys += key
        connect(edgeStart(key), edgeEnd(key))
    }

    /** Strict mode: close each edge without a shared neighbour into a triangle, or drop it. */
    fun closeOrDropOpenEdges(): Boolean {
        var changed = false
        for (key in currentKeys()) {
            val start = edgeStart(key)
            val end = edgeEnd(key)
            if (!isConnected(start, end) || sharesNeighbour(start, end)) continue
            changed = true
            if (!closeWithOneEdge(start, end) && !closeWithOneEdge(end, start)) disconnect(start, end)
        }
        return changed
    }

    /** Strict mode's removal-only half, for the terminal pass. */
    fun dropOpenEdges(): Boolean {
        var changed = false
        for (key in currentKeys()) {
            val start = edgeStart(key)
            val end = edgeEnd(key)
            if (!isConnected(start, end) || sharesNeighbour(start, end)) continue
            disconnect(start, end)
            changed = true
        }
        return changed
    }

    fun repairShortNodes(strictTriangles: Boolean): Boolean {
        var changed = false
        for (node in 0 until count) {
            if (!alive[node]) continue
            while (neighbours[node].size < 2) {
                val repaired = if (strictTriangles) repairWithTriangle(node) else repairWithNearest(node)
                if (!repaired) break
                changed = true
            }
        }
        return changed
    }

    fun pruneShortNodes(): Boolean {
        var changed = false
        for (node in 0 until count) {
            if (!alive[node] || neighbours[node].size >= 2) continue
            alive[node] = false
            for (other in neighbours[node].toList()) disconnect(node, other)
            changed = true
        }
        return changed
    }

    fun toTopology(converged: Boolean): MeshTopology {
        val edges = currentKeys().sortedArray()
        return MeshTopology(
            edges = edges,
            alive = alive,
            repairedEdgeCount = edges.count { it !in baseKeys },
            prunedNodeCount = alive.count { !it },
            converged = converged,
        )
    }

    /** Adds `apex–far`, where `apex` already neighbours `near`, so `near–far–apex` becomes a triangle. */
    private fun closeWithOneEdge(near: Int, far: Int): Boolean {
        for (apex in candidates[far]) {
            if (apex != near && isConnected(near, apex) && canAdd(far, apex)) {
                connect(far, apex)
                return true
            }
        }
        return false
    }

    /** Shortest candidate, preferring one that closes a triangle with an edge the node already has. */
    private fun repairWithNearest(node: Int): Boolean {
        var fallback = -1
        for (other in candidates[node]) {
            if (!canAdd(node, other)) continue
            if (sharesNeighbour(node, other)) {
                connect(node, other)
                return true
            }
            if (fallback < 0) fallback = other
        }
        if (fallback < 0) return false
        connect(node, fallback)
        return true
    }

    /** Only repairs that leave the node in a triangle, cheapest first: one new edge, then two, then three. */
    private fun repairWithTriangle(node: Int): Boolean = when {
        repairToNeighbourOfNeighbour(node) -> true
        neighbours[node].isNotEmpty() -> repairAroundAnchor(node, anchor = neighbours[node].first())
        else -> repairWithNewTriangle(node)
    }

    /** One new edge, to a node that already neighbours one of this node's neighbours. */
    private fun repairToNeighbourOfNeighbour(node: Int): Boolean {
        val other = candidates[node].firstOrNull { canAdd(node, it) && sharesNeighbour(node, it) } ?: return false
        connect(node, other)
        return true
    }

    /** Two new edges, `node–other` and `anchor–other`, closing a triangle on the node's one existing edge. */
    private fun repairAroundAnchor(node: Int, anchor: Int): Boolean {
        val other = candidates[node].firstOrNull { it != anchor && canAdd(node, it) && canAdd(anchor, it) } ?: return false
        connect(node, other)
        connect(anchor, other)
        return true
    }

    /** Edges to two candidates that are, or can become, connected to each other. */
    private fun repairWithNewTriangle(node: Int): Boolean {
        val reachable = candidates[node].filter { canAdd(node, it) }
        for (first in reachable) {
            val second = reachable.firstOrNull { it != first && (isConnected(first, it) || canAdd(first, it)) } ?: continue
            connect(node, first)
            connect(node, second)
            if (!isConnected(first, second)) connect(first, second)
            return true
        }
        return false
    }

    private fun canAdd(first: Int, second: Int): Boolean {
        if (!alive[first] || !alive[second] || isConnected(first, second)) return false
        val key = edgeKey(first, second)
        return key in candidateKeys && key !in removedKeys
    }

    private fun sharesNeighbour(first: Int, second: Int): Boolean {
        val (smaller, larger) = if (neighbours[first].size <= neighbours[second].size) first to second else second to first
        return neighbours[smaller].any { it != larger && it in neighbours[larger] }
    }

    private fun isConnected(first: Int, second: Int): Boolean = second in neighbours[first]

    private fun connect(first: Int, second: Int) {
        neighbours[first] += second
        neighbours[second] += first
    }

    private fun disconnect(first: Int, second: Int) {
        neighbours[first] -= second
        neighbours[second] -= first
        removedKeys += edgeKey(first, second)
    }

    private fun currentKeys(): LongArray {
        val keys = ArrayList<Long>()
        for (node in 0 until count) {
            for (other in neighbours[node]) if (node < other) keys += edgeKey(node, other)
        }
        return keys.toLongArray()
    }
}
