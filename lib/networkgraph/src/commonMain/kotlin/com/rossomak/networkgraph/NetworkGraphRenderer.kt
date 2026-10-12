@file:OptIn(ExperimentalNetworkGraphApi::class)

package com.rossomak.networkgraph

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalWindowInfo
import kotlin.math.max
import kotlin.time.TimeSource

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val NANOS_PER_MILLI = 1_000_000f

/**
 * Draws [spec] as an animated network-graph background: every layer's edges, glows and nodes,
 * back to front, painted with whatever [palette] gives each layer. Paints nothing else and is
 * transparent everywhere it doesn't draw, so it composes over whatever `Modifier.background(...)` ran
 * earlier in the chain.
 *
 * Every drawn node has at least two visible edges, and its dot is at least as opaque as each of them;
 * see [NetworkGraphLayerSpec].
 *
 * Ignores the system animator-duration scale ("Remove animations") and keeps its frame loop running
 * while the surface is off-screen or the app is backgrounded.
 */
@Composable
public fun Modifier.networkGraphBackground(spec: NetworkGraphSpec, palette: NetworkGraphPalette): Modifier =
    networkGraphBackground(NetworkGraphMorph(spec, spec, 0f), palette)

/**
 * The [morph] between two specs, at its fraction; see [NetworkGraphMorph]. Drive the fraction from
 * anything — a pager's scroll position, a slider, or [animateNetworkGraphMorph] — and the background
 * follows it continuously, in either direction.
 *
 * The surface may change size at any time without reshuffling: the node grid is sized from the
 * window, not the surface, and the shape follows the surface's new bounds the same way a morph does.
 */
@Composable
public fun Modifier.networkGraphBackground(morph: NetworkGraphMorph, palette: NetworkGraphPalette): Modifier =
    drawNetworkGraph(morph, palette, stats = null, layerStats = null, showViolations = false)

/**
 * [networkGraphBackground] with diagnostics: [stats] gets each frame's draw time, [layerStats] each
 * layer's visible, pruned and repaired counts, and [showViolations] rings in red any node that breaks
 * the connectivity or alpha rules anyway, as an on-device check.
 */
@ExperimentalNetworkGraphApi
@Composable
public fun Modifier.networkGraphBackground(
    morph: NetworkGraphMorph,
    palette: NetworkGraphPalette,
    stats: NetworkGraphFrameStats?,
    layerStats: NetworkGraphLayerStats? = null,
    showViolations: Boolean = false,
): Modifier = drawNetworkGraph(morph, palette, stats, layerStats, showViolations)

@Composable
private fun Modifier.drawNetworkGraph(
    morph: NetworkGraphMorph,
    palette: NetworkGraphPalette,
    stats: NetworkGraphFrameStats?,
    layerStats: NetworkGraphLayerStats?,
    showViolations: Boolean,
): Modifier {
    val layers = morphLayers(morph)
    val windowSize = LocalWindowInfo.current.containerSize
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
        val drawStart = TimeSource.Monotonic.markNow()
        val timeSeconds = elapsedSeconds.floatValue
        // No window in previews; the surface itself is the next best fixed reference.
        val windowLongSide = max(windowSize.width, windowSize.height).toFloat()
        val referenceSize = if (windowLongSide > 0f) windowLongSide else max(size.width, size.height)
        val fields = holder.fieldsFor(layers, referenceSize, density)
        // Nodes deliberately sit past the surface's edges, and drawBehind is not clipped.
        clipRect {
            fields.forEachIndexed { index, field ->
                val fill = layerFill(palette, layers, index, timeSeconds, size)
                field.advance(timeSeconds, size.width, size.height)
                field.draw(this, fill, holder.halosFor(fill.maskColor), timeSeconds, showViolations)
            }
        }
        stats?.recordDraw(drawStart.elapsedNow().inWholeNanoseconds)
        layerStats?.record(fields)
    }
}

/**
 * What layer [index] paints with: [palette] asked about each end of the morph that shows, with two
 * solid ends blended into one color and anything else crossfaded by [MorphLayer.colorFraction].
 */
internal fun layerFill(palette: NetworkGraphPalette, layers: List<MorphLayer>, index: Int, timeSeconds: Float, size: Size): LayerFill {
    val layer = layers[index]
    fun brushFor(side: NetworkGraphMorphSide, spec: NetworkGraphLayerSpec) =
        palette.brushFor(NetworkGraphLayerContext(index, layers.size, spec, side, timeSeconds, size))
    val fraction = layer.colorFraction
    val from = if (fraction < 1f) brushFor(NetworkGraphMorphSide.From, layer.fromLayer) else null
    val to = if (fraction > 0f) brushFor(NetworkGraphMorphSide.To, layer.toLayer) else null
    return when {
        from == null -> LayerFill.of(checkNotNull(to))
        to == null -> LayerFill.of(from)
        from is SolidColor && to is SolidColor ->
            LayerFill.Solid(if (from.value == to.value) from.value else lerp(from.value, to.value, fraction))
        else -> LayerFill.Masked(from, overlay = to, overlayAlpha = fraction)
    }
}

/** How [LayerField.draw] colors a layer. */
internal sealed interface LayerFill {
    /** The color the layer's edges, halos and dots are drawn in before any brush is applied. */
    val maskColor: Color

    /** Every element drawn directly in [color]. */
    data class Solid(val color: Color) : LayerFill {
        override val maskColor: Color get() = color
    }

    /**
     * Every element drawn in white, then the whole layer recolored with [base], keeping each pixel's
     * alpha; [overlay], when set, is blended over [base] at [overlayAlpha], again keeping alpha.
     */
    data class Masked(val base: Brush, val overlay: Brush? = null, val overlayAlpha: Float = 0f) : LayerFill {
        override val maskColor: Color get() = Color.White
    }

    companion object {
        fun of(brush: Brush): LayerFill = if (brush is SolidColor) Solid(brush.value) else Masked(brush)
    }
}

/**
 * Average frame interval and graph draw time, published twice a second so the readout doesn't
 * recompose every frame. Draw time is CPU time spent recording every graph on the page into the
 * display list, summed per frame — a relative cost signal between presets, not a GPU measurement.
 */
@ExperimentalNetworkGraphApi
@Stable
public class NetworkGraphFrameStats {
    public var frameMillis: Float by mutableFloatStateOf(0f)
        private set
    public var drawMillis: Float by mutableFloatStateOf(0f)
        private set

    private var intervalNanosSum = 0L
    private var drawNanosSum = 0L
    private var frames = 0

    public fun recordDraw(nanos: Long) {
        drawNanosSum += nanos
    }

    public fun recordFrame(intervalNanos: Long) {
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
@ExperimentalNetworkGraphApi
@Stable
public class NetworkGraphLayerStats {
    public var summary: String by mutableStateOf("")
        private set

    private var lastPublish: TimeSource.Monotonic.ValueTimeMark? = null

    internal fun record(fields: List<LayerField>) {
        val sincePublish = lastPublish?.elapsedNow()?.inWholeNanoseconds
        if (sincePublish != null && sincePublish < PUBLISH_INTERVAL_NANOS) return
        lastPublish = TimeSource.Monotonic.markNow()
        summary = fields.joinToString(separator = "  |  ") { field ->
            "${field.visibleNodeCount}/${field.prunedNodeCount} · ${field.repairedEdgeCount}"
        }
    }

    private companion object {
        const val PUBLISH_INTERVAL_NANOS = 500_000_000L
    }
}

/**
 * Per-modifier state kept across frames: one [LayerField] per layer and the halo brushes.
 *
 * A field is kept for as long as its layer stays in the same morph family and seed, whatever else
 * changes, so a morph, a tuning edit or a resize never rebuilds it; only a new window size, which
 * changes the grid's cell size, rebuilds them all.
 */
private class NetworkGraphHolder {
    private var builtReferenceSize = 0f
    private var builtPxPerDp = 0f
    private var fieldsByFamily = HashMap<LayerFamily, LayerField>()
    private val halosByColor = HashMap<Color, HaloBrushCache>()

    fun fieldsFor(layers: List<MorphLayer>, referenceSize: Float, pxPerDp: Float): List<LayerField> {
        if (referenceSize != builtReferenceSize || pxPerDp != builtPxPerDp) {
            fieldsByFamily.clear()
            builtReferenceSize = referenceSize
            builtPxPerDp = pxPerDp
        }
        val kept = HashMap<LayerFamily, LayerField>()
        val fields = layers.map { layer ->
            val family = LayerFamily(layer.seed, layer.spec)
            val field = fieldsByFamily[family] ?: LayerField(layer.spec, layer.seed, referenceSize, pxPerDp)
            field.spec = layer.spec
            field.shape = layer.shape
            kept[family] = field
            field
        }
        fieldsByFamily = kept
        return fields
    }

    fun halosFor(color: Color): HaloBrushCache = halosByColor.getOrPut(color) { HaloBrushCache(color) }
}

/** What a [LayerField] can't change after it is built. */
private data class LayerFamily(val seed: Int, val density: Float, val jitter: Float, val edgeRule: EdgeRule, val strictTriangles: Boolean) {
    constructor(seed: Int, spec: NetworkGraphLayerSpec) : this(seed, spec.density, spec.jitter, spec.edgeRule, spec.strictTriangles)
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
