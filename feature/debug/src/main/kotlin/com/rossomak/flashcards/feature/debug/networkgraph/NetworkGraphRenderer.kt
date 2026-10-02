// Every tuning value with a meaning of its own is either a spec field or a named constant below. What
// detekt flags here is the arithmetic of the formulas themselves (the 0.5 of a cell's center, the 2
// that turns 0..1 into -1..1), which naming individually would only bury.
@file:Suppress("MagicNumber", "LoopWithTooManyJumpStatements", "TooManyFunctions", "LongParameterList")

package com.rossomak.flashcards.feature.debug.networkgraph

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val NANOS_PER_MILLI = 1_000_000f

/** Seeds of consecutive layers are this far apart, so two layers of one spec never share a layout. */
private const val LAYER_SEED_STRIDE = 7_919

/** A ring of cells past every edge, so nodes drifting in from off-surface keep the borders populated. */
private const val BORDER_RING = 1

/** A frame gap longer than this (a pause, a page swipe) advances fades by this much only. */
private const val MAX_STEP_SECONDS = 0.1f

// Drift: three sine octaves per axis at non-rational ratios, so a node's path wanders instead of
// tracing a closed Lissajous loop. Carried over unchanged from the single-renderer revision.
private const val DRIFT_FREQUENCY_BASE_PRIMARY = 0.115f
private const val DRIFT_FREQUENCY_BASE_SECONDARY = 0.095f
private const val DRIFT_FREQUENCY_SPREAD = 0.075f
private const val DRIFT_SCALE_FLOOR = 0.35f
private const val DRIFT_OCTAVE_WEIGHT_COARSE = 0.55f
private const val DRIFT_OCTAVE_WEIGHT_MEDIUM = 0.30f
private const val DRIFT_OCTAVE_WEIGHT_FINE = 0.15f
private const val DRIFT_OCTAVE_RATIO_MEDIUM = 1.73f
private const val DRIFT_OCTAVE_RATIO_FINE = 2.61f

private const val EDGE_PULSE_SPEED = 1.6f
private const val EDGE_PULSE_FLOOR = 0.4f
private const val EDGE_MIN_VISIBLE_ALPHA = 0.004f

/**
 * How long an edge of a per-frame rule ([EdgeRule.Proximity], [EdgeRule.LivingDelaunay]) takes to
 * fade fully in or out when it enters or leaves the edge set, so nothing pops.
 */
private const val EDGE_FLIP_FADE_SECONDS = 0.6f

/** How many nearest neighbours [EdgeRule.Proximity] offers [enforceMinDegree] as repair candidates. */
private const val PROXIMITY_REPAIR_NEIGHBOURS = 6

/** An edge drawn fainter than this does not count toward a node's two visible edges. */
internal const val EDGE_VISIBLE_ALPHA = 0.02f

/** A node's dot drawn fainter than this counts as not drawn. */
internal const val NODE_VISIBLE_ALPHA = 0.03f

/**
 * A node's dot is at full alpha once its second-brightest edge reaches this share of the edge alpha
 * nominal at the node's depth, and fades out below it, reaching zero at [EDGE_VISIBLE_ALPHA].
 */
private const val NODE_FULL_AT_EDGE_SHARE = 0.35f

/**
 * Cap on [LayerField]'s dot/edge alpha rounds. Each round only lowers alphas, so they settle on their
 * own; a dot whose edges barely clear [EDGE_VISIBLE_ALPHA] can keep sinking for many rounds, and the
 * removal-only pass after the cap hides it instead.
 */
private const val MAX_ALPHA_ROUNDS = 16

/** An alpha round that lowers no dot by more than this counts as settled. */
private const val ALPHA_SETTLE_EPSILON = 1e-4f

/** How much brighter than an edge a dot may be before "Show violations" doubts the alpha rule held. */
private const val ALPHA_RULE_TOLERANCE = 1e-4f

private val ViolationColor = Color(0xFFFF1744)
private const val VIOLATION_RING_RADIUS_DP = 7f
private const val VIOLATION_RING_STROKE_DP = 1.5f

/** A plain node's dot alpha before layer alpha and depth; raised to the layer's edge alpha when that is higher. */
private const val NODE_ALPHA = 0.85f
private const val HOT_NODE_SCALE = 1.4f
private const val NODE_SIZE_FLOOR = 0.2f

/** How much a far node shrinks at full [NetworkGraphLayerSpec.depthDimming]. */
private const val DEPTH_SIZE_SHRINK = 0.45f

/**
 * PROTOTYPE — throwaway, see [NetworkGraphPrototypeScreen].
 *
 * Draws [spec] as an animated network-graph background: every layer's edges, glows and nodes,
 * back to front. Paints nothing else and is transparent everywhere it doesn't draw, so it
 * composes over whatever `Modifier.background(...)` ran earlier in the chain.
 *
 * Color follows [style]: white on [FlashcardsComponentStyle.OnGradient] (the gradient never flips),
 * and each layer's [NetworkGraphLayerSpec.surfaceColor] — theme `onSurfaceVariant` when unset — on
 * [FlashcardsComponentStyle.OnSurface].
 *
 * The node layout is rebuilt whenever the spec or the surface size changes, which is what lets the
 * tuning sheet edit any field live. Fine for a fixed-size prototype surface; a collapsing app bar
 * would reshuffle every frame of its collapse.
 *
 * Every drawn node has at least two visible edges, and its dot is at least as opaque as each of them —
 * see [NetworkGraphLayerSpec], [enforceMinDegree] and [LayerField.draw]. [showViolations] rings in red
 * any node that breaks either rule anyway, as an on-device check.
 * [layerStats], when given, publishes each layer's visible, pruned and repaired counts.
 *
 * Before promoting a winner out of `:feature:debug`:
 * - respect the system animator-duration scale ("Remove animations") and render a still frame;
 * - stop the frame loop while the surface is off-screen or the app is backgrounded.
 */
@Composable
fun Modifier.networkGraphBackground(
    spec: NetworkGraphSpec,
    style: FlashcardsComponentStyle,
    stats: NetworkGraphFrameStats? = null,
    layerStats: NetworkGraphLayerStats? = null,
    showViolations: Boolean = false,
): Modifier {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val layerColors = spec.layers.map { layer ->
        when (style) {
            FlashcardsComponentStyle.OnGradient -> Color.White
            FlashcardsComponentStyle.OnSurface -> layer.surfaceColor ?: onSurfaceVariant
        }
    }
    val holder = remember { NetworkGraphHolder() }
    val elapsedSeconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { nowNanos ->
                elapsedSeconds.floatValue = (nowNanos - startNanos) / NANOS_PER_SECOND
            }
        }
    }

    return this.drawBehind {
        if (size.width <= 0f || size.height <= 0f) return@drawBehind
        val drawStartNanos = System.nanoTime()
        val timeSeconds = elapsedSeconds.floatValue
        val fields = holder.fieldsFor(spec, size.width, size.height, density)
        // Nodes deliberately sit past the surface's edges, and drawBehind is not clipped.
        clipRect {
            fields.forEachIndexed { index, field ->
                val color = layerColors[index]
                field.advance(timeSeconds)
                field.draw(this, color, holder.halosFor(color), timeSeconds, showViolations)
            }
        }
        stats?.recordDraw(System.nanoTime() - drawStartNanos)
        layerStats?.record(fields)
    }
}

/**
 * Average frame interval and graph draw time, published twice a second so the readout doesn't
 * recompose every frame. Draw time is CPU time spent recording every graph on the page into the
 * display list, summed per frame — a relative cost signal between presets, not a GPU measurement.
 */
@Stable
class NetworkGraphFrameStats {
    var frameMillis by mutableFloatStateOf(0f)
        private set
    var drawMillis by mutableFloatStateOf(0f)
        private set

    private var intervalNanosSum = 0L
    private var drawNanosSum = 0L
    private var frames = 0

    fun recordDraw(nanos: Long) {
        drawNanosSum += nanos
    }

    fun recordFrame(intervalNanos: Long) {
        intervalNanosSum += intervalNanos
        frames++
        if (intervalNanosSum >= PUBLISH_INTERVAL_NANOS) {
            frameMillis = intervalNanosSum / frames / NANOS_PER_MILLI
            drawMillis = drawNanosSum / frames / NANOS_PER_MILLI
            intervalNanosSum = 0L
            drawNanosSum = 0L
            frames = 0
        }
    }

    private companion object {
        const val PUBLISH_INTERVAL_NANOS = 500_000_000L
    }
}

/**
 * Per layer, back to front: nodes drawn / nodes pruned by [enforceMinDegree] · edges it added by
 * repair. Refreshed twice a second. A layer that leans on repair hard is one whose filters fight
 * its density.
 */
@Stable
class NetworkGraphLayerStats {
    var summary by mutableStateOf("")
        private set

    private var lastPublishNanos = 0L

    internal fun record(fields: List<LayerField>) {
        val nowNanos = System.nanoTime()
        if (nowNanos - lastPublishNanos < PUBLISH_INTERVAL_NANOS) return
        lastPublishNanos = nowNanos
        summary = fields.joinToString(separator = "  |  ") { field ->
            "${field.visibleNodeCount}/${field.prunedNodeCount} · ${field.repairedEdgeCount}"
        }
    }

    private companion object {
        const val PUBLISH_INTERVAL_NANOS = 500_000_000L
    }
}

/** Per-modifier state kept across frames: the built layer fields and the halo brushes. */
private class NetworkGraphHolder {
    private var builtSpec: NetworkGraphSpec? = null
    private var builtWidth = 0f
    private var builtHeight = 0f
    private var fields: List<LayerField> = emptyList()
    private val halosByColor = HashMap<Color, HaloBrushCache>()

    fun fieldsFor(spec: NetworkGraphSpec, width: Float, height: Float, pxPerDp: Float): List<LayerField> {
        if (spec != builtSpec || width != builtWidth || height != builtHeight) {
            fields = spec.layers.mapIndexed { index, layer ->
                LayerField(layer, spec.seed + index * LAYER_SEED_STRIDE, width, height, pxPerDp)
            }
            builtSpec = spec
            builtWidth = width
            builtHeight = height
        }
        return fields
    }

    fun halosFor(color: Color): HaloBrushCache = halosByColor.getOrPut(color) { HaloBrushCache(color) }
}

/**
 * One layer's nodes and edges.
 *
 * Every cell of the grid draws the same fixed number of randoms whether or not it ends up holding a
 * node, so tuning an acceptance-only field (fill chance, envelope shape) leaves the surviving nodes
 * exactly where they were instead of reshuffling the whole layer.
 *
 * Per frame, [advance] moves the nodes, settles the edge set through [enforceMinDegree], and derives
 * each edge's and node's alpha; [draw] only paints what [advance] decided. Internal rather than private
 * so tests can check the invariant on real layers.
 */
internal class LayerField(
    private val spec: NetworkGraphLayerSpec,
    private val seed: Int,
    width: Float,
    height: Float,
    private val pxPerDp: Float,
) {
    private val random = Random(seed)
    private val cell = max(width, height) / spec.density.coerceAtLeast(1f)
    private val envelope = NetworkGraphEnvelope(spec, random, width, height)

    private val homeX: FloatArray
    private val homeY: FloatArray
    private val axisX: FloatArray
    private val axisY: FloatArray
    private val drifts: Array<NodeDrift>
    private val depths: FloatArray
    private val pulsePhases: FloatArray
    private val sizeFactors: FloatArray
    private val hot: BooleanArray
    private val count: Int

    private val positionsX: FloatArray
    private val positionsY: FloatArray

    private var edgeKeys = LongArray(0)
    private var edgeFades = FloatArray(0)

    /** Each edge's alpha from length, depth, fade and layer alpha alone, before its dots limit it. */
    private var rawEdgeAlphas = FloatArray(0)

    /** Each edge's alpha as drawn, before pulse: [rawEdgeAlphas] limited by the dimmer of its two dots. */
    private var edgeAlphas = FloatArray(0)
    private var edgeCount = 0

    /** Per-frame rules only: each edge's current fade, kept while it fades out after leaving the set. */
    private val fadingEdges = HashMap<Long, FadingEdge>()

    private val brightestEdgeAlphas: FloatArray
    private val secondEdgeAlphas: FloatArray
    private val visibleEdgeCounts: IntArray
    private val nodeAlphas: FloatArray
    private val glowFactors: FloatArray

    /** The last edge set [enforceMinDegree] settled on. */
    internal var topology: MeshTopology
        private set

    internal val nodeCount: Int get() = count
    internal var visibleNodeCount = 0
        private set
    internal val prunedNodeCount: Int get() = topology.prunedNodeCount
    internal val repairedEdgeCount: Int get() = topology.repairedEdgeCount

    private var lastTimeSeconds = Float.NaN

    /** Composites the offscreen layer [draw] paints into with plain source-over at full alpha. */
    private val layerPaint = Paint()

    init {
        val nodes = generateNodes(width, height)
        count = nodes.size
        homeX = FloatArray(count) { nodes[it].homeX }
        homeY = FloatArray(count) { nodes[it].homeY }
        axisX = FloatArray(count) { nodes[it].axisX }
        axisY = FloatArray(count) { nodes[it].axisY }
        drifts = Array(count) { nodes[it].drift }
        depths = FloatArray(count) { nodes[it].depth }
        pulsePhases = FloatArray(count) { nodes[it].pulsePhase }
        sizeFactors = FloatArray(count) { nodes[it].sizeFactor }
        hot = BooleanArray(count) { nodes[it].hot }
        positionsX = homeX.copyOf()
        positionsY = homeY.copyOf()
        brightestEdgeAlphas = FloatArray(count)
        secondEdgeAlphas = FloatArray(count)
        visibleEdgeCounts = IntArray(count)
        nodeAlphas = FloatArray(count)
        glowFactors = FloatArray(count)
        // Triangulated once, over rest positions: drift stays under a cell, so the mesh never tangles.
        // The per-frame rules overwrite this on their first frame.
        topology = settleTopology(homeX, homeY)
        if (spec.edgeRule == EdgeRule.FixedTriangulation) setEdges(topology.edges)
    }

    /** Edge alpha of node [index]'s second-brightest edge, as of the last [advance]. */
    internal fun secondEdgeAlpha(index: Int): Float = secondEdgeAlphas[index]

    /** How many of node [index]'s edges are at or above [EDGE_VISIBLE_ALPHA], as of the last [advance]. */
    internal fun visibleEdgeCount(index: Int): Int = visibleEdgeCounts[index]

    /** Node [index]'s dot alpha, as of the last [advance]. */
    internal fun nodeAlpha(index: Int): Float = nodeAlphas[index]

    /** How many edges [draw] paints, fading-out ones included, as of the last [advance]. */
    internal val drawnEdgeCount: Int get() = edgeCount

    internal fun drawnEdgeKey(edge: Int): Long = edgeKeys[edge]

    /** Drawn edge [edge]'s alpha at [timeSeconds], pulse included — exactly what [draw] paints. */
    internal fun drawnEdgeAlpha(edge: Int, timeSeconds: Float): Float {
        if (!spec.pulse) return edgeAlphas[edge]
        val key = edgeKeys[edge]
        val phase = timeSeconds * EDGE_PULSE_SPEED + pulsePhases[edgeStart(key)] + pulsePhases[edgeEnd(key)]
        return edgeAlphas[edge] * (EDGE_PULSE_FLOOR + (1f - EDGE_PULSE_FLOOR) * (0.5f * sin(phase) + 0.5f))
    }

    fun advance(timeSeconds: Float) {
        val stepSeconds = if (lastTimeSeconds.isNaN()) 0f else (timeSeconds - lastTimeSeconds).coerceIn(0f, MAX_STEP_SECONDS)
        val isFirstFrame = lastTimeSeconds.isNaN()
        lastTimeSeconds = timeSeconds

        val driftTime = timeSeconds * spec.driftSpeed
        for (index in 0 until count) {
            val local = drifts[index].offsetAt(driftTime)
            positionsX[index] = homeX[index] + (axisX[index] * local.x - axisY[index] * local.y) * cell
            positionsY[index] = homeY[index] + (axisY[index] * local.x + axisX[index] * local.y) * cell +
                envelope.waveOffsetY(homeX[index], timeSeconds)
        }

        if (spec.edgeRule != EdgeRule.FixedTriangulation) {
            topology = settleTopology(positionsX, positionsY)
            updateFadingEdges(stepSeconds, isFirstFrame)
        }
        updateAlphas()
    }

    /**
     * Paints the layer into its own offscreen layer, composited onto the canvas once. Edges and halos
     * blend with each other there, but each dot is drawn with [BlendMode.Src] and replaces whatever lies
     * under it, so a dot's pixels are exactly its own alpha instead of summing with the edges it anchors.
     */
    fun draw(scope: DrawScope, color: Color, halos: HaloBrushCache, timeSeconds: Float, showViolations: Boolean) = with(scope) {
        drawIntoCanvas { canvas -> canvas.saveLayer(Rect(Offset.Zero, size), layerPaint) }
        drawEdges(color, timeSeconds)
        drawGlows(halos)
        drawCores(color)
        drawIntoCanvas { canvas -> canvas.restore() }
        if (showViolations) drawViolations(timeSeconds)
    }

    private fun generateNodes(width: Float, height: Float): List<NodeTraits> {
        val columns = ceil(width / cell).toInt()
        val rows = ceil(height / cell).toInt()
        val nodes = ArrayList<NodeTraits>()
        for (row in -BORDER_RING until rows + BORDER_RING) {
            for (column in -BORDER_RING until columns + BORDER_RING) {
                // A random orientation per node, which both its jitter and its drift are written in, so
                // no two nodes' drift boxes line up into a visible square weave.
                val angle = random.nextFloat() * TWO_PI
                val nodeAxisX = cos(angle)
                val nodeAxisY = sin(angle)
                val jitterX = (random.nextFloat() - 0.5f) * 2f * spec.jitter
                val jitterY = (random.nextFloat() - 0.5f) * 2f * spec.jitter
                val acceptRoll = random.nextFloat()
                val traits = NodeTraits(
                    homeX = (column + 0.5f + nodeAxisX * jitterX - nodeAxisY * jitterY) * cell,
                    homeY = (row + 0.5f + nodeAxisY * jitterX + nodeAxisX * jitterY) * cell,
                    axisX = nodeAxisX,
                    axisY = nodeAxisY,
                    drift = NodeDrift(
                        primary = randomDriftAxis(DRIFT_FREQUENCY_BASE_PRIMARY),
                        secondary = randomDriftAxis(DRIFT_FREQUENCY_BASE_SECONDARY),
                        amplitude = spec.driftAmplitude * (DRIFT_SCALE_FLOOR + (1f - DRIFT_SCALE_FLOOR) * random.nextFloat()),
                    ),
                    depth = random.nextFloat(),
                    pulsePhase = random.nextFloat() * TWO_PI,
                    sizeFactor = max(NODE_SIZE_FLOOR, 1f + spec.nodeSizeVariance * (random.nextFloat() * 2f - 1f)),
                    hot = random.nextFloat() < spec.hotNodeShare,
                )
                if (acceptRoll < envelope.densityAt(traits.homeX, traits.homeY) * spec.fillChance) nodes += traits
            }
        }
        return nodes
    }

    /** One independent phase per octave, so no two octaves start their curve at the same place. */
    private fun randomDriftAxis(baseFrequency: Float) = DriftAxis(
        frequency = baseFrequency + random.nextFloat() * DRIFT_FREQUENCY_SPREAD,
        coarsePhase = random.nextFloat() * TWO_PI,
        mediumPhase = random.nextFloat() * TWO_PI,
        finePhase = random.nextFloat() * TWO_PI,
    )

    /** The rule's filtered edges, then [enforceMinDegree] with the rule's repair candidates. */
    private fun settleTopology(xs: FloatArray, ys: FloatArray): MeshTopology {
        val baseEdges: LongArray
        val candidateEdges: LongArray
        if (spec.edgeRule == EdgeRule.Proximity) {
            baseEdges = proximityEdges(xs, ys)
            candidateEdges = nearestNeighbourEdgeKeys(count, xs, ys, PROXIMITY_REPAIR_NEIGHBOURS)
        } else {
            val triangles = Delaunay.triangulate(xs, ys, count)
            baseEdges = meshEdgeKeys(
                triangles = triangles,
                xs = xs,
                ys = ys,
                minAngleDegrees = spec.minAngleDegrees,
                maxLength = spec.maxEdgeFactor * cell,
                keepChance = spec.edgeKeepChance,
                seed = seed,
            )
            candidateEdges = triangleEdgeKeys(triangles)
        }
        return enforceMinDegree(
            count = count,
            xs = xs,
            ys = ys,
            baseEdges = baseEdges,
            candidateEdges = candidateEdges,
            maxRepairLength = spec.repairReach * spec.maxEdgeFactor * cell,
            strictTriangles = spec.strictTriangles,
        )
    }

    /** Every pair within reach that passes its keep chance. */
    private fun proximityEdges(xs: FloatArray, ys: FloatArray): LongArray {
        val reach = spec.maxEdgeFactor * cell
        val reachSquared = reach * reach
        val keys = ArrayList<Long>()
        for (first in 0 until count) {
            for (second in first + 1 until count) {
                val deltaX = xs[second] - xs[first]
                val deltaY = ys[second] - ys[first]
                if (deltaX * deltaX + deltaY * deltaY >= reachSquared) continue
                if (pairRandom(first, second, seed) >= spec.edgeKeepChance) continue
                keys += edgeKey(first, second)
            }
        }
        return keys.toLongArray()
    }

    private fun setEdges(keys: LongArray) {
        edgeKeys = keys
        edgeFades = FloatArray(keys.size) { 1f }
        rawEdgeAlphas = FloatArray(keys.size)
        edgeAlphas = FloatArray(keys.size)
        edgeCount = keys.size
    }

    private fun ensureEdgeCapacity(capacity: Int) {
        if (edgeKeys.size >= capacity) return
        edgeKeys = edgeKeys.copyOf(max(capacity, edgeKeys.size * 2))
        edgeFades = edgeFades.copyOf(edgeKeys.size)
        rawEdgeAlphas = rawEdgeAlphas.copyOf(edgeKeys.size)
        edgeAlphas = edgeAlphas.copyOf(edgeKeys.size)
    }

    /** Edges entering [topology] fade in, edges leaving it fade out; the first frame starts fully drawn. */
    private fun updateFadingEdges(stepSeconds: Float, isFirstFrame: Boolean) {
        for (edge in fadingEdges.values) edge.present = false
        for (key in topology.edges) {
            val edge = fadingEdges.getOrPut(key) { FadingEdge(alpha = if (isFirstFrame) 1f else 0f) }
            edge.present = true
        }
        val fadeStep = stepSeconds / EDGE_FLIP_FADE_SECONDS
        val iterator = fadingEdges.entries.iterator()
        edgeCount = 0
        ensureEdgeCapacity(fadingEdges.size)
        while (iterator.hasNext()) {
            val (key, edge) = iterator.next()
            edge.alpha = if (edge.present) min(1f, edge.alpha + fadeStep) else edge.alpha - fadeStep
            if (edge.alpha <= 0f) {
                iterator.remove()
                continue
            }
            edgeKeys[edgeCount] = key
            edgeFades[edgeCount] = edge.alpha
            edgeCount++
        }
    }

    /**
     * Each edge's and each dot's alpha, holding both invariants whatever the falloff, depth, fade or
     * alpha settings do:
     * - a dot fades with its second-brightest edge and is hidden once that edge drops under
     *   [EDGE_VISIBLE_ALPHA], so a node can't show without two visible lines;
     * - an edge is never more opaque than the dimmer of its two dots, so a dot is at least as opaque as
     *   every edge it anchors, and a fading dot takes its edges with it.
     *
     * Limiting edges can lower a neighbour's second-brightest edge and so its dot, so the two settle
     * over rounds. Rounds only ever lower alphas; after [MAX_ALPHA_ROUNDS], a removal-only pass hides
     * any dot still short of two visible edges.
     */
    private fun updateAlphas() {
        updateRawEdgeAlphas()
        for (index in 0 until count) nodeAlphas[index] = dotBaseAlpha(index)
        var settled = false
        var rounds = 0
        while (!settled && rounds < MAX_ALPHA_ROUNDS) {
            tallyIncidentEdges()
            settled = !lowerDotsToTheirEdges()
            limitEdgesByDots()
            rounds++
        }
        do {
            tallyIncidentEdges()
            val hidAny = hideUnderconnectedDots()
            if (hidAny) limitEdgesByDots()
        } while (hidAny)
        visibleNodeCount = nodeAlphas.count { it >= NODE_VISIBLE_ALPHA }
    }

    private fun updateRawEdgeAlphas() {
        val referenceLength = spec.maxEdgeFactor * cell
        for (edge in 0 until edgeCount) {
            val key = edgeKeys[edge]
            val start = edgeStart(key)
            val end = edgeEnd(key)
            val deltaX = positionsX[end] - positionsX[start]
            val deltaY = positionsY[end] - positionsY[start]
            val length = sqrt(deltaX * deltaX + deltaY * deltaY)
            val lengthFactor = 1f - spec.lengthFalloff * (length / referenceLength).coerceIn(0f, 1f)
            val depthFactor = min(depthAlpha(start), depthAlpha(end))
            val alpha = (spec.edgeAlpha * spec.alpha * lengthFactor * depthFactor * edgeFades[edge]).coerceIn(0f, 1f)
            rawEdgeAlphas[edge] = alpha
            edgeAlphas[edge] = alpha
        }
    }

    /**
     * Node [index]'s dot alpha with two healthy edges. Never under the edge alpha nominal at its depth,
     * so with healthy edges the edge limit never bites.
     */
    private fun dotBaseAlpha(index: Int): Float {
        val baseAlpha = if (hot[index]) 1f else max(NODE_ALPHA, spec.edgeAlpha)
        return (baseAlpha * spec.alpha * depthAlpha(index)).coerceIn(0f, 1f)
    }

    private fun tallyIncidentEdges() {
        brightestEdgeAlphas.fill(0f)
        secondEdgeAlphas.fill(0f)
        visibleEdgeCounts.fill(0)
        for (edge in 0 until edgeCount) {
            val key = edgeKeys[edge]
            recordIncidentEdge(edgeStart(key), edgeAlphas[edge])
            recordIncidentEdge(edgeEnd(key), edgeAlphas[edge])
        }
    }

    private fun recordIncidentEdge(node: Int, alpha: Float) {
        if (alpha >= EDGE_VISIBLE_ALPHA) visibleEdgeCounts[node]++
        if (alpha > brightestEdgeAlphas[node]) {
            secondEdgeAlphas[node] = brightestEdgeAlphas[node]
            brightestEdgeAlphas[node] = alpha
        } else if (alpha > secondEdgeAlphas[node]) {
            secondEdgeAlphas[node] = alpha
        }
    }

    /** Fades each dot with its second-brightest edge, never raising one; true if any dot dropped noticeably. */
    private fun lowerDotsToTheirEdges(): Boolean {
        val nominalEdgeAlpha = spec.edgeAlpha * spec.alpha
        var lowered = false
        for (index in 0 until count) {
            val second = secondEdgeAlphas[index]
            val fullAt = max(NODE_FULL_AT_EDGE_SHARE * nominalEdgeAlpha * depthAlpha(index), 2f * EDGE_VISIBLE_ALPHA)
            val factor = if (second < EDGE_VISIBLE_ALPHA) 0f else smoothStep(EDGE_VISIBLE_ALPHA, fullAt, second)
            val alpha = min(nodeAlphas[index], dotBaseAlpha(index) * factor)
            if (nodeAlphas[index] - alpha > ALPHA_SETTLE_EPSILON) lowered = true
            nodeAlphas[index] = alpha
            glowFactors[index] = factor
        }
        return lowered
    }

    private fun limitEdgesByDots() {
        for (edge in 0 until edgeCount) {
            val key = edgeKeys[edge]
            edgeAlphas[edge] = min(rawEdgeAlphas[edge], min(nodeAlphas[edgeStart(key)], nodeAlphas[edgeEnd(key)]))
        }
    }

    /** Hides every dot still drawn with its second-brightest edge under [EDGE_VISIBLE_ALPHA]; true if any. */
    private fun hideUnderconnectedDots(): Boolean {
        var hidAny = false
        for (index in 0 until count) {
            if (nodeAlphas[index] <= 0f || secondEdgeAlphas[index] >= EDGE_VISIBLE_ALPHA) continue
            nodeAlphas[index] = 0f
            glowFactors[index] = 0f
            hidAny = true
        }
        return hidAny
    }

    private fun depthAlpha(index: Int): Float = 1f - spec.depthDimming * (1f - depths[index])

    private fun DrawScope.drawEdges(color: Color, timeSeconds: Float) {
        val strokeWidth = max(1f, spec.strokeWidthDp * pxPerDp)
        for (edge in 0 until edgeCount) {
            val key = edgeKeys[edge]
            val start = edgeStart(key)
            val end = edgeEnd(key)
            val alpha = drawnEdgeAlpha(edge, timeSeconds)
            if (alpha < EDGE_MIN_VISIBLE_ALPHA) continue
            drawLine(
                color = color.copy(alpha = alpha),
                start = Offset(positionsX[start], positionsY[start]),
                end = Offset(positionsX[end], positionsY[end]),
                strokeWidth = strokeWidth,
            )
        }
    }

    private fun DrawScope.drawGlows(halos: HaloBrushCache) {
        if (spec.glowStrength <= 0f || spec.hotNodeShare <= 0f) return
        for (index in 0 until count) {
            if (!hot[index]) continue
            val radius = spec.glowRadiusDp * pxPerDp * sizeFactors[index]
            val alpha = spec.glowStrength * spec.alpha * depthAlpha(index) * glowFactors[index]
            drawHalo(halos, positionsX[index], positionsY[index], radius, alpha)
        }
    }

    /** Drawn last and with [BlendMode.Src], so a dot replaces the edges and halo under it rather than summing with them. */
    private fun DrawScope.drawCores(color: Color) {
        for (index in 0 until count) {
            if (nodeAlphas[index] < EDGE_MIN_VISIBLE_ALPHA) continue
            val depthShrink = 1f - DEPTH_SIZE_SHRINK * spec.depthDimming * (1f - depths[index])
            val hotScale = if (hot[index]) HOT_NODE_SCALE else 1f
            drawCircle(
                color = color.copy(alpha = nodeAlphas[index]),
                radius = spec.nodeSizeDp * pxPerDp * sizeFactors[index] * hotScale * depthShrink,
                center = Offset(positionsX[index], positionsY[index]),
                blendMode = BlendMode.Src,
            )
        }
    }

    /**
     * Rings every node drawn visibly with fewer than two visible edges, and every node fainter than an
     * edge it anchors as drawn this frame; there should never be either.
     */
    private fun DrawScope.drawViolations(timeSeconds: Float) {
        val stroke = Stroke(width = VIOLATION_RING_STROKE_DP * pxPerDp)
        val brightestDrawn = FloatArray(count)
        for (edge in 0 until edgeCount) {
            val key = edgeKeys[edge]
            val alpha = drawnEdgeAlpha(edge, timeSeconds)
            brightestDrawn[edgeStart(key)] = max(brightestDrawn[edgeStart(key)], alpha)
            brightestDrawn[edgeEnd(key)] = max(brightestDrawn[edgeEnd(key)], alpha)
        }
        for (index in 0 until count) {
            val underconnected = nodeAlphas[index] >= NODE_VISIBLE_ALPHA && visibleEdgeCounts[index] < 2
            val fainterThanEdge = brightestDrawn[index] > nodeAlphas[index] + ALPHA_RULE_TOLERANCE
            if (!underconnected && !fainterThanEdge) continue
            drawCircle(
                color = ViolationColor,
                radius = VIOLATION_RING_RADIUS_DP * pxPerDp,
                center = Offset(positionsX[index], positionsY[index]),
                style = stroke,
            )
        }
    }

    /** The brush's gradient is centered on the origin, so the canvas moves to the point instead. */
    private fun DrawScope.drawHalo(halos: HaloBrushCache, x: Float, y: Float, radius: Float, alpha: Float) {
        if (alpha <= 0f || radius <= 0f) return
        translate(left = x, top = y) {
            drawCircle(brush = halos.brushFor(radius), radius = radius, center = Offset.Zero, alpha = alpha.coerceAtMost(1f))
        }
    }
}

private class FadingEdge(var alpha: Float, var present: Boolean = true)

private class NodeTraits(
    val homeX: Float,
    val homeY: Float,
    val axisX: Float,
    val axisY: Float,
    val drift: NodeDrift,
    val depth: Float,
    val pulsePhase: Float,
    val sizeFactor: Float,
    val hot: Boolean,
)

/** One axis of a node's wander: three sine octaves at non-rational ratios, so the path never retraces. */
private class DriftAxis(private val frequency: Float, private val coarsePhase: Float, private val mediumPhase: Float, private val finePhase: Float) {

    /** Displacement along this axis at [timeSeconds], as a fraction of the node's amplitude, in -1..1. */
    fun offsetAt(timeSeconds: Float): Float =
        DRIFT_OCTAVE_WEIGHT_COARSE * sin(timeSeconds * frequency + coarsePhase) +
            DRIFT_OCTAVE_WEIGHT_MEDIUM * sin(timeSeconds * frequency * DRIFT_OCTAVE_RATIO_MEDIUM + mediumPhase) +
            DRIFT_OCTAVE_WEIGHT_FINE * sin(timeSeconds * frequency * DRIFT_OCTAVE_RATIO_FINE + finePhase)
}

/** A node's wander in its own rotated frame, in cells. */
private class NodeDrift(private val primary: DriftAxis, private val secondary: DriftAxis, private val amplitude: Float) {
    fun offsetAt(timeSeconds: Float): Offset = Offset(primary.offsetAt(timeSeconds) * amplitude, secondary.offsetAt(timeSeconds) * amplitude)
}

/**
 * One radial-gradient [Brush] per halo radius, built on first use and kept. Per-halo brightness goes
 * through `drawCircle`'s alpha rather than into the brush, so a handful of brushes cover a surface.
 */
internal class HaloBrushCache(private val color: Color) {
    private val brushesByRadius = HashMap<Int, Brush>()

    fun brushFor(radius: Float): Brush {
        val key = radius.toInt().coerceAtLeast(1)
        return brushesByRadius.getOrPut(key) {
            // Fading to the same color at zero alpha, not Color.Transparent, which would pass through black.
            Brush.radialGradient(
                colors = listOf(color.copy(alpha = 1f), color.copy(alpha = 0f)),
                center = Offset.Zero,
                radius = key.toFloat(),
            )
        }
    }
}
