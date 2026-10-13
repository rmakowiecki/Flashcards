package com.rossomak.networkgraph

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.lerp
import com.rossomak.networkgraph.NetworkGraphMorphSide.From
import com.rossomak.networkgraph.NetworkGraphMorphSide.To
import kotlin.math.max
import kotlin.math.min

/** Seeds of consecutive layers are this far apart, so two layers of one spec never share a layout. */
private const val LAYER_SEED_STRIDE = 7_919

private const val DEFAULT_MORPH_MILLIS = 900

/** Where a value that can't be interpolated, such as a shape list, goes from one end to the other. */
private const val SNAP_FRACTION = 0.5f

/** How many interrupted morphs [animateNetworkGraphMorph] keeps nested under the current one before dropping the oldest. */
private const val MAX_MORPH_NESTING = 4

/**
 * A background part way between two specs: what [networkGraphBackground]'s morph overload draws.
 * [fraction] runs from 0 ([from]) to 1 ([to]) and can stop, scrub and reverse anywhere in between.
 *
 * [origin] is set only by [animateNetworkGraphMorph] when it is retargeted part way: the morph it
 * interrupted, frozen where it stopped, which then stands in for [from]'s node presences so the new
 * morph starts exactly from what was on screen. [from] is that frozen morph's specs interpolated.
 */
@Immutable
public data class NetworkGraphMorph(
    val from: NetworkGraphSpec,
    val to: NetworkGraphSpec,
    val fraction: Float,
    internal val origin: NetworkGraphMorph? = null,
)

/**
 * A timed morph toward [target]: every time [target] changes, the background morphs from wherever it
 * is to the new target over [durationMillis], including from part way through an earlier morph.
 */
@Composable
public fun animateNetworkGraphMorph(target: NetworkGraphSpec, durationMillis: Int = DEFAULT_MORPH_MILLIS): NetworkGraphMorph {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    var origin by remember { mutableStateOf<NetworkGraphMorph?>(null) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        val interrupted = NetworkGraphMorph(from, to, progress.value, origin)
        from = lerp(from, to, progress.value)
        to = target
        origin = if (progress.value < 1f) interrupted.withNestingAtMost(MAX_MORPH_NESTING) else null
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing))
    }
    return NetworkGraphMorph(from, to, progress.value, origin.takeIf { progress.value < 1f })
}

/** This morph with at most [levels] morphs nested under it; the oldest beyond that snaps to its interpolated [from]. */
private fun NetworkGraphMorph.withNestingAtMost(levels: Int): NetworkGraphMorph? = when {
    levels <= 0 -> null
    fraction <= 0f -> origin?.withNestingAtMost(levels)
    else -> copy(origin = origin?.withNestingAtMost(levels - 1))
}

/**
 * One drawn layer of a morph: the layer's own seed, its spec at the morph's fraction, the [shape]
 * that decides which of its nodes show, the [fill] it is painted with, and the spec layer it draws,
 * counted from the front: two meshes dissolving into each other draw the same [slotFromFront].
 */
internal class MorphLayer(
    val seed: Int,
    val spec: NetworkGraphLayerSpec,
    val shape: LayerShape,
    val fill: FillState,
    val slotFromFront: Int,
)

/**
 * Which nodes of a layer show: a spec's own shape, a morph between two shapes, or [Vanish], no nodes
 * at all. A morph never interpolates the shapes' parameters; each node takes its presence from the two
 * ends and switches between them at a fraction of its own (see [LayerField]), so nodes present at both
 * ends stay put.
 */
internal sealed interface LayerShape {
    /** The spec whose shape is nearest to this one, which orders the nodes a morph out of or into it switches. */
    val nearestSpec: NetworkGraphLayerSpec

    /** The seed of the mesh [nearestSpec] is drawn by, when that is another mesh than this layer's own. */
    val nearestSeed: Int?

    data class Of(val spec: NetworkGraphLayerSpec) : LayerShape {
        override val nearestSpec: NetworkGraphLayerSpec get() = spec
        override val nearestSeed: Int? get() = null
    }

    data class Morph(val from: LayerShape, val to: LayerShape, val fraction: Float) : LayerShape {
        private val nearer: LayerShape get() = if (fraction < SNAP_FRACTION) from else to
        override val nearestSpec: NetworkGraphLayerSpec get() = nearer.nearestSpec
        override val nearestSeed: Int? get() = nearer.nearestSeed
    }

    /**
     * No node shows: the end a mesh dissolves out of or into while another mesh, [guideSeed]'s,
     * takes its place drawing [guide]. Its nodes switch in the order a same-family morph into or out
     * of [guide] would use; where neither shape ranks them, they follow one sweep shared by both
     * meshes, so a node leaves where the other mesh's arrive. [reversed] mirrors that sweep, and is set
     * for the opposite direction, so a dissolve played backwards retraces itself.
     *
     * Only one of the two meshes sends the dissolve's comets, the same one whichever way it runs: the
     * leaving mesh when [reversed], the arriving one otherwise.
     */
    data class Vanish(val guide: NetworkGraphLayerSpec, val guideSeed: Int, val reversed: Boolean) : LayerShape {
        override val nearestSpec: NetworkGraphLayerSpec get() = guide
        override val nearestSeed: Int? get() = guideSeed
    }
}

/**
 * The layers [from] and [to] draw at [fraction], back to front.
 *
 * Layers pair up counting from the front, and a layer one side lacks is the other side's at zero
 * alpha, so a back layer fades in or out. A pair in the same morph family (shared seed, density,
 * jitter, edge rule and strict-triangles flag) has every other field interpolated and its shapes
 * morphed. Any other pair dissolves one mesh into the other: both draw while the morph runs, the
 * leaving mesh's nodes switching off and the arriving mesh's switching on in the order a same-family
 * morph would switch them (see [LayerShape.Vanish]). Fills blend the same way either way.
 */
internal fun morphLayers(from: NetworkGraphSpec, to: NetworkGraphSpec, fraction: Float, inherited: NetworkGraphFill): List<MorphLayer> =
    morphLayers(NetworkGraphMorph(from, to, fraction), inherited)

/**
 * [morph]'s layers, each painted with its own fill, else its spec's, else [inherited]. Where
 * [NetworkGraphMorph.origin] is set, the layers it draws are what the morph starts from, shapes and
 * fills included: a mesh of the end's family morphs on, and every other mesh dissolves.
 */
internal fun morphLayers(morph: NetworkGraphMorph, inherited: NetworkGraphFill): List<MorphLayer> {
    val from = morph.from
    val to = morph.to
    val progress = morph.fraction.coerceIn(0f, 1f)
    val originLayers = morph.origin?.let { origin -> morphLayers(origin, inherited) }.orEmpty()
    val count = max(from.layers.size, to.layers.size)
    return (count - 1 downTo 0).flatMap { slotFromFront ->
        val fromLayer = from.layers.getOrNull(from.layers.lastIndex - slotFromFront)
        val toLayer = to.layers.getOrNull(to.layers.lastIndex - slotFromFront)
        // A layer one side lacks is the other side's, invisible, in that side's mesh and fill.
        val startSeed = layerSeed(if (fromLayer != null) from.seed else to.seed, slotFromFront)
        val endSeed = if (toLayer != null) layerSeed(to.seed, slotFromFront) else startSeed
        val start = fromLayer ?: checkNotNull(toLayer).copy(alpha = 0f)
        val end = toLayer ?: start.copy(alpha = 0f)
        val startFill = FillState.Of(resolveFill(start, if (fromLayer != null) from else to, inherited), start, From)
        val endFill = FillState.Of(resolveFill(end, if (toLayer != null) to else from, inherited), end, To)
        val startMeshes = originLayers.filter { it.slotFromFront == slotFromFront }.ifEmpty {
            listOf(MorphLayer(startSeed, start, LayerShape.Of(start), startFill, slotFromFront))
        }
        val ends = SlotEnds(start, startSeed, startMeshes.firstOrNull { it.seed == startSeed && it.spec.isSameMorphFamily(start) }?.fill ?: startFill, end, endSeed, endFill)
        slotLayers(startMeshes, ends, progress, slotFromFront)
    }
}

/** One slot's two ends, as [morphLayers] resolved them. */
private class SlotEnds(
    val start: NetworkGraphLayerSpec,
    val startSeed: Int,
    val startFill: FillState,
    val end: NetworkGraphLayerSpec,
    val endSeed: Int,
    val endFill: FillState.Of,
)

/** The meshes one slot draws at [progress]: [startMeshes] each morphing on or dissolving, and the end's mesh arriving if none of them is it. */
private fun slotLayers(startMeshes: List<MorphLayer>, ends: SlotEnds, progress: Float, slot: Int): List<MorphLayer> {
    val end = ends.end
    if (progress <= 0f) return startMeshes
    if (progress >= 1f) return listOf(MorphLayer(ends.endSeed, end, LayerShape.Of(end), ends.endFill, slot))
    // Every mesh keeps its place in the drawing order, so two meshes never swap mid-morph.
    val drawn = startMeshes.map { mesh ->
        val isEndMesh = mesh.seed == ends.endSeed && mesh.spec.isSameMorphFamily(end)
        val target = if (isEndMesh) LayerShape.Of(end) else LayerShape.Vanish(end, ends.endSeed, reversed = isReversed(mesh.seed, mesh.spec, ends.endSeed, end))
        MorphLayer(
            seed = mesh.seed,
            spec = lerp(mesh.spec, if (isEndMesh) end else end.inFamilyOf(mesh.spec), progress),
            shape = LayerShape.Morph(mesh.shape, target, progress),
            fill = blendFills(mesh.fill, ends.endFill, progress),
            slotFromFront = slot,
        )
    }
    if (drawn.any { it.shape is LayerShape.Morph && it.shape.to is LayerShape.Of }) return drawn
    val start = ends.start
    val arriving = MorphLayer(
        seed = ends.endSeed,
        spec = lerp(start.inFamilyOf(end), end, progress),
        shape = LayerShape.Morph(LayerShape.Vanish(start, ends.startSeed, reversed = isReversed(ends.startSeed, start, ends.endSeed, end)), LayerShape.Of(end), progress),
        fill = blendFills(ends.startFill, ends.endFill, progress),
        slotFromFront = slot,
    )
    return drawn + arriving
}

/**
 * Whether a dissolve from the mesh of [fromSeed] and [from]'s family into [toSeed]'s and [to]'s runs
 * its sweep mirrored: an arbitrary but fixed order of the two meshes, so the opposite direction always
 * gets the opposite answer.
 */
private fun isReversed(fromSeed: Int, from: NetworkGraphLayerSpec, toSeed: Int, to: NetworkGraphLayerSpec): Boolean =
    compareValuesBy(MeshKey(fromSeed, from), MeshKey(toSeed, to), { it.seed }, { it.density }, { it.jitter }, { it.edgeRule }, { it.strictTriangles }) > 0

private class MeshKey(val seed: Int, spec: NetworkGraphLayerSpec) {
    val density = spec.density
    val jitter = spec.jitter
    val edgeRule = spec.edgeRule
    val strictTriangles = spec.strictTriangles
}

/** This layer with [other]'s morph-family fields, so it can be interpolated against layers of [other]'s mesh. */
private fun NetworkGraphLayerSpec.inFamilyOf(other: NetworkGraphLayerSpec): NetworkGraphLayerSpec =
    copy(density = other.density, jitter = other.jitter, edgeRule = other.edgeRule, strictTriangles = other.strictTriangles)

/**
 * The spec [morphLayers] draws, as a spec of its own: each layer the one of the mesh that ends the
 * morph, interpolated, and its fill that of the end it is nearer, unresolved. Only used to restart a
 * morph from part way through, alongside the interrupted morph itself, which carries exactly what was
 * on screen, every mesh and fill included.
 */
internal fun lerp(from: NetworkGraphSpec, to: NetworkGraphSpec, fraction: Float): NetworkGraphSpec {
    val nearer = if (fraction < SNAP_FRACTION) from else to
    if (from.seed != to.seed) return nearer
    val layers = morphLayers(from, to, fraction, NeutralFill)
        .groupBy { it.slotFromFront }
        .entries
        .sortedByDescending { it.key }
        .map { (_, slot) -> (slot.firstOrNull { (it.shape as? LayerShape.Morph)?.to is LayerShape.Of } ?: slot.last()).spec }
    return nearer.copy(layers = layers)
}

/** Stands in for the inherited fill where only shapes and specs matter, never colors. */
private val NeutralFill = NetworkGraphFill.Solid(Color.Transparent)

/**
 * True when every layer of this spec morphs into [other]'s within one mesh, its nodes staying put
 * while the shape changes; any other layer pair dissolves one mesh into the other.
 */
public fun NetworkGraphSpec.isSameMorphFamily(other: NetworkGraphSpec): Boolean {
    if (seed != other.seed) return false
    val pairs = min(layers.size, other.layers.size)
    return (1..pairs).all { fromFront -> layers[layers.size - fromFront].isSameMorphFamily(other.layers[other.layers.size - fromFront]) }
}

private fun layerSeed(specSeed: Int, indexFromFront: Int): Int = specSeed + indexFromFront * LAYER_SEED_STRIDE

internal fun NetworkGraphLayerSpec.isSameMorphFamily(other: NetworkGraphLayerSpec): Boolean =
    density == other.density && jitter == other.jitter && edgeRule == other.edgeRule && strictTriangles == other.strictTriangles

/**
 * Every continuous field interpolated; the morph-family fields are equal on both sides already. The
 * shapes and fill snap half way: which nodes show comes from [LayerShape] and the colors from
 * [FillState], never from these, so only a morph restarted from this spec without its origin sees them.
 */
private fun lerp(from: NetworkGraphLayerSpec, to: NetworkGraphLayerSpec, fraction: Float): NetworkGraphLayerSpec = from.copy(
    shapes = if (fraction < SNAP_FRACTION) from.shapes else to.shapes,
    fillChance = lerp(from.fillChance, to.fillChance, fraction),
    driftAmplitude = lerp(from.driftAmplitude, to.driftAmplitude, fraction),
    driftSpeed = lerp(from.driftSpeed, to.driftSpeed, fraction),
    envelopeFloor = lerp(from.envelopeFloor, to.envelopeFloor, fraction),
    waveAmplitude = lerp(from.waveAmplitude, to.waveAmplitude, fraction),
    waveSpeed = lerp(from.waveSpeed, to.waveSpeed, fraction),
    maxEdgeFactor = lerp(from.maxEdgeFactor, to.maxEdgeFactor, fraction),
    minAngleDegrees = lerp(from.minAngleDegrees, to.minAngleDegrees, fraction),
    edgeKeepChance = lerp(from.edgeKeepChance, to.edgeKeepChance, fraction),
    repairReach = lerp(from.repairReach, to.repairReach, fraction),
    strokeWidthDp = lerp(from.strokeWidthDp, to.strokeWidthDp, fraction),
    edgeAlpha = lerp(from.edgeAlpha, to.edgeAlpha, fraction),
    lengthFalloff = lerp(from.lengthFalloff, to.lengthFalloff, fraction),
    pulseDepth = lerp(from.pulseDepth, to.pulseDepth, fraction),
    nodeSizeDp = lerp(from.nodeSizeDp, to.nodeSizeDp, fraction),
    nodeSizeVariance = lerp(from.nodeSizeVariance, to.nodeSizeVariance, fraction),
    hotNodeShare = lerp(from.hotNodeShare, to.hotNodeShare, fraction),
    glowRadiusDp = lerp(from.glowRadiusDp, to.glowRadiusDp, fraction),
    glowStrength = lerp(from.glowStrength, to.glowStrength, fraction),
    depthDimming = lerp(from.depthDimming, to.depthDimming, fraction),
    cometStrength = lerp(from.cometStrength, to.cometStrength, fraction),
    cometSpark = lerp(from.cometSpark, to.cometSpark, fraction),
    alpha = lerp(from.alpha, to.alpha, fraction),
    fill = if (fraction < SNAP_FRACTION) from.fill else to.fill,
)
