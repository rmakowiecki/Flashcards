package com.rossomak.flashcards.feature.debug.networkgraph

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt
import org.junit.Test

/**
 * Morphs and resizes on real [LayerField]s: both invariants must hold on every frame part way through
 * a morph, not only at its ends, and a resize must never move a node that already exists. A morph must
 * also be stable: every node switches at most once, in order of distance from the shape, and a scrub
 * back retraces it.
 */
class NetworkGraphMorphTest {

    private val family = NetworkGraphPresets.morphFamily
    private val surfaces = listOf(WIDE_STRIP_WIDTH to WIDE_STRIP_HEIGHT, TALL_HERO_WIDTH to TALL_HERO_HEIGHT)
    private val fractions = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)

    @Test
    fun `every morph family pair keeps the invariants at every fraction`() {
        val violations = family.flatMap { from ->
            family.filter { it != from }.flatMap { to ->
                surfaces.flatMap { (width, height) ->
                    val run = MorphRun(from, to, width, height)
                    fractions.flatMap { fraction -> run.frames("${from.name} → ${to.name} at $fraction", fraction, frameCount = 2) }
                }
            }
        }

        violations.shouldBeEmpty()
    }

    @Test
    fun `a scrub forward and back keeps the invariants every frame for every edge rule`() {
        val scrub = (0..SCRUB_STEPS).map { it / SCRUB_STEPS.toFloat() }.let { forward -> forward + forward.reversed() }

        val violations = EdgeRule.entries.flatMap { edgeRule ->
            val from = family[0].withEdgeRule(edgeRule)
            val to = family[3].withEdgeRule(edgeRule)
            val run = MorphRun(from, to, TALL_HERO_WIDTH, TALL_HERO_HEIGHT)
            scrub.flatMap { fraction -> run.frames("$edgeRule scrub at $fraction", fraction, frameCount = 1) }
        }

        violations.shouldBeEmpty()
    }

    @Test
    fun `an incompatible pair snaps from one side to the other half way`() {
        val from = NetworkGraphPresets.baseline
        val to = NetworkGraphPresets.ribbonMesh

        morphLayers(from, to, 0.49f).single().spec shouldBe from.layers.single()
        morphLayers(from, to, 0.5f).single().spec shouldBe to.layers.single()
    }

    @Test
    fun `a back layer only one side has fades in from zero alpha and leaves the front layer's seed alone`() {
        val single = family.first { it.layers.size == 1 }
        val withGhost = family.first { it.layers.size == 2 }

        val layers = morphLayers(single, withGhost, 0.5f)

        layers shouldHaveSize 2
        layers[0].spec.alpha shouldBe (withGhost.layers[0].alpha * 0.5f plusOrMinus 1e-4f)
        layers[1].seed shouldBe morphLayers(single, single, 0f).single().seed
    }

    @Test
    fun `resizing the surface keeps every existing node in place and the invariants on every frame`() {
        val layer = family[3].layers.single()
        val field = LayerField(layer, family[3].seed, TALL_HERO_HEIGHT, PX_PER_DP)
        field.advance(0f, TALL_HERO_WIDTH, TALL_HERO_HEIGHT)
        val homes = (0 until field.nodeCount).map(field::nodeHome)
        val heights = (0..RESIZE_STEPS).map { TALL_HERO_HEIGHT - (TALL_HERO_HEIGHT - WIDE_STRIP_HEIGHT) * it / RESIZE_STEPS }
        val sizes = heights.map { TALL_HERO_WIDTH to it } + heights.reversed().map { TALL_HERO_WIDTH to it } + (GROWN_WIDTH to GROWN_HEIGHT)

        val violations = sizes.flatMapIndexed { frame, (width, height) ->
            val timeSeconds = (frame + 1) * FRAME_SECONDS
            field.advance(timeSeconds, width, height)
            frameViolations("${width.toInt()}x${height.toInt()}", field, layer.strictTriangles, timeSeconds)
        }

        violations.shouldBeEmpty()
        field.nodeCount shouldBeGreaterThan homes.size
        (homes.indices).map(field::nodeHome) shouldBe homes
    }

    /**
     * Over every pair and surface — some 80 000 node morphs — this allows [CROSSING_FRONTS_BUDGET]
     * exceptions: where a pair's leaving and arriving fronts follow two different shapes, they can cross
     * so that a node that stays briefly has neither its old edges nor its replacements yet.
     */
    @Test
    fun `scrubbing any morph family pair end to end turns each node on or off at most once`() {
        val flickering = morphFamilyPairs().flatMap { (from, to) ->
            surfaces.flatMap { (width, height) ->
                val run = MorphRun(from, to, width, height)
                val visibility = scrubFrames().map { fraction -> run.visibility(fraction) }
                visibility.first().keys.flatMap { layerSeed ->
                    val layerVisibility = visibility.map { it.getValue(layerSeed) }
                    layerVisibility.first().indices.filter { node -> layerVisibility.zipWithNext { before, after -> before[node] != after[node] }.count { it } > 1 }
                        .map { node -> "${from.name} → ${to.name} ${width.toInt()}x${height.toInt()} layer $layerSeed node $node" }
                }
            }
        }

        flickering.size shouldBeLessThanOrEqual CROSSING_FRONTS_BUDGET
    }

    /** Allows [CROSSING_FRONTS_BUDGET] exceptions, for the reason the test above gives. */
    @Test
    fun `a node visible at both ends of a morph stays visible all the way through`() {
        val dropped = morphFamilyPairs().flatMap { (from, to) ->
            surfaces.flatMap { (width, height) ->
                val run = MorphRun(from, to, width, height)
                val visibility = scrubFrames().map { fraction -> run.visibility(fraction) }
                visibility.first().keys.flatMap { layerSeed ->
                    val layerVisibility = visibility.map { it.getValue(layerSeed) }
                    layerVisibility.first().indices
                        .filter { node -> layerVisibility.first()[node] && layerVisibility.last()[node] && layerVisibility.any { !it[node] } }
                        .map { node -> "${from.name} → ${to.name} ${width.toInt()}x${height.toInt()} layer $layerSeed node $node" }
                }
            }
        }

        dropped.size shouldBeLessThanOrEqual CROSSING_FRONTS_BUDGET
    }

    @Test
    fun `a uniform field morphing into a corner bloom removes the nodes furthest from the corner first`() {
        val from = family.first { it.name == "Uniform field" }
        val to = family.first { it.name == "Corner bloom" }
        val field = MorphRun(from, to, TALL_HERO_WIDTH, TALL_HERO_HEIGHT).also { it.advance(0f) }.singleField()
        val initiallyPresent = (0 until field.nodeCount).filter { field.nodePresence(it) >= HALF_PRESENT }.toSet()
        val leftAt = HashMap<Int, Float>()
        val run = MorphRun(from, to, TALL_HERO_WIDTH, TALL_HERO_HEIGHT)
        for (step in 0..ORDER_STEPS) {
            val fraction = step / ORDER_STEPS.toFloat()
            val stepField = run.also { it.advance(fraction) }.singleField()
            initiallyPresent.filter { it !in leftAt && stepField.nodePresence(it) < HALF_PRESENT }.forEach { leftAt[it] = fraction }
        }
        val leaving = leftAt.keys.toList()
        val distances = leaving.map { node -> field.nodeHome(node).let { home -> hypot(TALL_HERO_WIDTH - home.x, home.y) } }

        leaving.size shouldBeGreaterThan MIN_LEAVING_NODES
        rankCorrelation(distances, leaving.map(leftAt::getValue)) shouldBeLessThan -MIN_ORDER_CORRELATION
    }

    @Test
    fun `a morph played backwards gives every node the presence it had at the mirrored fraction`() {
        val mismatches = morphFamilyPairs().flatMap { (from, to) ->
            fractions.flatMap { fraction ->
                val forward = MorphRun(from, to, TALL_HERO_WIDTH, TALL_HERO_HEIGHT).also { it.advance(fraction) }
                val backward = MorphRun(to, from, TALL_HERO_WIDTH, TALL_HERO_HEIGHT).also { it.advance(1f - fraction) }
                forward.fields.flatMap { (layerSeed, field) ->
                    val mirror = backward.fields.getValue(layerSeed)
                    (0 until field.nodeCount).filter { abs(field.nodePresence(it) - mirror.nodePresence(it)) > PRESENCE_TOLERANCE }
                        .map { node -> "${from.name} → ${to.name} at $fraction layer $layerSeed node $node" }
                }
            }
        }

        mismatches.shouldBeEmpty()
    }

    @Test
    fun `a fraction jittering around one value never re-settles the mesh`() {
        val from = family.first { it.name == "Uniform field" }
        val to = family.first { it.name == "Corner bloom" }
        EdgeRule.entries.forEach { edgeRule ->
            val run = MorphRun(from.withEdgeRule(edgeRule), to.withEdgeRule(edgeRule), TALL_HERO_WIDTH, TALL_HERO_HEIGHT)
            val jitter = (0 until JITTER_FRAMES).map { frame -> JITTER_CENTER + if (frame % 2 == 0) JITTER_AMPLITUDE else -JITTER_AMPLITUDE }
            jitter.take(2).forEach(run::advance)
            val field = run.singleField()
            val settledTopology = field.topology
            val settledMembershipChanges = field.meshMembershipChanges

            jitter.drop(2).forEach(run::advance)

            if (edgeRule == EdgeRule.FixedTriangulation) {
                (field.topology === settledTopology) shouldBe true
            } else {
                field.meshMembershipChanges shouldBe settledMembershipChanges
            }
        }
    }

    @Test
    fun `a timed morph retargeted part way starts from exactly what was drawn`() {
        val first = family.first { it.name == "Uniform field" }
        val second = family.first { it.name == "Corner bloom" }
        val third = family.first { it.name == "Ribbon" }
        val interrupted = NetworkGraphMorph(first, second, INTERRUPT_FRACTION)
        val retargeted = NetworkGraphMorph(lerp(first, second, INTERRUPT_FRACTION), third, 0f, origin = interrupted)

        val drawn = MorphRun(interrupted, TALL_HERO_WIDTH, TALL_HERO_HEIGHT).also { it.advance(INTERRUPT_FRACTION) }.singleField()
        val restarted = MorphRun(retargeted, TALL_HERO_WIDTH, TALL_HERO_HEIGHT).also { it.advance(0f) }.singleField()
        val halfway = MorphRun(retargeted, TALL_HERO_WIDTH, TALL_HERO_HEIGHT).also { it.advance(0.5f) }

        (0 until drawn.nodeCount).map(drawn::nodePresence) shouldBe (0 until restarted.nodeCount).map(restarted::nodePresence)
        halfway.frames("retargeted half way", 0.5f, frameCount = 1).shouldBeEmpty()
    }

    /** Every ordered pair of distinct [family] variants. */
    private fun morphFamilyPairs(): List<Pair<NetworkGraphSpec, NetworkGraphSpec>> =
        family.flatMap { from -> family.filter { it != from }.map { to -> Pair(from, to) } }

    /** A two-second swipe from end to end, then a second held at the end so every fade can finish. */
    private fun scrubFrames() = (0..SWIPE_FRAMES).map { it / SWIPE_FRAMES.toFloat() } + List(HOLD_FRAMES) { 1f }

    /** Spearman's rank correlation of [first] and [second]. */
    private fun rankCorrelation(first: List<Float>, second: List<Float>): Double {
        fun ranks(values: List<Float>): List<Double> {
            val order = values.indices.sortedBy { values[it] }
            return DoubleArray(values.size).also { ranks -> order.forEachIndexed { rank, index -> ranks[index] = rank.toDouble() } }.toList()
        }
        val firstRanks = ranks(first)
        val secondRanks = ranks(second)
        val firstMean = firstRanks.average()
        val secondMean = secondRanks.average()
        val covariance = firstRanks.indices.sumOf { (firstRanks[it] - firstMean) * (secondRanks[it] - secondMean) }
        val firstSpread = firstRanks.sumOf { (it - firstMean) * (it - firstMean) }
        val secondSpread = secondRanks.sumOf { (it - secondMean) * (it - secondMean) }
        return covariance / sqrt(firstSpread * secondSpread)
    }

    /** One [LayerField] per morph layer, kept across frames the way the modifier keeps them. */
    private class MorphRun(private val morph: NetworkGraphMorph, private val width: Float, private val height: Float) {
        constructor(from: NetworkGraphSpec, to: NetworkGraphSpec, width: Float, height: Float) : this(NetworkGraphMorph(from, to, 0f), width, height)

        val fields = LinkedHashMap<Int, LayerField>()
        private var timeSeconds = 0f

        /** One frame at [fraction]; the layers it drew, back to front. */
        fun advance(fraction: Float): List<MorphLayer> {
            timeSeconds += FRAME_SECONDS
            return morphLayers(morph.copy(fraction = fraction)).onEach { layer ->
                val field = fields.getOrPut(layer.seed) { LayerField(layer.spec, layer.seed, max(width, height), PX_PER_DP) }
                field.spec = layer.spec
                field.shape = layer.shape
                field.advance(timeSeconds, width, height)
            }
        }

        fun frames(label: String, fraction: Float, frameCount: Int): List<String> = (0 until frameCount).flatMap {
            advance(fraction).flatMap { layer ->
                val field = fields.getValue(layer.seed)
                frameViolations("$label ${width.toInt()}x${height.toInt()} layer ${layer.seed}", field, layer.spec.strictTriangles, timeSeconds)
            }
        }

        /** One frame at [fraction]: which dots are drawn, per layer seed. */
        fun visibility(fraction: Float): Map<Int, List<Boolean>> = advance(fraction).associate { layer ->
            val field = fields.getValue(layer.seed)
            layer.seed to (0 until field.nodeCount).map { field.nodeAlpha(it) >= NODE_VISIBLE_ALPHA }
        }

        fun singleField(): LayerField = fields.values.single()
    }

    private fun NetworkGraphSpec.withEdgeRule(edgeRule: EdgeRule) = copy(layers = layers.map { it.copy(edgeRule = edgeRule) })

    private companion object {
        const val PX_PER_DP = 2.75f
        const val WIDE_STRIP_WIDTH = 1080f
        const val WIDE_STRIP_HEIGHT = 420f
        const val TALL_HERO_WIDTH = 1080f
        const val TALL_HERO_HEIGHT = 1900f
        const val GROWN_WIDTH = 1300f
        const val GROWN_HEIGHT = 2100f
        const val FRAME_SECONDS = 1f / 60f
        const val SCRUB_STEPS = 20
        const val RESIZE_STEPS = 12
        const val CROSSING_FRONTS_BUDGET = 6
        const val SWIPE_FRAMES = 120
        const val HOLD_FRAMES = 60
        const val ORDER_STEPS = 200
        const val HALF_PRESENT = 0.5f
        const val MIN_LEAVING_NODES = 100
        const val MIN_ORDER_CORRELATION = 0.9
        const val PRESENCE_TOLERANCE = 1e-4f
        const val JITTER_FRAMES = 240
        const val JITTER_CENTER = 0.5f
        const val JITTER_AMPLITUDE = 0.01f
        const val INTERRUPT_FRACTION = 0.4f
    }
}
