@file:Suppress("LoopWithTooManyJumpStatements", "TooManyFunctions")

package com.rossomak.flashcards.feature.debug.networkgraph

import kotlin.math.max
import kotlin.math.min

/**
 * Safety cap on [enforceMinDegree]'s repair rounds. Every pair can be added and removed at most once
 * per call, so the rounds converge on their own; the cap only bounds a bug.
 */
internal const val MAX_ENFORCEMENT_ROUNDS = 8

private const val TRIANGLE_CORNERS = 3

private val EMPTY_NEIGHBOURS = IntArray(0)

/**
 * The fewest nodes a separate graph may have: a smaller one reads as a stray scrap next to the mesh.
 * Also the fewest nodes a piece hanging from a cut node keeps when [enforceMinDegree] cuts it loose
 * instead of pruning it.
 */
internal const val MIN_GRAPH_NODES = 6

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
 * 3. every node still short is pruned, along with its edges;
 * 4. once those settle, and with [keepConnectivity], every cut node (one whose removal splits its graph, as at either end of a
 *    bridge) gets an edge between two of its neighbours on either side, closing a triangle across it,
 *    or without [strictTriangles] any candidate edge across; a piece that still hangs from it is
 *    pruned when smaller than [MIN_GRAPH_NODES] and cut loose otherwise. Every separate graph smaller
 *    than [MIN_GRAPH_NODES] is pruned.
 *
 * With [strictTriangles] and [triangles] (the Delaunay triangles the candidates come from), every
 * triangle with all three corners kept and two of its edges also gets its third, so no face of the mesh
 * is a polygon: a dropped diagonal comes back unless it is longer than [maxRepairLength].
 *
 * Repair edges bypass the rule's keep chance and minimum-angle filters but not the length cap, so a
 * sparse area keeps its nodes connected while a far outlier disappears instead of growing a long line.
 * A pair removed during the call is never re-added, which is what makes the rounds converge. A final
 * removal-only pass guarantees the invariants even if they did not.
 */
internal fun enforceMinDegree(
    count: Int,
    xs: FloatArray,
    ys: FloatArray,
    baseEdges: LongArray,
    candidateEdges: LongArray,
    maxRepairLength: Float,
    strictTriangles: Boolean,
    triangles: IntArray? = null,
    keepConnectivity: Boolean = true,
): MeshTopology {
    val graph = EnforcementGraph(count, xs, ys, candidateEdges, maxRepairLength)
    for (key in baseEdges) graph.addBase(key)
    var converged = false
    var rounds = 0
    while (!converged && rounds < MAX_ENFORCEMENT_ROUNDS) {
        converged = !graph.repairRound(strictTriangles, triangles, keepConnectivity)
        rounds++
    }
    while (graph.removalRound(strictTriangles, keepConnectivity)) Unit
    return graph.toTopology(converged)
}

/** What breaks [enforceMinDegree]'s connectivity rules in a graph: nodes of graphs under [MIN_GRAPH_NODES], and cut nodes. */
internal class ConnectivityViolations(val smallGraphNodes: List<Int>, val cutNodes: List<Int>)

/** [ConnectivityViolations] of the graph of [edges] over [count] nodes. */
internal fun connectivityViolations(count: Int, edges: LongArray): ConnectivityViolations {
    val graph = EnforcementGraph(count, FloatArray(count), FloatArray(count), candidateEdges = LongArray(0), maxRepairLength = 0f)
    for (key in edges) graph.addBase(key)
    return ConnectivityViolations(smallGraphNodes = graph.smallGraphNodes(), cutNodes = graph.cutNodes().asList())
}

/** Every triangle of the graph of [edges] over [count] nodes, as node index triples, each once. */
internal fun meshTriangles(count: Int, edges: LongArray): IntArray {
    val neighbours = Array(count) { HashSet<Int>() }
    for (key in edges) {
        neighbours[edgeStart(key)] += edgeEnd(key)
        neighbours[edgeEnd(key)] += edgeStart(key)
    }
    val triangles = ArrayList<Int>()
    for (key in edges) {
        val low = min(edgeStart(key), edgeEnd(key))
        val high = max(edgeStart(key), edgeEnd(key))
        // From the edge between each triangle's two lowest corners only.
        for (apex in neighbours[low]) {
            if (apex <= high || apex !in neighbours[high]) continue
            triangles += low
            triangles += high
            triangles += apex
        }
    }
    return triangles.toIntArray()
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

    /** One repair round of [enforceMinDegree]; true if it changed anything. */
    fun repairRound(strictTriangles: Boolean, triangles: IntArray?, keepConnectivity: Boolean): Boolean {
        var changed = strictTriangles && closeOrDropOpenEdges()
        if (strictTriangles && triangles != null) changed = closeFaces(triangles) || changed
        changed = repairShortNodes(strictTriangles) || changed
        changed = pruneShortNodes() || changed
        // Connectivity only once the degrees settle: its search costs more, and repairs move cut nodes.
        if (!changed && keepConnectivity) changed = fixCutNodes(allowAdding = true, strictTriangles = strictTriangles) || pruneSmallGraphs()
        return changed
    }

    /** One round of [enforceMinDegree]'s removal-only terminal pass; true if it removed anything. */
    fun removalRound(strictTriangles: Boolean, keepConnectivity: Boolean): Boolean {
        var changed = strictTriangles && dropOpenEdges()
        changed = pruneShortNodes() || changed
        if (!changed && keepConnectivity) changed = fixCutNodes(allowAdding = false, strictTriangles = strictTriangles) || pruneSmallGraphs()
        return changed
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
            prune(node)
            changed = true
        }
        return changed
    }

    /** Strict mode: gives each triangle of [triangles] with all corners alive and two of its edges the third. */
    fun closeFaces(triangles: IntArray): Boolean {
        var changed = false
        // Closing one face can give a neighbouring face its second edge, so repeat until none closes.
        while (closeFacesOnce(triangles)) changed = true
        return changed
    }

    private fun closeFacesOnce(triangles: IntArray): Boolean {
        var changed = false
        for (base in triangles.indices step TRIANGLE_CORNERS) {
            val first = triangles[base]
            val second = triangles[base + 1]
            val third = triangles[base + 2]
            if (!alive[first] || !alive[second] || !alive[third]) continue
            val missing = when {
                !isConnected(first, second) -> if (isConnected(second, third) && isConnected(third, first)) first to second else null
                !isConnected(second, third) -> if (isConnected(third, first)) second to third else null
                !isConnected(third, first) -> third to first
                else -> null
            } ?: continue
            if (!canAdd(missing.first, missing.second)) continue
            connect(missing.first, missing.second)
            changed = true
        }
        return changed
    }

    /**
     * Fixes every cut node: joins each piece hanging from it to another piece with one new edge if
     * [allowAdding] and one fits (see [enforceMinDegree]), else prunes the piece if it is smaller than
     * [MIN_GRAPH_NODES] or cuts it loose from the cut node. True if anything changed.
     */
    fun fixCutNodes(allowAdding: Boolean, strictTriangles: Boolean): Boolean {
        var changed = false
        for (cut in cutNodes()) {
            if (!alive[cut]) continue
            val pieces = piecesAround(cut)
            if (pieces.size < 2) continue
            val pieceOf = IntArray(count) { -1 }
            pieces.forEachIndexed { index, piece -> for (node in piece) pieceOf[node] = index }
            val main = pieces.indices.maxBy { pieces[it].size }
            for (index in pieces.indices) {
                if (index == main) continue
                val joined = allowAdding && (joinAcross(cut, pieces[index], pieceOf, index) || !strictTriangles && joinAnywhere(cut, pieces[index], pieceOf, index))
                if (!joined) cutOff(cut, pieces[index])
                changed = true
            }
        }
        return changed
    }

    /** Prunes every separate graph smaller than [MIN_GRAPH_NODES]; true if any. */
    fun pruneSmallGraphs(): Boolean {
        val small = smallGraphNodes()
        for (node in small) prune(node)
        return small.isNotEmpty()
    }

    /** Every node of a separate graph smaller than [MIN_GRAPH_NODES]. */
    fun smallGraphNodes(): List<Int> {
        val seen = BooleanArray(count)
        val small = ArrayList<Int>()
        for (start in 0 until count) {
            if (seen[start] || !alive[start] || neighbours[start].isEmpty()) continue
            val graph = collectFrom(start, blocked = -1, seen = seen)
            if (graph.size < MIN_GRAPH_NODES) small += graph.asList()
        }
        return small
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

    /** The edge between two of [cut]'s neighbours, one in [piece] and one outside it, that closes a triangle across [cut]; shortest first. */
    private fun joinAcross(cut: Int, piece: IntArray, pieceOf: IntArray, pieceIndex: Int): Boolean {
        var best: Pair<Int, Int>? = null
        var bestDistance = Float.MAX_VALUE
        for (near in piece) {
            if (!isConnected(near, cut)) continue
            for (far in candidates[near]) {
                if (pieceOf[far] == pieceIndex || !isConnected(far, cut) || !canAdd(near, far)) continue
                val distance = squaredDistance(near, far, xs, ys)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = near to far
                }
                break
            }
        }
        val (near, far) = best ?: return false
        connect(near, far)
        return true
    }

    /** The shortest candidate edge from [piece] to any node outside it other than [cut]. */
    private fun joinAnywhere(cut: Int, piece: IntArray, pieceOf: IntArray, pieceIndex: Int): Boolean {
        var best: Pair<Int, Int>? = null
        var bestDistance = Float.MAX_VALUE
        for (near in piece) {
            val far = candidates[near].firstOrNull { it != cut && pieceOf[it] != pieceIndex && canAdd(near, it) } ?: continue
            val distance = squaredDistance(near, far, xs, ys)
            if (distance < bestDistance) {
                bestDistance = distance
                best = near to far
            }
        }
        val (near, far) = best ?: return false
        connect(near, far)
        return true
    }

    /** Prunes [piece] if it is too small to stand alone, else removes its edges to [cut]. */
    private fun cutOff(cut: Int, piece: IntArray) {
        if (piece.size < MIN_GRAPH_NODES) {
            for (node in piece) prune(node)
        } else {
            for (node in piece) if (isConnected(node, cut)) disconnect(node, cut)
        }
    }

    /** The groups [cut]'s graph falls into without [cut]: each holds at least one of its neighbours. */
    private fun piecesAround(cut: Int): List<IntArray> {
        val seen = BooleanArray(count)
        return neighbours[cut].toList().mapNotNull { start -> if (seen[start]) null else collectFrom(start, blocked = cut, seen = seen) }
    }

    /** Every node reachable from [start] without passing [blocked], marked in [seen]. */
    private fun collectFrom(start: Int, blocked: Int, seen: BooleanArray): IntArray {
        val found = ArrayList<Int>()
        val stack = ArrayDeque<Int>()
        seen[start] = true
        stack += start
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            found += node
            for (other in neighbours[node]) {
                if (other == blocked || seen[other]) continue
                seen[other] = true
                stack += other
            }
        }
        return found.toIntArray()
    }

    /** Every cut node of the current graph; see [CutSearch]. */
    fun cutNodes(): IntArray = CutSearch(Array(count) { if (neighbours[it].isEmpty()) EMPTY_NEIGHBOURS else neighbours[it].toIntArray() }).cutNodes()

    private fun prune(node: Int) {
        alive[node] = false
        for (other in neighbours[node].toList()) disconnect(node, other)
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

/** Finds the cut nodes of the graph of [adjacency] by an iterative Hopcroft–Tarjan search. */
private class CutSearch(private val adjacency: Array<IntArray>) {
    private val count = adjacency.size
    private val order = IntArray(count) { -1 }
    private val low = IntArray(count)
    private val parent = IntArray(count) { -1 }
    private val nextNeighbour = IntArray(count)
    private val isCut = BooleanArray(count)
    private val stack = IntArray(count)
    private var time = 0

    fun cutNodes(): IntArray {
        for (root in 0 until count) if (order[root] < 0 && adjacency[root].isNotEmpty()) searchFrom(root)
        return (0 until count).filter { isCut[it] }.toIntArray()
    }

    private fun searchFrom(root: Int) {
        var depth = 0
        var rootChildren = 0
        stack[0] = root
        visit(root)
        while (depth >= 0) {
            val node = stack[depth]
            if (nextNeighbour[node] == adjacency[node].size) {
                depth--
                finish(node, root)
                continue
            }
            val other = adjacency[node][nextNeighbour[node]++]
            if (order[other] >= 0) {
                if (other != parent[node]) low[node] = min(low[node], order[other])
                continue
            }
            if (node == root) rootChildren++
            parent[other] = node
            visit(other)
            stack[++depth] = other
        }
        if (rootChildren > 1) isCut[root] = true
    }

    private fun visit(node: Int) {
        order[node] = time
        low[node] = time++
    }

    /** Hands [node]'s low point to its parent, which is a cut node if nothing under [node] reaches above it. */
    private fun finish(node: Int, root: Int) {
        val up = parent[node]
        if (up < 0) return
        low[up] = min(low[up], low[node])
        if (up != root && low[node] >= order[up]) isCut[up] = true
    }
}
