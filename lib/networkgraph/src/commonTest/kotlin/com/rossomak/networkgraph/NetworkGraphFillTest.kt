package com.rossomak.networkgraph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import com.rossomak.networkgraph.NetworkGraphFill.Linear
import com.rossomak.networkgraph.NetworkGraphFill.Radial
import com.rossomak.networkgraph.NetworkGraphFill.Solid
import com.rossomak.networkgraph.NetworkGraphMorphSide.From
import com.rossomak.networkgraph.NetworkGraphMorphSide.To
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

/** How a layer's fill is resolved and what [toLayerFill] paints with, still and part way through a morph. */
class NetworkGraphFillTest {

    private val size = Size(SURFACE_WIDTH, SURFACE_HEIGHT)
    private val plainLayer = NetworkGraphLayerSpec()
    private val redLayer = NetworkGraphLayerSpec(fill = Solid(Color.Red))
    private val blueLayer = NetworkGraphLayerSpec(fill = Solid(Color.Blue))
    private val inheritedWhite = Solid(Color.White)
    private val fromGradient = Brush.linearGradient(listOf(Color.Red, Color.Blue), start = Offset.Zero, end = Offset(SURFACE_WIDTH, 0f))
    private val toGradient = Brush.linearGradient(listOf(Color.Green, Color.Yellow), start = Offset.Zero, end = Offset(0f, SURFACE_HEIGHT))

    @Test
    fun `a layer paints with its own fill, else its spec's, else the inherited one`() {
        val specFill = Solid(Color.Green)

        fillOf(spec(redLayer, fill = specFill)) shouldBe LayerFill.Solid(Color.Red)
        fillOf(spec(plainLayer, fill = specFill)) shouldBe LayerFill.Solid(Color.Green)
        fillOf(spec(plainLayer)) shouldBe LayerFill.Solid(Color.White)
    }

    @Test
    fun `two inherited ends of a morph keep the inherited color all the way through`() {
        val fill = fillOf(spec(plainLayer), spec(plainLayer.copy(edgeAlpha = 0.2f)), HALF_WAY)

        fill shouldBe LayerFill.Solid(Color.White)
    }

    @Test
    fun `an inherited end and an overriding end blend color by color`() {
        val fill = fillOf(spec(plainLayer), spec(blueLayer), HALF_WAY)

        fill shouldBe LayerFill.Solid(lerp(Color.White, Color.Blue, HALF_WAY))
    }

    @Test
    fun `a back layer only one side has takes that side's fill`() {
        val withBack = NetworkGraphSpec(seed = SEED, layers = listOf(blueLayer, plainLayer))
        val layers = morphLayers(spec(plainLayer), withBack, HALF_WAY, inheritedWhite)

        layers.first().fill.toLayerFill(0, layers.size, TIME_SECONDS, size) shouldBe LayerFill.Solid(Color.Blue)
    }

    @Test
    fun `a solid color's alpha is applied to the layer as a whole`() {
        val fill = LayerFill.Solid(Color.White.copy(alpha = 0.4f))

        fill.maskColor shouldBe Color.White
        fill.layerAlpha shouldBe 0.4f
    }

    @Test
    fun `a solid morphing into a gradient becomes a gradient of its color, blending stop by stop`() {
        val gradient = NetworkGraphFill.linear(listOf(Color.Red, Color.Blue), angleDegrees = 90f)

        val half = lerpFill(Solid(Color.White), gradient, HALF_WAY).shouldBeInstanceOf<Linear>()

        half.angleDegrees shouldBe 90f
        half.stops shouldContainExactly listOf(GradientStop(0f, lerp(Color.White, Color.Red, HALF_WAY)), GradientStop(1f, lerp(Color.White, Color.Blue, HALF_WAY)))
    }

    @Test
    fun `two gradients blend over the union of their stop positions`() {
        val from = Linear(listOf(GradientStop(0f, Color.Red), GradientStop(1f, Color.Blue)))
        val to = Linear(listOf(GradientStop(0f, Color.Green), GradientStop(0.25f, Color.Yellow), GradientStop(1f, Color.Green)))

        val start = lerpFill(from, to, 0f).shouldBeInstanceOf<Linear>()

        start.stops.map { it.position } shouldContainExactly listOf(0f, 0.25f, 1f)
        start.stops[1].color.red shouldBe (0.75f plusOrMinus COLOR_TOLERANCE)
        start.stops[1].color.blue shouldBe (0.25f plusOrMinus COLOR_TOLERANCE)
    }

    @Test
    fun `a linear gradient turns the short way round`() {
        val from = NetworkGraphFill.linear(listOf(Color.Red, Color.Blue), angleDegrees = 350f)
        val to = NetworkGraphFill.linear(listOf(Color.Red, Color.Blue), angleDegrees = 10f)

        lerpFill(from, to, HALF_WAY).shouldBeInstanceOf<Linear>().angleDegrees shouldBe (360f plusOrMinus ANGLE_TOLERANCE)
    }

    @Test
    fun `a radial gradient moves its center and radius`() {
        val from = NetworkGraphFill.radial(listOf(Color.Red, Color.Blue), center = Offset(0f, 0f), radius = 0.2f)
        val to = NetworkGraphFill.radial(listOf(Color.Red, Color.Blue), center = Offset(1f, 1f), radius = 0.6f)

        val half = lerpFill(from, to, HALF_WAY).shouldBeInstanceOf<Radial>()

        half.center shouldBe Offset(0.5f, 0.5f)
        half.radius shouldBe (0.4f plusOrMinus ANGLE_TOLERANCE)
    }

    @Test
    fun `a linear gradient morphing into a radial one passes through one color half way`() {
        val linear = NetworkGraphFill.linear(listOf(Color.Red, Color.Red))
        val radial = NetworkGraphFill.radial(listOf(Color.Blue, Color.Blue))

        val middle = lerp(Color.Red, Color.Blue, HALF_WAY)

        val halfColors = lerpFill(linear, radial, HALF_WAY).shouldBeInstanceOf<Radial>().stops.map { it.color }

        halfColors.forEach { color ->
            color.red shouldBe (middle.red plusOrMinus COLOR_TOLERANCE)
            color.green shouldBe (middle.green plusOrMinus COLOR_TOLERANCE)
            color.blue shouldBe (middle.blue plusOrMinus COLOR_TOLERANCE)
        }
        lerpFill(linear, radial, 0.25f).shouldBeInstanceOf<Linear>()
    }

    @Test
    fun `a gradient fill spans the surface along its angle`() {
        val brush = NetworkGraphFill.linear(listOf(Color.Red, Color.Blue), angleDegrees = 0f).brushOver(size)

        brush shouldBe Brush.linearGradient(
            colorStops = arrayOf(0f to Color.Red, 1f to Color.Blue),
            start = Offset(0f, SURFACE_HEIGHT / 2f),
            end = Offset(SURFACE_WIDTH, SURFACE_HEIGHT / 2f),
        )
    }

    @Test
    fun `a custom fill masks a still layer with its brush`() {
        val fill = fillOf(spec(plainLayer, fill = NetworkGraphFill.Custom { fromGradient }))

        fill shouldBe LayerFill.Masked(fromGradient)
    }

    @Test
    fun `a custom end crossfades into the other end by the morph fraction, both brushes asked every frame`() {
        val custom = NetworkGraphFill.Custom { context -> if (context.side == From) fromGradient else toGradient }

        val fill = fillOf(spec(plainLayer, fill = custom), spec(plainLayer.copy(edgeAlpha = 0.2f), fill = custom), HALF_WAY)

        fill shouldBe LayerFill.Masked(fromGradient, listOf(LayerFill.Overlay(toGradient, HALF_WAY)))
    }

    @Test
    fun `a solid end morphing into a custom end crossfades rather than blending colors`() {
        val fill = fillOf(spec(plainLayer), spec(plainLayer, fill = NetworkGraphFill.Custom { toGradient }), HALF_WAY)

        fill shouldBe LayerFill.Masked(SolidColor(Color.White), listOf(LayerFill.Overlay(toGradient, HALF_WAY)))
    }

    @Test
    fun `a custom fill reading the same at both ends draws its brush once`() {
        val custom = NetworkGraphFill.Custom { fromGradient }

        val fill = fillOf(spec(plainLayer, fill = custom), spec(plainLayer.copy(edgeAlpha = 0.2f), fill = custom), HALF_WAY)

        fill shouldBe LayerFill.Masked(fromGradient)
    }

    @Test
    fun `a custom fill sees each end's own spec, side, the surface size and the time`() {
        val contexts = mutableListOf<NetworkGraphLayerContext>()
        val custom = NetworkGraphFill.Custom { context -> SolidColor(Color.Red).also { contexts += context } }
        val fadedLayer = plainLayer.copy(edgeAlpha = 0.2f)

        fillOf(spec(plainLayer, fill = custom), spec(fadedLayer, fill = custom), HALF_WAY)

        contexts.map { it.side } shouldBe listOf(From, To)
        contexts.map { it.layer } shouldBe listOf(plainLayer, fadedLayer)
        contexts.map { it.size }.distinct() shouldBe listOf(size)
        contexts.map { it.timeSeconds }.distinct() shouldBe listOf(TIME_SECONDS)
    }

    @Test
    fun `a morph retargeted part way through a crossfade keeps the interrupted crossfade under the new one`() {
        val first = spec(plainLayer)
        val second = spec(plainLayer, fill = NetworkGraphFill.Custom { toGradient })
        val third = spec(plainLayer, fill = Solid(Color.Blue))
        val interrupted = NetworkGraphMorph(first, second, INTERRUPT_FRACTION)
        val retargeted = NetworkGraphMorph(lerp(first, second, INTERRUPT_FRACTION), third, HALF_WAY, origin = interrupted)

        val fill = morphLayers(retargeted, inheritedWhite).single().fill.toLayerFill(0, 1, TIME_SECONDS, size)

        fill shouldBe LayerFill.Masked(
            SolidColor(Color.White),
            listOf(LayerFill.Overlay(toGradient, INTERRUPT_FRACTION), LayerFill.Overlay(SolidColor(Color.Blue), HALF_WAY)),
        )
    }

    private fun spec(layer: NetworkGraphLayerSpec, fill: NetworkGraphFill? = null) = NetworkGraphSpec(seed = SEED, layers = listOf(layer), fill = fill)

    private fun fillOf(from: NetworkGraphSpec, to: NetworkGraphSpec = from, fraction: Float = 0f): LayerFill =
        morphLayers(from, to, fraction, inheritedWhite).single().fill.toLayerFill(0, 1, TIME_SECONDS, size)

    private companion object {
        const val SEED = 7
        const val HALF_WAY = 0.5f
        const val INTERRUPT_FRACTION = 0.4f
        const val TIME_SECONDS = 3.5f
        const val SURFACE_WIDTH = 1080f
        const val SURFACE_HEIGHT = 1900f
        const val COLOR_TOLERANCE = 0.01f
        const val ANGLE_TOLERANCE = 1e-3f
    }
}
