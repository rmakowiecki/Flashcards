// Every tuning value with a meaning of its own is either a spec field or a named constant below. What
// detekt flags here is the arithmetic of the formulas themselves (the 0.5 of a cell's center, the 2
// that turns 0..1 into -1..1), which naming individually would only bury.
@file:Suppress("MagicNumber", "LoopWithTooManyJumpStatements", "TooManyFunctions", "LongParameterList")

package com.rossomak.flashcards.feature.debug.networkgraph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Rings of cells past every edge, so nodes drifting in from off-surface keep the borders populated. */
private const val BORDER_RING = 2

/** A node whose home sits further outside the surface than this many cells starts fading out, and is gone half a cell later. */
private const val BORDER_FADE_START_CELLS = 1f
private const val BORDER_FADE_END_CELLS = 1.5f

/** A frame gap longer than this (a pause, a page swipe) advances fades and drift by this much only. */
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

/** How long an edge takes to fade fully in or out when it enters or leaves the edge set, so nothing pops. */
private const val EDGE_FLIP_FADE_SECONDS = 0.6f

/** How many nearest neighbours [EdgeRule.Proximity] offers [enforceMinDegree] as repair candidates. */
private const val PROXIMITY_REPAIR_NEIGHBOURS = 6

/**
 * Width of the soft band in which a node fades in or out as its shape density (or the hot node share)
 * passes the node's own roll. Wider means softer morphs, but more nodes left part way in a still frame.
 */
private const val ROLL_BAND = 0.1f

/** A node at least this present joins the mesh; fainter ones are left out of it entirely. */
private const val PRESENT_THRESHOLD = 0.05f

/**
 * Under the rules that re-settle every frame, a node that is not yet in the mesh joins only once this
 * present, so a fraction jittering around one node's switch (a finger resting on a pager) doesn't
 * flip it in and out every frame. [EdgeRule.FixedTriangulation] needs none: during a morph its mesh
 * comes from the two ends, not from the frame's presences.
 */
private const val PRESENT_JOIN_THRESHOLD = 0.15f

/** The span of a morph's fraction over which one node fades between its presences at the two ends. */
private const val MORPH_FADE_WINDOW = 0.2f

/**
 * How far a smooth noise over the surface moves a node's switch fraction either way, so a morph's front
 * is ragged rather than a clean curve. The noise varies over a few cells, so neighbours switch nearly
 * together and a node leaving and the node arriving beside it keep pace.
 */
private const val MORPH_FRONT_JITTER = 0.15f

/** Spatial frequencies of the front noise, in radians per cell; non-rational ratios so it never tiles. */
private const val FRONT_NOISE_FREQUENCY_X = 0.9f
private const val FRONT_NOISE_FREQUENCY_Y = 0.7f
private const val FRONT_NOISE_FREQUENCY_DIAGONAL = 0.45f
private const val FRONT_NOISE_SEED_SALT = 0x2F6B_1D3

/** Presences at a morph's two ends closer than this count as equal: the node has no switch and simply blends between them. */
private const val MORPH_CHANGE_EPSILON = 1e-3f

/** Closeness spreads narrower than this give no order, and a morph switches its nodes in roll order instead. */
private const val MORPH_MIN_CLOSENESS_SPREAD = 1e-3f

/**
 * How many steps of a morph's fraction a comet's light on each node is sampled at, once per morph. In
 * between, the presence is interpolated, so a node's comet light rises and falls in straight segments.
 */
private const val COMET_SAMPLES = 48

/**
 * A comet lights a node once its density at the node passes this level, give or take the layer's front
 * noise, over a band of [COMET_LIGHT_BAND]. Lighting by level rather than by each node's own roll keeps
 * the lit nodes one solid patch, so every lit node has lit neighbours to draw its edges to.
 */
private const val COMET_LIGHT_LEVEL = 0.45f
private const val COMET_LIGHT_JITTER = 0.12f
private const val COMET_LIGHT_BAND = 0.1f

/** Share of a comet's nodes that heat up as it reaches them, under [NetworkGraphLayerSpec.cometSpark]. */
private const val COMET_SPARK_SHARE = 0.4f

/** How long before its comet peaks a sparking node starts cooling, and how long after it has cooled, as fractions of the morph. */
private const val COMET_SPARK_LEAD = 0.08f
private const val COMET_SPARK_TRAIL = 0.06f

/** Per morph nesting level: presences at both ends, then closeness to each end's shape. */
private const val SCRATCH_SLOTS_PER_LEVEL = 4

/** An edge drawn fainter than this does not count toward a node's two visible edges. */
internal const val EDGE_VISIBLE_ALPHA = 0.02f

/** A node's dot drawn fainter than this counts as not drawn. */
internal const val NODE_VISIBLE_ALPHA = 0.03f

/**
 * A node's dot is at full alpha once its second-brightest edge reaches this share of the edge alpha
 * nominal at the node's depth and presence, and fades out below it, reaching zero at [EDGE_VISIBLE_ALPHA].
 */
private const val NODE_FULL_AT_EDGE_SHARE = 0.35f

/**
 * Cap on [LayerField]'s dot/edge alpha rounds. Each round only lowers alphas, so they settle on their
 * own; a dot whose edges barely clear [EDGE_VISIBLE_ALPHA] can keep sinking for many rounds, and the
 * removal-only pass after the cap hides it instead.
 */
private const val MAX_ALPHA_ROUNDS = 16

/**
 * How far over [EDGE_VISIBLE_ALPHA] an edge must be drawn at rest to count toward the mesh rules: one
 * drawn fainter reads as missing, so a node or join held only by such edges is repaired or pruned
 * instead. The margin covers drift stretching an edge.
 */
private const val VISIBLE_EDGE_MARGIN = 2f

/**
 * Cap on how many times a strict mesh re-settles without edges its own triangles leave too faint to
 * see; each pass only drops edges, and one more is rarely needed.
 */
private const val MAX_VISIBILITY_PASSES = 3

/** Cap on [LayerField]'s triangle fill rounds; one raise rarely enables another, so they settle fast. */
private const val MAX_TRIANGLE_FILL_ROUNDS = 3

/** An alpha round that lowers no dot by more than this counts as settled. */
private const val ALPHA_SETTLE_EPSILON = 1e-4f

/** How much brighter than an edge a dot may be before "Show violations" doubts the alpha rule held. */
private const val ALPHA_RULE_TOLERANCE = 1e-4f

private val ViolationColor = Color(0xFFFF1744)

/** Rings the nodes of a drawn graph smaller than [MIN_GRAPH_NODES]. */
private val SmallGraphColor = Color(0xFFFFAB00)
private const val VIOLATION_RING_RADIUS_DP = 7f
private const val VIOLATION_RING_STROKE_DP = 1.5f

/** A plain node's dot alpha before layer alpha and depth; raised to the layer's edge alpha when that is higher. */
private const val NODE_ALPHA = 0.85f
private const val HOT_NODE_SCALE = 1.4f
private const val NODE_SIZE_FLOOR = 0.2f

/** How much a far node shrinks at full [NetworkGraphLayerSpec.depthDimming]. */
private const val DEPTH_SIZE_SHRINK = 0.45f

/**
 * One layer's nodes and edges.
 *
 * The node pool is a fixed grid: the cell size comes from [referenceSize] (the window's longer side)
 * rather than the surface, and every cell draws its randoms from a hash of its own column, row and
 * [seed]. So a surface that grows only adds cells, nodes that already exist never move, and a surface
 * that shrinks keeps its pool and fades the nodes now outside it.
 *
 * Which nodes show is decided every frame, not at build time, by [shape]: under one spec, a node's
 * presence rises from 0 to 1 as the shape density at its home (see [NetworkGraphEnvelope]) passes its
 * own random roll. Under a morph, each node takes its presence at the two ends and only switches
 * between them, over a short window around a fraction of its own: nodes leaving go furthest from the
 * shape being morphed into first, nodes arriving go nearest to the shape being morphed out of first.
 * So a node present at both ends stays untouched, the shape recedes or grows as a front, and scrubbing
 * back retraces it exactly. Where a ribbon or bloom moves or changes shape, a comet travels from its old
 * place to its new one (see [Comet]) and lights the nodes it passes that neither end shows, each once.
 * Only nodes present enough join the mesh. [spec] and [shape] may change
 * freely within the morph family ([isSameMorphFamily]); anything else needs a new field.
 *
 * Per frame, [advance] moves the nodes, settles the edge set through [enforceMinDegree], and derives
 * each edge's and node's alpha; [draw] only paints what [advance] decided. Internal rather than private
 * so tests can check the invariants on real layers.
 */
internal class LayerField(
    spec: NetworkGraphLayerSpec,
    private val seed: Int,
    referenceSize: Float,
    private val pxPerDp: Float,
) {
    var spec: NetworkGraphLayerSpec = spec
        set(value) {
            require(value.isSameMorphFamily(field)) { "A layer field only morphs within its morph family" }
            field = value
        }

    /** Which nodes show; see the class doc. [spec] still carries every other value, interpolated. */
    var shape: LayerShape = LayerShape.Of(spec)

    private val cell = referenceSize / spec.density.coerceAtLeast(1f)
    private val envelope = NetworkGraphEnvelope(seed)
    private val frontNoisePhases = Random(seed xor FRONT_NOISE_SEED_SALT).let { random -> FloatArray(3) { random.nextFloat() * TWO_PI } }

    private val nodes = ArrayList<NodeTraits>()
    private var count = 0
    private var coveredColumns = 0
    private var coveredRows = 0
    private var hasCoverage = false

    private var homeX = FloatArray(0)
    private var homeY = FloatArray(0)
    private var positionsX = FloatArray(0)
    private var positionsY = FloatArray(0)
    private var presences = FloatArray(0)
    private var presentMask = BooleanArray(0)

    /** Per-node working arrays for [shape]'s presences, [SCRATCH_SLOTS_PER_LEVEL] per morph nesting level; see [evaluateShape]. */
    private val shapeScratch = ArrayList<FloatArray>()

    /** Each node's switch fraction in the top-level morph, NaN for a node the same at both ends. */
    private var switchFractions = FloatArray(0)

    /** Whether each node switching in the top-level morph is arriving (more present at its end) rather than leaving. */
    private var switchArrivals = BooleanArray(0)

    /** The comets of each morph nesting level, sampled per node. */
    private val cometTracks = CometTracks(envelope)

    /** The [EdgeRule.FixedTriangulation] mesh of each end of the current morph, and how its edges switch. */
    private var morphMesh: MorphMesh? = null
    private var hotWeights = FloatArray(0)
    private var brightestEdgeAlphas = FloatArray(0)
    private var secondEdgeAlphas = FloatArray(0)
    private var visibleEdgeCounts = IntArray(0)
    private var nodeAlphas = FloatArray(0)
    private var glowFactors = FloatArray(0)

    private var edgeKeys = LongArray(0)
    private var edgeFades = FloatArray(0)

    /** Each edge's alpha from length, depth, fade and layer alpha alone, before its dots limit it. */
    private var rawEdgeAlphas = FloatArray(0)

    /** Each edge's alpha as drawn, before pulse: [rawEdgeAlphas] limited by the dimmer of its two dots. */
    private var edgeAlphas = FloatArray(0)
    private var edgeCount = 0

    /** Each edge's current fade, kept while it fades out after leaving the set. */
    private val fadingEdges = HashMap<Long, FadingEdge>()

    /** [fillDrawnTriangles]' triangles, and the drawn edges they were found for. */
    private var triangleCache = IntArray(0)
    private var triangleCacheKeys = LongArray(0)

    /** The edge-filter values [EdgeRule.FixedTriangulation] last settled with; a change re-settles it. */
    private val settledFilters = FloatArray(4)

    /** Drift and wave time, each already multiplied by its speed, so a speed change never jumps a node. */
    private var driftClock = 0f
    private var waveTravel = 0f

    /** The last edge set [enforceMinDegree] settled on. */
    internal var topology = MeshTopology(LongArray(0), BooleanArray(0), repairedEdgeCount = 0, prunedNodeCount = 0, converged = true)
        private set

    /** Every node in the pool, absent ones included. */
    internal val nodeCount: Int get() = count
    internal var visibleNodeCount = 0
        private set

    /** How many comets the current morph sends, 0 when not morphing. */
    internal val cometCount: Int get() = if (shape is LayerShape.Morph) cometTracks.topLevel?.cometCount ?: 0 else 0

    /** How many times any node joined or left the mesh since the field was built. */
    internal var meshMembershipChanges = 0
        private set

    /** Nodes present enough to join the mesh that [enforceMinDegree] still had to prune. */
    internal var prunedNodeCount = 0
        private set
    internal val repairedEdgeCount: Int get() = topology.repairedEdgeCount

    private var lastTimeSeconds = Float.NaN

    /** Composites the offscreen layer [draw] paints into with plain source-over at full alpha. */
    private val layerPaint = Paint()

    /** Edge alpha of node [index]'s second-brightest edge, as of the last [advance]. */
    internal fun secondEdgeAlpha(index: Int): Float = secondEdgeAlphas[index]

    /** How many of node [index]'s edges are at or above [EDGE_VISIBLE_ALPHA], as of the last [advance]. */
    internal fun visibleEdgeCount(index: Int): Int = visibleEdgeCounts[index]

    /** Node [index]'s dot alpha, as of the last [advance]. */
    internal fun nodeAlpha(index: Int): Float = nodeAlphas[index]

    /** Node [index]'s presence under [shape], border fade included, as of the last [advance]. */
    internal fun nodePresence(index: Int): Float = presences[index]

    /** Node [index]'s rest position, which never changes once the node exists. */
    internal fun nodeHome(index: Int): Offset = Offset(homeX[index], homeY[index])

    /** How many edges [draw] paints, fading-out ones included, as of the last [advance]. */
    internal val drawnEdgeCount: Int get() = edgeCount

    internal fun drawnEdgeKey(edge: Int): Long = edgeKeys[edge]

    /** Drawn edge [edge]'s alpha at [timeSeconds], pulse included — exactly what [draw] paints. */
    internal fun drawnEdgeAlpha(edge: Int, timeSeconds: Float): Float {
        if (spec.pulseDepth <= 0f) return edgeAlphas[edge]
        val key = edgeKeys[edge]
        val phase = timeSeconds * EDGE_PULSE_SPEED + nodes[edgeStart(key)].pulsePhase + nodes[edgeEnd(key)].pulsePhase
        val trough = 1f - (0.5f * sin(phase) + 0.5f)
        return edgeAlphas[edge] * (1f - spec.pulseDepth.coerceIn(0f, 1f) * (1f - EDGE_PULSE_FLOOR) * trough)
    }

    /**
     * [connectivityViolations] of the graph as drawn by the last [advance]: its visible edges between
     * visible dots.
     */
    internal fun drawnConnectivityViolations(): ConnectivityViolations {
        val visible = (0 until edgeCount).map { edgeKeys[it] to edgeAlphas[it] }.filter { (key, alpha) ->
            alpha >= EDGE_VISIBLE_ALPHA && nodeAlphas[edgeStart(key)] >= NODE_VISIBLE_ALPHA && nodeAlphas[edgeEnd(key)] >= NODE_VISIBLE_ALPHA
        }
        return connectivityViolations(count, LongArray(visible.size) { visible[it].first })
    }

    /** Moves the layer to [timeSeconds] on a [width] × [height] surface, under the current [spec]. */
    fun advance(timeSeconds: Float, width: Float, height: Float) {
        val isFirstFrame = lastTimeSeconds.isNaN()
        val stepSeconds = if (isFirstFrame) 0f else (timeSeconds - lastTimeSeconds).coerceIn(0f, MAX_STEP_SECONDS)
        lastTimeSeconds = timeSeconds
        if (isFirstFrame) {
            driftClock = timeSeconds * spec.driftSpeed
            waveTravel = timeSeconds * spec.waveSpeed
        } else {
            driftClock += stepSeconds * spec.driftSpeed
            waveTravel += stepSeconds * spec.waveSpeed
        }

        ensureCoverage(width, height)
        updatePositions(width, height)
        val presenceChanged = updatePresences(width, height)
        val filtersChanged = updateSettledFilters()
        val morph = (shape as? LayerShape.Morph)?.takeIf { it.fraction > 0f && it.fraction < 1f }
        val wasMorphing = morphMesh != null
        if (spec.edgeRule != EdgeRule.FixedTriangulation) {
            morphMesh = null
            topology = settleTopology(positionsX, positionsY, presentMask, presences, spec)
        } else if (morph != null) {
            topology = morphMeshFor(morph, width, height).union
        } else if (isFirstFrame || presenceChanged || filtersChanged || wasMorphing) {
            morphMesh = null
            // Triangulated over rest positions: drift stays under a cell, so the mesh never tangles.
            topology = settleTopology(homeX, homeY, presentMask, presences, spec)
        }
        prunedNodeCount = (0 until count).count { presentMask[it] && !topology.alive[it] }
        updateFadingEdges(stepSeconds, isFirstFrame, morphing = morph != null)
        morph?.let { applyMorphEdgeWeights(it.fraction) }
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

    /** Adds the cells a grown surface needs, plus the border rings; never removes any. */
    private fun ensureCoverage(width: Float, height: Float) {
        val columns = ceil(width / cell).toInt()
        val rows = ceil(height / cell).toInt()
        if (hasCoverage && columns <= coveredColumns && rows <= coveredRows) return
        val newColumns = max(columns, coveredColumns)
        val newRows = max(rows, coveredRows)
        for (row in -BORDER_RING until newRows + BORDER_RING) {
            for (column in -BORDER_RING until newColumns + BORDER_RING) {
                val covered = hasCoverage && column < coveredColumns + BORDER_RING && row < coveredRows + BORDER_RING
                if (!covered) nodes += generateNode(column, row)
            }
        }
        coveredColumns = newColumns
        coveredRows = newRows
        hasCoverage = true
        growNodeArrays(nodes.size)
    }

    private fun growNodeArrays(newCount: Int) {
        homeX = FloatArray(newCount) { index -> nodes[index].homeX }
        homeY = FloatArray(newCount) { index -> nodes[index].homeY }
        positionsX = positionsX.copyOf(newCount)
        positionsY = positionsY.copyOf(newCount)
        presences = presences.copyOf(newCount)
        presentMask = presentMask.copyOf(newCount)
        switchFractions = switchFractions.copyOf(newCount)
        switchArrivals = switchArrivals.copyOf(newCount)
        hotWeights = hotWeights.copyOf(newCount)
        brightestEdgeAlphas = brightestEdgeAlphas.copyOf(newCount)
        secondEdgeAlphas = secondEdgeAlphas.copyOf(newCount)
        visibleEdgeCounts = visibleEdgeCounts.copyOf(newCount)
        nodeAlphas = nodeAlphas.copyOf(newCount)
        glowFactors = glowFactors.copyOf(newCount)
        count = newCount
    }

    /** One node per cell, from randoms that depend only on the cell and [seed], never on spec values that morph. */
    private fun generateNode(column: Int, row: Int): NodeTraits {
        val random = Random(cellSeed(column, row, seed))
        // A random orientation per node, which both its jitter and its drift are written in, so no two
        // nodes' drift boxes line up into a visible square weave.
        val angle = random.nextFloat() * TWO_PI
        val nodeAxisX = cos(angle)
        val nodeAxisY = sin(angle)
        val jitterX = (random.nextFloat() - 0.5f) * 2f * spec.jitter
        val jitterY = (random.nextFloat() - 0.5f) * 2f * spec.jitter
        return NodeTraits(
            homeX = (column + 0.5f + nodeAxisX * jitterX - nodeAxisY * jitterY) * cell,
            homeY = (row + 0.5f + nodeAxisY * jitterX + nodeAxisX * jitterY) * cell,
            axisX = nodeAxisX,
            axisY = nodeAxisY,
            drift = NodeDrift(
                primary = randomDriftAxis(random, DRIFT_FREQUENCY_BASE_PRIMARY),
                secondary = randomDriftAxis(random, DRIFT_FREQUENCY_BASE_SECONDARY),
            ),
            driftScale = DRIFT_SCALE_FLOOR + (1f - DRIFT_SCALE_FLOOR) * random.nextFloat(),
            depth = random.nextFloat(),
            pulsePhase = random.nextFloat() * TWO_PI,
            sizeRoll = random.nextFloat(),
            hotRoll = random.nextFloat(),
            presenceRoll = random.nextFloat(),
            morphRoll = random.nextFloat(),
            frontNoise = frontNoiseAt(column + 0.5f, row + 0.5f),
        )
    }

    /** The layer's smooth front noise at a point given in cells, in -1..1. */
    private fun frontNoiseAt(columns: Float, rows: Float): Float =
        0.5f * (
            sin(columns * FRONT_NOISE_FREQUENCY_X + frontNoisePhases[0]) * cos(rows * FRONT_NOISE_FREQUENCY_Y + frontNoisePhases[1]) +
                sin((columns + rows) * FRONT_NOISE_FREQUENCY_DIAGONAL + frontNoisePhases[2])
            )

    /** One independent phase per octave, so no two octaves start their curve at the same place. */
    private fun randomDriftAxis(random: Random, baseFrequency: Float) = DriftAxis(
        frequency = baseFrequency + random.nextFloat() * DRIFT_FREQUENCY_SPREAD,
        coarsePhase = random.nextFloat() * TWO_PI,
        mediumPhase = random.nextFloat() * TWO_PI,
        finePhase = random.nextFloat() * TWO_PI,
    )

    private fun updatePositions(width: Float, height: Float) {
        for (index in 0 until count) {
            val node = nodes[index]
            val local = node.drift.offsetAt(driftClock)
            val reach = node.driftScale * spec.driftAmplitude * cell
            positionsX[index] = homeX[index] + (node.axisX * local.x - node.axisY * local.y) * reach
            positionsY[index] = homeY[index] + (node.axisY * local.x + node.axisX * local.y) * reach +
                envelope.waveOffsetY(spec, width, height, homeX[index], waveTravel)
        }
    }

    /**
     * Each node's presence and hot weight under the current spec and surface. True if any node joined
     * or left the mesh.
     */
    private fun updatePresences(width: Float, height: Float): Boolean {
        evaluateShape(shape, level = 0, width = width, height = height, out = presences)
        var changed = false
        for (index in 0 until count) {
            val presence = presences[index] * borderFactor(index, width, height)
            presences[index] = presence
            hotWeights[index] = hotWeight(index)
            val joinThreshold = if (spec.edgeRule == EdgeRule.FixedTriangulation) PRESENT_THRESHOLD else PRESENT_JOIN_THRESHOLD
            val present = presence >= if (presentMask[index]) PRESENT_THRESHOLD else joinThreshold
            if (present != presentMask[index]) {
                presentMask[index] = present
                meshMembershipChanges++
                changed = true
            }
        }
        return changed
    }

    /**
     * How hot node [index] is. Mid-morph a node that leaves or arrives changes heat along with its own
     * presence, so a node never flares up on its way out; every other node follows the interpolated share.
     */
    private fun hotWeight(index: Int): Float {
        val morph = (shape as? LayerShape.Morph)?.takeIf { it.fraction > 0f && it.fraction < 1f }
        val switchAt = switchFractions[index]
        if (morph == null) return rollWeight(nodes[index].hotRoll, spec.hotNodeShare)
        if (switchAt.isNaN()) {
            val spark = cometTracks.topLevel?.sparkAt(index, nodes[index].hotRoll, morph.fraction) ?: 0f
            return max(rollWeight(nodes[index].hotRoll, spec.hotNodeShare), spark)
        }
        val startHot = rollWeight(nodes[index].hotRoll, morph.from.nearestSpec.hotNodeShare)
        val endHot = rollWeight(nodes[index].hotRoll, morph.to.nearestSpec.hotNodeShare)
        return lerp(startHot, endHot, switchProgress(switchAt, morph.fraction))
    }

    /** 1 for a node whose home is on the surface or near it, fading to 0 as it lies further outside. */
    private fun borderFactor(index: Int, width: Float, height: Float): Float {
        val outside = max(max(-homeX[index], homeX[index] - width), max(-homeY[index], homeY[index] - height))
        return 1f - smoothStep(BORDER_FADE_START_CELLS * cell, BORDER_FADE_END_CELLS * cell, outside)
    }

    /** Every node's presence under [shape] into [out], border fade aside; [level] is the morph nesting depth. */
    private fun evaluateShape(shape: LayerShape, level: Int, width: Float, height: Float, out: FloatArray) {
        when (shape) {
            is LayerShape.Of -> for (index in 0 until count) {
                val target = envelope.densityAt(shape.spec, width, height, homeX[index], homeY[index]) * shape.spec.fillChance
                out[index] = rollWeight(nodes[index].presenceRoll, target)
            }
            is LayerShape.Morph -> when {
                shape.fraction <= 0f -> evaluateShape(shape.from, level, width, height, out)
                shape.fraction >= 1f -> evaluateShape(shape.to, level, width, height, out)
                else -> {
                    val start = scratch(level, 0)
                    val end = scratch(level, 1)
                    evaluateShape(shape.from, level + 1, width, height, start)
                    evaluateShape(shape.to, level + 1, width, height, end)
                    blendEnds(
                        morph = shape,
                        comets = cometTrackFor(shape, level, width, height),
                        closenessInto = scratch(level, 2),
                        closenessOutOf = scratch(level, 3),
                        start = start,
                        end = end,
                        width = width,
                        height = height,
                        out = out,
                        switches = switchFractions.takeIf { level == 0 },
                    )
                }
            }
        }
    }

    /**
     * Each node's presence part way through [morph], from its presences at the two ends. A node that
     * changes fades over [MORPH_FADE_WINDOW] around its own switch fraction, ranked across the nodes
     * moving the same way and roughened by the node's roll: leaving nodes by closeness to the shape
     * being morphed into, furthest first, arriving nodes by closeness to the shape being morphed out
     * of, nearest first. A uniform shape is equally close everywhere and ranks nothing, so a group
     * whose own shape is uniform ranks by the other shape instead, on the other group's scale: then
     * both groups switch together where the front passes, and a node's replacement edges arrive as
     * its old ones leave. The switch fraction for the reverse morph is one minus this one, so a scrub
     * back retraces the front exactly.
     *
     * A node the same at both ends takes the light of any [comets] passing it on top. Nodes that leave or
     * arrive never do: one of them caught by a comet would turn back, and the comet lights the gap
     * between the two shapes, where neither end has nodes, anyway.
     */
    private fun blendEnds(
        morph: LayerShape.Morph,
        comets: CometTrack,
        closenessInto: FloatArray,
        closenessOutOf: FloatArray,
        start: FloatArray,
        end: FloatArray,
        width: Float,
        height: Float,
        out: FloatArray,
        switches: FloatArray?,
    ) {
        val intoSpec = morph.to.nearestSpec
        val outOfSpec = morph.from.nearestSpec
        val leavingInto = ClosenessRange()
        val leavingOutOf = ClosenessRange()
        val arrivingInto = ClosenessRange()
        val arrivingOutOf = ClosenessRange()
        for (index in 0 until count) {
            val change = end[index] - start[index]
            if (abs(change) <= MORPH_CHANGE_EPSILON) continue
            closenessInto[index] = envelope.closenessAt(intoSpec, width, height, homeX[index], homeY[index])
            closenessOutOf[index] = envelope.closenessAt(outOfSpec, width, height, homeX[index], homeY[index])
            if (change < 0f) {
                leavingInto.include(closenessInto[index])
                leavingOutOf.include(closenessOutOf[index])
            } else {
                arrivingInto.include(closenessInto[index])
                arrivingOutOf.include(closenessOutOf[index])
            }
        }
        for (index in 0 until count) {
            val change = end[index] - start[index]
            if (abs(change) <= MORPH_CHANGE_EPSILON) {
                out[index] = max(lerp(start[index], end[index], morph.fraction), comets.presenceAt(index, morph.fraction))
                switches?.set(index, Float.NaN)
                continue
            }
            val roll = nodes[index].morphRoll
            val jitter = nodes[index].frontNoise * MORPH_FRONT_JITTER
            val switchAt = if (change < 0f) {
                when {
                    leavingInto.ranks -> leavingInto.rank(closenessInto[index]) + jitter
                    leavingOutOf.ranks -> 1f - arrivingOutOf.or(leavingOutOf).rank(closenessOutOf[index]) - jitter
                    else -> roll
                }
            } else {
                when {
                    arrivingOutOf.ranks -> 1f - arrivingOutOf.rank(closenessOutOf[index]) - jitter
                    arrivingInto.ranks -> leavingInto.or(arrivingInto).rank(closenessInto[index]) + jitter
                    else -> 1f - roll
                }
            }
            if (switches != null) {
                switches[index] = switchAt.coerceIn(0f, 1f)
                switchArrivals[index] = change > 0f
            }
            out[index] = start[index] + change * switchProgress(switchAt, morph.fraction)
        }
    }

    private fun cometTrackFor(morph: LayerShape.Morph, level: Int, width: Float, height: Float): CometTrack =
        cometTracks.forMorph(morph, level, width, height, nodes, homeX, homeY)

    /**
     * The mesh at each end of [morph] and the union [LayerField.topology] holds while it runs, rebuilt
     * only when the ends or the surface change, never as the fraction moves. Each end is settled exactly
     * as a still frame of it would be, so the mesh meets the still mesh at both ends. Nodes only a comet
     * lights get their edges from a third mesh over every node either end or a comet shows.
     */
    private fun morphMeshFor(morph: LayerShape.Morph, width: Float, height: Float): MorphMesh {
        morphMesh?.takeIf { it.isFor(morph, width, height, count) }?.let { return it }
        val startPresences = FloatArray(count) { scratch(0, 0)[it] * borderFactor(it, width, height) }
        val endPresences = FloatArray(count) { scratch(0, 1)[it] * borderFactor(it, width, height) }
        val startMask = BooleanArray(count) { startPresences[it] >= PRESENT_THRESHOLD }
        val endMask = BooleanArray(count) { endPresences[it] >= PRESENT_THRESHOLD }
        val start = settleTopology(homeX, homeY, startMask, startPresences, morph.from.nearestSpec)
        val end = settleTopology(homeX, homeY, endMask, endPresences, morph.to.nearestSpec)
        val comets = cometTrackFor(morph, level = 0, width = width, height = height)
        val transitMask = BooleanArray(count) { index ->
            !startMask[index] && !endMask[index] && comets.peaks[index] * borderFactor(index, width, height) >= PRESENT_THRESHOLD
        }
        val transit = if (transitMask.any { it }) {
            val everyMask = BooleanArray(count) { startMask[it] || endMask[it] || transitMask[it] }
            val everyPresences = FloatArray(count) { index ->
                max(max(startPresences[index], endPresences[index]), comets.peaks[index] * borderFactor(index, width, height))
            }
            // Never strict: a comet's patch lights and darkens node by node, and with only triangle edges
            // its nodes run short of visible ones and blink.
            TransitMesh(settleTopology(homeX, homeY, everyMask, everyPresences, morph.to.nearestSpec.copy(strictTriangles = false)), transitMask)
        } else {
            null
        }
        val switches = NodeSwitches(switchFractions.copyOf(count), switchArrivals.copyOf(count))
        return MorphMesh(morph.from, morph.to, width, height, count, start, end, transit, switches).also { morphMesh = it }
    }

    /** Scales each drawn edge by how far it is through its own switch, for an edge only one end of the morph has. */
    private fun applyMorphEdgeWeights(fraction: Float) {
        val mesh = morphMesh ?: return
        for (edge in 0 until edgeCount) {
            val key = edgeKeys[edge]
            val weight = mesh.leavingEdgeSwitches[key]?.let { switchAt -> 1f - switchProgress(switchAt, fraction) }
                ?: mesh.arrivingEdgeSwitches[key]?.let { switchAt -> switchProgress(switchAt, fraction) }
                ?: 1f
            fadingEdges[key]?.morphWeight = weight
            edgeFades[edge] *= weight
        }
    }

    private fun switchProgress(switchAt: Float, fraction: Float): Float {
        val halfWindow = MORPH_FADE_WINDOW / 2f
        val center = halfWindow + switchAt.coerceIn(0f, 1f) * (1f - MORPH_FADE_WINDOW)
        return smoothStep(center - halfWindow, center + halfWindow, fraction)
    }

    private fun scratch(level: Int, slot: Int): FloatArray {
        val position = level * SCRATCH_SLOTS_PER_LEVEL + slot
        while (shapeScratch.size <= position) shapeScratch += FloatArray(0)
        if (shapeScratch[position].size < count) shapeScratch[position] = FloatArray(count)
        return shapeScratch[position]
    }

    /** True if any value the edge filters use changed since the last settle. */
    private fun updateSettledFilters(): Boolean {
        val filters = floatArrayOf(spec.maxEdgeFactor, spec.minAngleDegrees, spec.edgeKeepChance, spec.repairReach)
        if (filters.contentEquals(settledFilters)) return false
        filters.copyInto(settledFilters)
        return true
    }

    /**
     * The rule's filtered edges over the nodes present enough to join the mesh, then [enforceMinDegree]
     * with the rule's repair candidates. Absent nodes get no edges, so it prunes them along the way.
     *
     * Under [EdgeRule.FixedTriangulation], which settles once per shape rather than every frame, the
     * mesh also keeps [enforceMinDegree]'s connectivity rules, and edges drawn too faint to see (see
     * [VISIBLE_EDGE_MARGIN]) don't count, so the rules hold for the graph as seen.
     */
    private fun settleTopology(xs: FloatArray, ys: FloatArray, mask: BooleanArray, presence: FloatArray, filters: NetworkGraphLayerSpec): MeshTopology =
        enforceOnVisibleEdges(
            count = count,
            xs = xs,
            ys = ys,
            edges = ruleEdges(spec.edgeRule, mask, xs, ys, filters, cell, seed),
            presence = presence,
            filters = filters,
            cell = cell,
            // A mesh rebuilt every frame can't afford the connectivity rules, nor needs edge visibility without them.
            keepConnectivity = spec.edgeRule == EdgeRule.FixedTriangulation,
        ) { nodes[it].depth }

    private fun ensureEdgeCapacity(capacity: Int) {
        if (edgeKeys.size >= capacity) return
        edgeKeys = edgeKeys.copyOf(max(capacity, edgeKeys.size * 2))
        edgeFades = edgeFades.copyOf(edgeKeys.size)
        rawEdgeAlphas = rawEdgeAlphas.copyOf(edgeKeys.size)
        edgeAlphas = edgeAlphas.copyOf(edgeKeys.size)
    }

    /**
     * Edges entering [topology] fade in, edges leaving it fade out; the first frame starts fully drawn,
     * and so does every edge of a running morph's mesh, which the morph fades itself.
     */
    private fun updateFadingEdges(stepSeconds: Float, isFirstFrame: Boolean, morphing: Boolean) {
        for (edge in fadingEdges.values) {
            edge.present = false
            // A morph that stopped leaves each edge where it was, rather than handing it back its full fade.
            if (!morphing) {
                edge.alpha *= edge.morphWeight
                edge.morphWeight = 1f
            }
        }
        for (key in topology.edges) {
            // Mid-morph, the morph's own switch fades each edge (see [applyMorphEdgeWeights]); a second,
            // timed fade on top would hold back every edge the morph brings in for its first moments.
            val edge = fadingEdges.getOrPut(key) { FadingEdge(alpha = if (isFirstFrame || morphing) 1f else 0f) }
            if (morphing) edge.alpha = 1f
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
     * Each edge's and each dot's alpha, holding both invariants whatever the falloff, depth, fade,
     * presence or alpha settings do:
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
        visibleNodeCount = (0 until count).count { nodeAlphas[it] >= NODE_VISIBLE_ALPHA }
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
            rawEdgeAlphas[edge] = (spec.edgeAlpha * spec.alpha * lengthFactor * depthFactor).coerceIn(0f, 1f)
        }
        if (spec.strictTriangles) fillDrawnTriangles()
        for (edge in 0 until edgeCount) {
            val alpha = rawEdgeAlphas[edge] * edgeFades[edge]
            rawEdgeAlphas[edge] = alpha
            edgeAlphas[edge] = alpha
        }
    }

    /**
     * [fillTriangles] over the drawn edges, finding their triangles only when the drawn edges change,
     * which for a mesh rebuilt every frame is still only when an edge flips.
     */
    private fun fillDrawnTriangles() {
        if (!triangleCacheKeys.contentEquals(edgeKeys.copyOf(edgeCount))) {
            triangleCacheKeys = edgeKeys.copyOf(edgeCount)
            triangleCache = edgeTriangles(edgeKeys, edgeCount)
        }
        fillTriangles(triangleCache, rawEdgeAlphas)
    }

    /**
     * Node [index]'s dot alpha with two healthy edges, scaled by its presence. A fully present dot is
     * never under the edge alpha nominal at its depth, so with healthy edges the edge limit never bites.
     */
    private fun dotBaseAlpha(index: Int): Float {
        val baseAlpha = lerp(max(NODE_ALPHA, spec.edgeAlpha), 1f, hotWeights[index])
        return (baseAlpha * spec.alpha * depthAlpha(index) * presences[index]).coerceIn(0f, 1f)
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

    /**
     * Fades each dot with its second-brightest edge, never raising one; true if any dot dropped
     * noticeably. The full-alpha point scales with presence, so a half-present node next to fully
     * present ones settles at half alpha instead of sinking to nothing over the rounds.
     */
    private fun lowerDotsToTheirEdges(): Boolean {
        val nominalEdgeAlpha = spec.edgeAlpha * spec.alpha
        var lowered = false
        for (index in 0 until count) {
            val second = secondEdgeAlphas[index]
            val fullAt = max(NODE_FULL_AT_EDGE_SHARE * nominalEdgeAlpha * depthAlpha(index) * presences[index], 2f * EDGE_VISIBLE_ALPHA)
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

    private fun depthAlpha(index: Int): Float = 1f - spec.depthDimming * (1f - nodes[index].depth)

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

    /** Halos are exempt from the alpha rule but still fade with their node's presence and edges. */
    private fun DrawScope.drawGlows(halos: HaloBrushCache) {
        if (spec.glowStrength <= 0f) return
        for (index in 0 until count) {
            if (hotWeights[index] <= 0f) continue
            val radius = spec.glowRadiusDp * pxPerDp * sizeFactor(index)
            val alpha = spec.glowStrength * spec.alpha * depthAlpha(index) * glowFactors[index] * hotWeights[index] * presences[index]
            drawHalo(halos, positionsX[index], positionsY[index], radius, alpha)
        }
    }

    /** Drawn last and with [BlendMode.Src], so a dot replaces the edges and halo under it rather than summing with them. */
    private fun DrawScope.drawCores(color: Color) {
        for (index in 0 until count) {
            if (nodeAlphas[index] < EDGE_MIN_VISIBLE_ALPHA) continue
            val depthShrink = 1f - DEPTH_SIZE_SHRINK * spec.depthDimming * (1f - nodes[index].depth)
            val hotScale = lerp(1f, HOT_NODE_SCALE, hotWeights[index])
            drawCircle(
                color = color.copy(alpha = nodeAlphas[index]),
                radius = spec.nodeSizeDp * pxPerDp * sizeFactor(index) * hotScale * depthShrink,
                center = Offset(positionsX[index], positionsY[index]),
                blendMode = BlendMode.Src,
            )
        }
    }

    private fun sizeFactor(index: Int): Float = max(NODE_SIZE_FLOOR, 1f + spec.nodeSizeVariance * (nodes[index].sizeRoll * 2f - 1f))

    /**
     * Rings in red every node drawn visibly with fewer than two visible edges, every node fainter than an
     * edge it anchors as drawn this frame, and every cut node of the drawn graph; in amber, every node of
     * a drawn graph smaller than [MIN_GRAPH_NODES]. Still frames should have none; a morph may pass
     * through some.
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
            drawViolationRing(index, ViolationColor, stroke)
        }
        val connectivity = drawnConnectivityViolations()
        connectivity.smallGraphNodes.forEach { drawViolationRing(it, SmallGraphColor, stroke) }
        connectivity.cutNodes.forEach { drawViolationRing(it, ViolationColor, stroke) }
    }

    private fun DrawScope.drawViolationRing(index: Int, color: Color, stroke: Stroke) {
        drawCircle(color = color, radius = VIOLATION_RING_RADIUS_DP * pxPerDp, center = Offset(positionsX[index], positionsY[index]), style = stroke)
    }

    /** The brush's gradient is centered on the origin, so the canvas moves to the point instead. */
    private fun DrawScope.drawHalo(halos: HaloBrushCache, x: Float, y: Float, radius: Float, alpha: Float) {
        if (alpha <= 0f || radius <= 0f) return
        translate(left = x, top = y) {
            drawCircle(brush = halos.brushFor(radius), radius = radius, center = Offset.Zero, alpha = alpha.coerceAtMost(1f))
        }
    }
}

/**
 * 0 while [share] is at or under [roll], 1 once it is [ROLL_BAND] past it, smooth in between. The
 * band shrinks the roll's range so that a share of 1 is fully on for every roll, and 0 fully off.
 */
private fun rollWeight(roll: Float, share: Float): Float {
    val start = roll * (1f - ROLL_BAND)
    return smoothStep(start, start + ROLL_BAND, share)
}

/** A stable seed for one grid cell, so a cell's node is the same whenever and in whatever order it is created. */
private fun cellSeed(column: Int, row: Int, seed: Int): Int {
    var hash = column * 73_856_093 xor row * 19_349_663 xor seed * 83_492_791
    hash = hash xor (hash ushr 13)
    hash *= 0x5BD1E995
    return hash xor (hash ushr 15)
}

/** Each node's switch fraction in one morph (NaN for a node the same at both ends) and whether it arrives or leaves. */
private class NodeSwitches(private val fractions: FloatArray, val arriving: BooleanArray) {
    fun at(node: Int): Float? = fractions[node].takeIf { !it.isNaN() }
}

/** The span of closeness values one group of a morph's nodes covers, and each value's place in it. */
private class ClosenessRange {
    private var low = Float.MAX_VALUE
    private var high = -Float.MAX_VALUE

    /** True when the values spread enough to put nodes in an order. */
    val ranks: Boolean get() = high - low >= MORPH_MIN_CLOSENESS_SPREAD

    fun include(value: Float) {
        low = min(low, value)
        high = max(high, value)
    }

    /** [value]'s place from 0 (the lowest) to 1 (the highest); outside the range it lands past either end. */
    fun rank(value: Float): Float = (value - low) / (high - low)

    /** This range if it [ranks], else [fallback]. */
    fun or(fallback: ClosenessRange): ClosenessRange = if (ranks) this else fallback
}

/** [morphWeight] is the share of [alpha] a running morph leaves the edge; it folds into [alpha] once the morph stops. */
private class FadingEdge(var alpha: Float, var present: Boolean = true, var morphWeight: Float = 1f)

/**
 * The [EdgeRule.FixedTriangulation] mesh of each end of one morph, and when each edge only one end has
 * switches. An edge leaves with the first of its nodes to leave and arrives with the last of its nodes
 * to arrive. An edge with no node moving its way (a min-degree repair between nodes that stay or
 * leave, or one a neighbour's arrival flipped) switches with the nodes changing around it, arriving
 * with the first of them and leaving with the last, so a node losing its edges to a leaving neighbour
 * gains its replacements at the same moment instead of going dark in between.
 * Reversing the morph mirrors every switch, so a scrub back retraces it.
 *
 * With comets, the edges of [transit] that touch a node only a comet lights join too, at full weight:
 * such an edge shows only while both its dots do, and a comet node's dot only while the comet lights it.
 */
private class MorphMesh(
    private val from: LayerShape,
    private val to: LayerShape,
    private val width: Float,
    private val height: Float,
    private val nodeCount: Int,
    start: MeshTopology,
    end: MeshTopology,
    transit: TransitMesh?,
    switches: NodeSwitches,
) {
    val union: MeshTopology
    val leavingEdgeSwitches: Map<Long, Float>
    val arrivingEdgeSwitches: Map<Long, Float>

    init {
        val startEdges = start.edges.toHashSet()
        val endEdges = end.edges.toHashSet()
        val alive = BooleanArray(nodeCount) { start.alive[it] || end.alive[it] }
        val transitEdges = transit?.let { connectTransit(it, startEdges, endEdges, alive, switches) }.orEmpty()
        val unionEdges = (startEdges + endEdges + transitEdges).toLongArray()
        val neighbours = Array(nodeCount) { ArrayList<Int>() }
        for (key in unionEdges) {
            neighbours[edgeStart(key)] += edgeEnd(key)
            neighbours[edgeEnd(key)] += edgeStart(key)
        }
        // The switches of the changing nodes at or around an edge's two ends.
        fun switchesAround(key: Long): List<Float> = listOf(edgeStart(key), edgeEnd(key))
            .flatMap { node -> neighbours[node] + node }
            .mapNotNull(switches::at)

        // The switches of the edge's own two nodes, of those moving the way the edge does.
        fun ownSwitches(key: Long, arriving: Boolean): List<Float> = listOf(edgeStart(key), edgeEnd(key))
            .filter { node -> switches.arriving[node] == arriving }
            .mapNotNull(switches::at)
        leavingEdgeSwitches = (startEdges - endEdges).associateWith { key ->
            ownSwitches(key, arriving = false).minOrNull() ?: switchesAround(key).maxOrNull() ?: NEUTRAL_SWITCH
        }
        arrivingEdgeSwitches = (endEdges - startEdges).associateWith { key ->
            ownSwitches(key, arriving = true).maxOrNull() ?: switchesAround(key).minOrNull() ?: NEUTRAL_SWITCH
        }
        union = MeshTopology(
            edges = unionEdges,
            alive = alive,
            repairedEdgeCount = max(start.repairedEdgeCount, end.repairedEdgeCount),
            prunedNodeCount = min(start.prunedNodeCount, end.prunedNodeCount),
            converged = start.converged && end.converged,
        )
    }

    fun isFor(morph: LayerShape.Morph, width: Float, height: Float, nodeCount: Int): Boolean =
        morph.from == from && morph.to == to && width == this.width && height == this.height && nodeCount == this.nodeCount

    /**
     * [transit]'s edges that touch a comet node and join it to another or to a node both ends keep, marking each comet
     * node that ends up with two of them alive in [alive]. A comet node left with fewer, because its
     * other edges ran to nodes neither end keeps, is dropped with its edges, which can leave a neighbour
     * short in turn, so this repeats until nothing changes.
     */
    private fun connectTransit(transit: TransitMesh, startEdges: Set<Long>, endEdges: Set<Long>, alive: BooleanArray, switches: NodeSwitches): List<Long> {
        val candidates = BooleanArray(nodeCount) { transit.nodes[it] && transit.topology.alive[it] }
        // Nodes a comet edge may reach: comet nodes, and nodes both ends keep; never one that leaves or arrives, which it would light up out of turn.
        val reachable = BooleanArray(nodeCount) { candidates[it] || (alive[it] && switches.at(it) == null) }
        var edges = transit.topology.edges.filter { key ->
            val first = edgeStart(key)
            val second = edgeEnd(key)
            (candidates[first] || candidates[second]) && key !in startEdges && key !in endEdges && reachable[first] && reachable[second]
        }
        do {
            val short = shortCandidates(edges, candidates)
            short.forEach { candidates[it] = false }
            edges = edges.filter { key -> keeps(edgeStart(key), transit, candidates) && keeps(edgeEnd(key), transit, candidates) }
        } while (short.isNotEmpty())
        for (node in 0 until nodeCount) if (candidates[node]) alive[node] = true
        return edges
    }

    /** The comet nodes still in [candidates] with fewer than two of [edges]. */
    private fun shortCandidates(edges: List<Long>, candidates: BooleanArray): List<Int> {
        val degrees = IntArray(nodeCount)
        for (key in edges) {
            degrees[edgeStart(key)]++
            degrees[edgeEnd(key)]++
        }
        return (0 until nodeCount).filter { candidates[it] && degrees[it] < 2 }
    }

    /** False for a comet node already dropped from [candidates]. */
    private fun keeps(node: Int, transit: TransitMesh, candidates: BooleanArray): Boolean = !transit.nodes[node] || candidates[node]

    private companion object {
        /** The switch of an edge with no changing node anywhere near it, which the morph never actually reaches. */
        const val NEUTRAL_SWITCH = 0.5f
    }
}

/** Every present pair within [reach] of each other that passes its [keepChance]. */
private fun proximityEdges(presentIndices: IntArray, xs: FloatArray, ys: FloatArray, reach: Float, keepChance: Float, seed: Int): LongArray {
    val reachSquared = reach * reach
    val keys = ArrayList<Long>()
    for (firstSlot in presentIndices.indices) {
        val first = presentIndices[firstSlot]
        for (secondSlot in firstSlot + 1 until presentIndices.size) {
            val second = presentIndices[secondSlot]
            val deltaX = xs[second] - xs[first]
            val deltaY = ys[second] - ys[first]
            if (deltaX * deltaX + deltaY * deltaY >= reachSquared) continue
            if (pairRandom(first, second, seed) >= keepChance) continue
            keys += edgeKey(first, second)
        }
    }
    return keys.toLongArray()
}

/**
 * Each morph nesting level's [CometTrack], built on first use and kept until that level's morph or the
 * surface changes. [topLevel] is the drawn morph's own.
 */
private class CometTracks(private val envelope: NetworkGraphEnvelope) {
    private val byLevel = ArrayList<CometTrack?>()

    val topLevel: CometTrack? get() = byLevel.getOrNull(0)

    @Suppress("LongParameterList") // The morph, the surface, and the node pool its comets light.
    fun forMorph(morph: LayerShape.Morph, level: Int, width: Float, height: Float, nodes: List<NodeTraits>, homeX: FloatArray, homeY: FloatArray): CometTrack {
        while (byLevel.size <= level) byLevel += null
        byLevel[level]?.takeIf { it.isFor(morph, width, height, homeX.size) }?.let { return it }
        val fromSpec = morph.from.nearestSpec
        val toSpec = morph.to.nearestSpec
        val strength = (fromSpec.cometStrength + toSpec.cometStrength) / 2f
        val comets = if (strength > 0f) {
            cometsBetween(envelope.standIns(fromSpec, width, height), envelope.standIns(toSpec, width, height), width, height)
        } else {
            emptyList()
        }
        val fillChance = (fromSpec.fillChance + toSpec.fillChance) / 2f
        val spark = fromSpec.cometSpark || toSpec.cometSpark
        return CometTrack(morph.from, morph.to, width, height, comets, strength, fillChance, spark, nodes, homeX, homeY)
            .also { byLevel[level] = it }
    }
}

/** The mesh over every node a morph's ends or comets show, and which of those nodes only a comet lights. */
private class TransitMesh(val topology: MeshTopology, val nodes: BooleanArray)

/**
 * One morph's [comets], sampled per node at [COMET_SAMPLES] steps of the fraction: [presenceAt] is a
 * node's presence under them at a fraction, rising then falling once. [peaks] are each node's brightest
 * presence; a node no comet reaches has a peak of 0.
 *
 * A comet lights a node once its density there, times [strength], passes [COMET_LIGHT_LEVEL], so the
 * patch it lights grows out of the shape it leaves, travels and shrinks into the shape it reaches. The
 * nodes [fillChance] leaves out of every shape stay out of every comet too, so its holes never move.
 */
@Suppress("LongParameterList") // One morph's ends and comets, and the node pool they light.
private class CometTrack(
    private val from: LayerShape,
    private val to: LayerShape,
    private val width: Float,
    private val height: Float,
    comets: List<Comet>,
    strength: Float,
    fillChance: Float,
    private val spark: Boolean,
    nodes: List<NodeTraits>,
    homeX: FloatArray,
    homeY: FloatArray,
) {
    private val nodeCount = homeX.size
    val cometCount = comets.size
    private val samples = FloatArray(nodeCount * (COMET_SAMPLES + 1))
    val peaks = FloatArray(nodeCount)
    private val peakFractions = FloatArray(nodeCount)

    init {
        if (comets.isNotEmpty()) {
            val curve = FloatArray(COMET_SAMPLES + 1)
            for (index in 0 until nodeCount) {
                val membership = rollWeight(nodes[index].presenceRoll, fillChance)
                val lightLevel = COMET_LIGHT_LEVEL + nodes[index].frontNoise * COMET_LIGHT_JITTER
                for (step in 0..COMET_SAMPLES) {
                    val density = comets.maxOf { it.densityAt(homeX[index], homeY[index], step / COMET_SAMPLES.toFloat()) }
                    curve[step] = membership * smoothStep(lightLevel - COMET_LIGHT_BAND / 2f, lightLevel + COMET_LIGHT_BAND / 2f, density * strength)
                }
                record(index, curve)
            }
        }
    }

    /** How hot [node] is from the comet passing it, under [NetworkGraphLayerSpec.cometSpark]: hot as the comet reaches it, cooling once it has passed. */
    fun sparkAt(node: Int, hotRoll: Float, fraction: Float): Float {
        if (!spark || peaks[node] <= 0f) return 0f
        val peakAt = peakFractions[node]
        return rollWeight(hotRoll, COMET_SPARK_SHARE) * (1f - smoothStep(peakAt - COMET_SPARK_LEAD, peakAt + COMET_SPARK_TRAIL, fraction))
    }

    fun presenceAt(node: Int, fraction: Float): Float {
        if (peaks[node] <= 0f) return 0f
        val position = fraction.coerceIn(0f, 1f) * COMET_SAMPLES
        val step = position.toInt().coerceAtMost(COMET_SAMPLES - 1)
        val offset = node * (COMET_SAMPLES + 1) + step
        return lerp(samples[offset], samples[offset + 1], position - step)
    }

    fun isFor(morph: LayerShape.Morph, width: Float, height: Float, nodeCount: Int): Boolean =
        morph.from == from && morph.to == to && width == this.width && height == this.height && nodeCount == this.nodeCount

    /**
     * Keeps node [index]'s [curve], lifted to the lowest curve that only rises then falls, so where two
     * comets pass one node in turn it lights once for both rather than flickering between them.
     */
    private fun record(index: Int, curve: FloatArray) {
        liftToSinglePeak(curve)
        curve.copyInto(samples, index * (COMET_SAMPLES + 1))
        val peakStep = curve.indices.maxBy { curve[it] }
        peaks[index] = curve[peakStep]
        peakFractions[index] = peakStep / COMET_SAMPLES.toFloat()
    }
}

/**
 * Raises [curve] in place to the lowest curve over it that only rises then falls: each value becomes
 * the lower of the highest value at or before it and the highest at or after it. Reversing the curve
 * reverses the result, so a morph played backwards lights the same nodes in mirror.
 */
private fun liftToSinglePeak(curve: FloatArray) {
    val risingMax = FloatArray(curve.size)
    var running = 0f
    for (step in curve.indices) {
        running = max(running, curve[step])
        risingMax[step] = running
    }
    running = 0f
    for (step in curve.indices.reversed()) {
        running = max(running, curve[step])
        curve[step] = min(risingMax[step], running)
    }
}

/** Everything about a node that never changes: its rest position, its motion, and the rolls its looks are decided against. */
private class NodeTraits(
    val homeX: Float,
    val homeY: Float,
    val axisX: Float,
    val axisY: Float,
    val drift: NodeDrift,
    /** The node's share of the layer's drift amplitude. */
    val driftScale: Float,
    val depth: Float,
    val pulsePhase: Float,
    val sizeRoll: Float,
    val hotRoll: Float,
    val presenceRoll: Float,
    /** When the node switches in a morph whose shapes put no nodes in order. */
    val morphRoll: Float,
    /** The layer's front noise at the node's cell, in -1..1: how far ahead of or behind a morph's front it switches. */
    val frontNoise: Float,
)

/** One axis of a node's wander: three sine octaves at non-rational ratios, so the path never retraces. */
private class DriftAxis(private val frequency: Float, private val coarsePhase: Float, private val mediumPhase: Float, private val finePhase: Float) {

    /** Displacement along this axis at drift time [clock], in -1..1. */
    fun offsetAt(clock: Float): Float =
        DRIFT_OCTAVE_WEIGHT_COARSE * sin(clock * frequency + coarsePhase) +
            DRIFT_OCTAVE_WEIGHT_MEDIUM * sin(clock * frequency * DRIFT_OCTAVE_RATIO_MEDIUM + mediumPhase) +
            DRIFT_OCTAVE_WEIGHT_FINE * sin(clock * frequency * DRIFT_OCTAVE_RATIO_FINE + finePhase)
}

/** A node's wander in its own rotated frame, as a fraction of its drift reach. */
private class NodeDrift(private val primary: DriftAxis, private val secondary: DriftAxis) {
    fun offsetAt(clock: Float): Offset = Offset(primary.offsetAt(clock), secondary.offsetAt(clock))
}

/**
 * The alpha the edge [key] is drawn at once fully faded in under [filters], at these positions and
 * [presence]s: what length falloff, depth and the layer's alpha leave of it, capped by the dimmer of
 * its two dots as that dot would fade with this edge (see [LayerField]'s updateAlphas).
 */
private fun restEdgeAlpha(key: Long, xs: FloatArray, ys: FloatArray, presence: FloatArray, filters: NetworkGraphLayerSpec, cell: Float, depthOf: (Int) -> Float): Float {
    val start = edgeStart(key)
    val end = edgeEnd(key)
    val length = sqrt((xs[end] - xs[start]).let { it * it } + (ys[end] - ys[start]).let { it * it })
    val lengthFactor = 1f - filters.lengthFalloff * (length / (filters.maxEdgeFactor * cell)).coerceIn(0f, 1f)
    val startDepth = 1f - filters.depthDimming * (1f - depthOf(start))
    val endDepth = 1f - filters.depthDimming * (1f - depthOf(end))
    val depthFactor = min(startDepth, endDepth)
    val edgeAlpha = filters.edgeAlpha * filters.alpha * lengthFactor * depthFactor
    // The dimmer dot, faded as lowerDotsToTheirEdges would fade it were this its second-brightest edge.
    val dimmerDot = min(startDepth * presence[start], endDepth * presence[end])
    val fullAt = max(NODE_FULL_AT_EDGE_SHARE * filters.edgeAlpha * filters.alpha * dimmerDot, 2f * EDGE_VISIBLE_ALPHA)
    val dotAlpha = max(NODE_ALPHA, filters.edgeAlpha) * filters.alpha * dimmerDot * smoothStep(EDGE_VISIBLE_ALPHA, fullAt, edgeAlpha)
    return min(edgeAlpha, dotAlpha)
}

/**
 * Strict meshes: raises each edge's alpha before fades to the dimmer of the other two edges of any
 * triangle it closes, so a long diagonal that length falloff would fade out of sight stays as visible
 * as its triangle and no drawn face reads as a polygon. Depth dims an edge by its dimmer dot, so only
 * length can leave an edge fainter than both other sides of its triangle; and each edge keeps its own
 * fade, so an edge fading in or out still does. [triangles] holds edge index triples into [alphas].
 */
private fun fillTriangles(triangles: IntArray, alphas: FloatArray) {
    var rounds = 0
    var raised = true
    while (raised && rounds < MAX_TRIANGLE_FILL_ROUNDS) {
        raised = false
        for (base in triangles.indices step 3) {
            val first = triangles[base]
            val second = triangles[base + 1]
            val third = triangles[base + 2]
            raised = alphas.raise(first, min(alphas[second], alphas[third])) || raised
            raised = alphas.raise(second, min(alphas[first], alphas[third])) || raised
            raised = alphas.raise(third, min(alphas[first], alphas[second])) || raised
        }
        rounds++
    }
}

private fun FloatArray.raise(index: Int, value: Float): Boolean {
    if (value <= this[index]) return false
    this[index] = value
    return true
}

/** The triangles of the first [edgeCount] of [edgeKeys], as triples of their indices there, each once. */
private fun edgeTriangles(edgeKeys: LongArray, edgeCount: Int): IntArray {
    val indexOf = HashMap<Long, Int>(edgeCount * 2)
    val neighbours = HashMap<Int, MutableList<Int>>()
    for (edge in 0 until edgeCount) {
        val key = edgeKeys[edge]
        indexOf[key] = edge
        neighbours.getOrPut(edgeStart(key)) { ArrayList() } += edgeEnd(key)
        neighbours.getOrPut(edgeEnd(key)) { ArrayList() } += edgeStart(key)
    }
    val triangles = ArrayList<Int>()
    for (edge in 0 until edgeCount) {
        val key = edgeKeys[edge]
        // From the edge between each triangle's two lowest corners only; keys hold the lower end first.
        for (apex in neighbours[edgeStart(key)].orEmpty()) {
            if (apex <= edgeEnd(key)) continue
            val toEnd = indexOf[edgeKey(edgeEnd(key), apex)] ?: continue
            triangles += edge
            triangles += indexOf.getValue(edgeKey(edgeStart(key), apex))
            triangles += toEnd
        }
    }
    return triangles.toIntArray()
}

/** [restAlphas] with each edge of [triangles] lifted to the dimmer of its triangle's other two, as [fillTriangles] does. */
private fun liftedByTriangles(restAlphas: Map<Long, Float>, triangles: IntArray): Map<Long, Float> {
    val lifted = HashMap(restAlphas)
    for (base in triangles.indices step 3) {
        val sides = longArrayOf(
            edgeKey(triangles[base], triangles[base + 1]),
            edgeKey(triangles[base + 1], triangles[base + 2]),
            edgeKey(triangles[base + 2], triangles[base]),
        )
        for (side in 0 until 3) {
            val others = min(restAlphas.getValue(sides[(side + 1) % 3]), restAlphas.getValue(sides[(side + 2) % 3]))
            if (others > lifted.getValue(sides[side])) lifted[sides[side]] = others
        }
    }
    return lifted
}

/** [edgeRule]'s filtered edges over the nodes in [mask] under [filters], with its repair candidates. */
private fun ruleEdges(edgeRule: EdgeRule, mask: BooleanArray, xs: FloatArray, ys: FloatArray, filters: NetworkGraphLayerSpec, cell: Float, seed: Int): RuleEdges {
    val presentIndices = mask.indices.filter { mask[it] }.toIntArray()
    val presentX = FloatArray(presentIndices.size) { xs[presentIndices[it]] }
    val presentY = FloatArray(presentIndices.size) { ys[presentIndices[it]] }
    if (edgeRule == EdgeRule.Proximity) {
        val nearest = nearestNeighbourEdgeKeys(presentIndices.size, presentX, presentY, PROXIMITY_REPAIR_NEIGHBOURS)
        return RuleEdges(
            base = proximityEdges(presentIndices, xs, ys, reach = filters.maxEdgeFactor * cell, keepChance = filters.edgeKeepChance, seed = seed),
            candidates = LongArray(nearest.size) { edge -> edgeKey(presentIndices[edgeStart(nearest[edge])], presentIndices[edgeEnd(nearest[edge])]) },
            triangles = null,
        )
    }
    val triangles = Delaunay.triangulate(presentX, presentY, presentIndices.size)
    // Back to pool indices, so each pair's keep chance stays the same whoever else is present.
    for (corner in triangles.indices) triangles[corner] = presentIndices[triangles[corner]]
    val base = meshEdgeKeys(
        triangles = triangles,
        xs = xs,
        ys = ys,
        minAngleDegrees = filters.minAngleDegrees,
        maxLength = filters.maxEdgeFactor * cell,
        keepChance = filters.edgeKeepChance,
        seed = seed,
    )
    return RuleEdges(base, candidates = triangleEdgeKeys(triangles), triangles = triangles)
}

/** An edge rule's output for [enforceMinDegree]: its filtered edges, repair candidates and, for Delaunay rules, triangles. */
private class RuleEdges(val base: LongArray, val candidates: LongArray, val triangles: IntArray?)

/**
 * [enforceMinDegree] over [edges], keeping its connectivity rules when [keepConnectivity] and then
 * counting only edges drawn visibly at rest (see [VISIBLE_EDGE_MARGIN] and [restEdgeAlpha]).
 */
private fun enforceOnVisibleEdges(
    count: Int,
    xs: FloatArray,
    ys: FloatArray,
    edges: RuleEdges,
    presence: FloatArray,
    filters: NetworkGraphLayerSpec,
    cell: Float,
    keepConnectivity: Boolean,
    depthOf: (Int) -> Float,
): MeshTopology {
    val visibleAlpha = EDGE_VISIBLE_ALPHA * VISIBLE_EDGE_MARGIN
    val restAlphaOf = { key: Long -> restEdgeAlpha(key, xs, ys, presence, filters, cell, depthOf) }
    val settle = { visible: (Long) -> Boolean ->
        enforceMinDegree(
            count = count,
            xs = xs,
            ys = ys,
            baseEdges = edges.base.filter(visible).toLongArray(),
            candidateEdges = edges.candidates.filter(visible).toLongArray(),
            maxRepairLength = filters.repairReach * filters.maxEdgeFactor * cell,
            strictTriangles = filters.strictTriangles,
            triangles = edges.triangles,
            keepConnectivity = keepConnectivity,
        )
    }
    if (!keepConnectivity) return settle { true }
    if (!filters.strictTriangles) return settle { key -> restAlphaOf(key) >= visibleAlpha }
    val restAlphas = HashMap<Long, Float>()
    for (key in edges.candidates) restAlphas[key] = restAlphaOf(key)
    for (key in edges.base) restAlphas.getOrPut(key) { restAlphaOf(key) }
    // Strict meshes lift a faint edge to its triangle's other sides (see [fillTriangles]). Which
    // triangles the mesh keeps is known only once it settles, so this first guess counts every
    // triangle, and an edge the settled mesh leaves faint after all is dropped and the mesh re-settled.
    val liftedAlphas = edges.triangles?.let { liftedByTriangles(restAlphas, it) } ?: restAlphas
    val faint = HashSet<Long>()
    var passes = 0
    while (true) {
        val topology = settle { key -> key !in faint && liftedAlphas.getValue(key) >= visibleAlpha }
        if (++passes == MAX_VISIBILITY_PASSES) return topology
        val settled = liftedByTriangles(restAlphas, meshTriangles(count, topology.edges))
        val newlyFaint = topology.edges.filter { settled.getValue(it) < visibleAlpha }
        if (newlyFaint.isEmpty()) return topology
        faint += newlyFaint
    }
}
