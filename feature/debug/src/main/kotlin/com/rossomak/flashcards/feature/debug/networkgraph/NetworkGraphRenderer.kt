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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalWindowInfo
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import kotlin.math.max

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val NANOS_PER_MILLI = 1_000_000f

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
): Modifier = networkGraphBackground(NetworkGraphMorph(spec, spec, 0f), style, stats, layerStats, showViolations)

/**
 * The [morph] between two specs, at its fraction; see [NetworkGraphMorph] and [morphLayers]. Drive the
 * fraction from anything — a pager's scroll position, a slider, or [animateNetworkGraphMorph] — and
 * the background follows it continuously, in either direction.
 *
 * The surface may change size at any time without reshuffling: the node grid is sized from the
 * window, not the surface, and the shape follows the surface's new bounds the same way a morph does.
 */
@Composable
fun Modifier.networkGraphBackground(
    morph: NetworkGraphMorph,
    style: FlashcardsComponentStyle,
    stats: NetworkGraphFrameStats? = null,
    layerStats: NetworkGraphLayerStats? = null,
    showViolations: Boolean = false,
): Modifier {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val layers = morphLayers(morph)
    val layerColors = layers.map { layer ->
        when (style) {
            FlashcardsComponentStyle.OnGradient -> Color.White
            FlashcardsComponentStyle.OnSurface ->
                lerp(layer.fromColor ?: onSurfaceVariant, layer.toColor ?: onSurfaceVariant, layer.colorFraction)
        }
    }
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
        val drawStartNanos = System.nanoTime()
        val timeSeconds = elapsedSeconds.floatValue
        // No window in previews; the surface itself is the next best fixed reference.
        val windowLongSide = max(windowSize.width, windowSize.height).toFloat()
        val referenceSize = if (windowLongSide > 0f) windowLongSide else max(size.width, size.height)
        val fields = holder.fieldsFor(layers, referenceSize, density)
        // Nodes deliberately sit past the surface's edges, and drawBehind is not clipped.
        clipRect {
            fields.forEachIndexed { index, field ->
                val color = layerColors[index]
                field.advance(timeSeconds, size.width, size.height)
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
