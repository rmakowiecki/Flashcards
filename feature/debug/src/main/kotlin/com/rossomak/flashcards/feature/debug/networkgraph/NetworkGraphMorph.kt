package com.rossomak.flashcards.feature.debug.networkgraph

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
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.util.lerp
import kotlin.math.max
import kotlin.math.min

/** Seeds of consecutive layers are this far apart, so two layers of one spec never share a layout. */
private const val LAYER_SEED_STRIDE = 7_919

private const val DEFAULT_MORPH_MILLIS = 900

/** Below this fraction an incompatible pair shows [NetworkGraphMorph.from], from it on [NetworkGraphMorph.to]. */
private const val SNAP_FRACTION = 0.5f

/** How many interrupted morphs [animateNetworkGraphMorph] keeps nested under the current one before dropping the oldest. */
private const val MAX_MORPH_NESTING = 4

/**
 * PROTOTYPE — throwaway, see [NetworkGraphPrototypeScreen].
 *
 * A background part way between two specs: what [networkGraphBackground]'s morph overload draws.
 * [fraction] runs from 0 ([from]) to 1 ([to]) and can stop, scrub and reverse anywhere in between.
 *
 * [origin] is set only by [animateNetworkGraphMorph] when it is retargeted part way: the morph it
 * interrupted, frozen where it stopped, which then stands in for [from]'s node presences so the new
 * morph starts exactly from what was on screen. [from] is that frozen morph's specs interpolated.
 */
@Immutable
data class NetworkGraphMorph(
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
fun animateNetworkGraphMorph(target: NetworkGraphSpec, durationMillis: Int = DEFAULT_MORPH_MILLIS): NetworkGraphMorph {
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
 * that decides which of its nodes show, and the two colors to blend by [colorFraction] once the theme
 * has resolved a null one.
 */
internal class MorphLayer(
    val seed: Int,
    val spec: NetworkGraphLayerSpec,
    val shape: LayerShape,
    val fromColor: Color?,
    val toColor: Color?,
    val colorFraction: Float,
)

/**
 * Which nodes of a layer show: a spec's own shape, or a morph between two shapes. A morph never
 * interpolates the shapes' parameters; each node takes its presence from the two ends and switches
 * between them at a fraction of its own (see [LayerField]), so nodes present at both ends stay put.
 */
internal sealed interface LayerShape {
    /** The spec whose shape is nearest to this one, which orders the nodes a morph out of or into it switches. */
    val nearestSpec: NetworkGraphLayerSpec

    data class Of(val spec: NetworkGraphLayerSpec) : LayerShape {
        override val nearestSpec: NetworkGraphLayerSpec get() = spec
    }

    data class Morph(val from: LayerShape, val to: LayerShape, val fraction: Float) : LayerShape {
        override val nearestSpec: NetworkGraphLayerSpec get() = if (fraction < SNAP_FRACTION) from.nearestSpec else to.nearestSpec
    }
}

/**
 * The layers [from] and [to] draw at [fraction], back to front.
 *
 * Layers pair up counting from the front, and a layer one side lacks is the other side's at zero
 * alpha, so a back layer fades in or out. A pair in the same morph family (shared seed, density,
 * jitter, edge rule and strict-triangles flag) has every other field interpolated and its shapes
 * morphed; any other pair snaps from one side to the other at [SNAP_FRACTION], rebuilding that layer.
 */
internal fun morphLayers(from: NetworkGraphSpec, to: NetworkGraphSpec, fraction: Float): List<MorphLayer> =
    morphLayers(NetworkGraphMorph(from, to, fraction))

/** [morph]'s layers; a layer [NetworkGraphMorph.origin] draws in the same family starts from that layer's shape. */
internal fun morphLayers(morph: NetworkGraphMorph): List<MorphLayer> {
    val from = morph.from
    val to = morph.to
    val progress = morph.fraction.coerceIn(0f, 1f)
    val originLayers = morph.origin?.let(::morphLayers).orEmpty()
    val count = max(from.layers.size, to.layers.size)
    return (0 until count).mapNotNull { indexFromBack ->
        val indexFromFront = count - 1 - indexFromBack
        val fromLayer = from.layers.getOrNull(from.layers.lastIndex - indexFromFront)
        val toLayer = to.layers.getOrNull(to.layers.lastIndex - indexFromFront)
        val start = fromLayer ?: toLayer?.copy(alpha = 0f) ?: return@mapNotNull null
        val end = toLayer ?: start.copy(alpha = 0f)
        val fromSeed = layerSeed(from.seed, indexFromFront)
        val toSeed = layerSeed(to.seed, indexFromFront)
        val originLayer = originLayers.getOrNull(originLayers.lastIndex - indexFromFront)
        val startShape = originLayer
            ?.takeIf { it.seed == fromSeed && it.spec.isSameMorphFamily(start) }
            ?.shape
            ?: LayerShape.Of(start)
        when {
            fromSeed == toSeed && start.isSameMorphFamily(end) -> MorphLayer(
                seed = fromSeed,
                spec = lerp(start, end, progress),
                shape = LayerShape.Morph(startShape, LayerShape.Of(end), progress),
                fromColor = start.surfaceColor,
                toColor = end.surfaceColor,
                colorFraction = progress,
            )
            progress < SNAP_FRACTION -> MorphLayer(fromSeed, start, startShape, start.surfaceColor, start.surfaceColor, 0f)
            else -> MorphLayer(toSeed, end, LayerShape.Of(end), end.surfaceColor, end.surfaceColor, 0f)
        }
    }
}

/**
 * The spec [morphLayers] draws, as a spec of its own. A null color paired with a set one can't be
 * blended without the theme, so it snaps at [SNAP_FRACTION]; [animateNetworkGraphMorph] only uses
 * this to restart a morph from part way through.
 */
internal fun lerp(from: NetworkGraphSpec, to: NetworkGraphSpec, fraction: Float): NetworkGraphSpec {
    val nearer = if (fraction < SNAP_FRACTION) from else to
    if (from.seed != to.seed) return nearer
    val layers = morphLayers(from, to, fraction).map { layer ->
        val fromColor = layer.fromColor
        val toColor = layer.toColor
        val color = if (fromColor != null && toColor != null) lerpColor(fromColor, toColor, layer.colorFraction) else layer.spec.surfaceColor
        layer.spec.copy(surfaceColor = color)
    }
    return nearer.copy(layers = layers)
}

/** True when every layer of this spec morphs into [other]'s smoothly, with no layer snapping. */
internal fun NetworkGraphSpec.morphsSmoothlyInto(other: NetworkGraphSpec): Boolean {
    if (seed != other.seed) return false
    val pairs = min(layers.size, other.layers.size)
    return (1..pairs).all { fromFront -> layers[layers.size - fromFront].isSameMorphFamily(other.layers[other.layers.size - fromFront]) }
}

private fun layerSeed(specSeed: Int, indexFromFront: Int): Int = specSeed + indexFromFront * LAYER_SEED_STRIDE

internal fun NetworkGraphLayerSpec.isSameMorphFamily(other: NetworkGraphLayerSpec): Boolean =
    density == other.density && jitter == other.jitter && edgeRule == other.edgeRule && strictTriangles == other.strictTriangles

/**
 * Every continuous field interpolated; the morph-family fields are equal on both sides already. The
 * shapes snap half way: which nodes show comes from [LayerShape], never from these, so only a morph
 * restarted from this spec without its origin ever sees them.
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
    cometSpark = if (fraction < SNAP_FRACTION) from.cometSpark else to.cometSpark,
    alpha = lerp(from.alpha, to.alpha, fraction),
    surfaceColor = if (fraction < SNAP_FRACTION) from.surfaceColor else to.surfaceColor,
)
