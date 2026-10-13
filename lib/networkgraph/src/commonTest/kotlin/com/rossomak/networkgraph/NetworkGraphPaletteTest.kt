package com.rossomak.networkgraph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import com.rossomak.networkgraph.NetworkGraphMorphSide.From
import com.rossomak.networkgraph.NetworkGraphMorphSide.To
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

/** What [layerFill] makes of a palette's brushes, at a still frame and part way through a morph. */
class NetworkGraphPaletteTest {

    private val size = Size(SURFACE_WIDTH, SURFACE_HEIGHT)
    private val redLayer = NetworkGraphLayerSpec(surfaceColor = Color.Red)
    private val blueLayer = NetworkGraphLayerSpec(surfaceColor = Color.Blue)
    private val uncoloredLayer = NetworkGraphLayerSpec()
    private val fromGradient = Brush.linearGradient(listOf(Color.Red, Color.Blue), start = Offset.Zero, end = Offset(SURFACE_WIDTH, 0f))
    private val toGradient = Brush.linearGradient(listOf(Color.Green, Color.Yellow), start = Offset.Zero, end = Offset(0f, SURFACE_HEIGHT))

    @Test
    fun `fromSpec paints a still layer in its own color`() {
        val fill = fillFor(NetworkGraphPalette.fromSpec(fallback = Color.Gray), redLayer, redLayer, fraction = 0f)

        fill shouldBe LayerFill.Solid(Color.Red)
    }

    @Test
    fun `fromSpec paints a layer with no color in the fallback`() {
        val fill = fillFor(NetworkGraphPalette.fromSpec(fallback = Color.Gray), uncoloredLayer, uncoloredLayer, fraction = 0f)

        fill shouldBe LayerFill.Solid(Color.Gray)
    }

    @Test
    fun `two solid ends of a morph blend color by color`() {
        val fill = fillFor(NetworkGraphPalette.fromSpec(fallback = Color.Gray), redLayer, blueLayer, fraction = HALF_WAY)

        fill shouldBe LayerFill.Solid(lerp(Color.Red, Color.Blue, HALF_WAY))
    }

    @Test
    fun `a uniform palette keeps its exact color part way through a morph`() {
        val fill = fillFor(NetworkGraphPalette.uniform(Color.White), redLayer, blueLayer, fraction = HALF_WAY)

        fill shouldBe LayerFill.Solid(Color.White)
    }

    @Test
    fun `a gradient palette masks a still layer with its brush`() {
        val fill = fillFor(NetworkGraphPalette.gradient(fromGradient), redLayer, redLayer, fraction = 0f)

        fill shouldBe LayerFill.Masked(fromGradient)
    }

    @Test
    fun `two gradient ends of a morph crossfade by the morph fraction`() {
        val palette = NetworkGraphPalette { context -> if (context.side == From) fromGradient else toGradient }

        val fill = fillFor(palette, redLayer, blueLayer, fraction = HALF_WAY)

        fill shouldBe LayerFill.Masked(fromGradient, overlay = toGradient, overlayAlpha = HALF_WAY)
    }

    @Test
    fun `a solid end morphing into a gradient end crossfades rather than blending colors`() {
        val palette = NetworkGraphPalette { context -> if (context.side == From) SolidColor(Color.Red) else toGradient }

        val fill = fillFor(palette, redLayer, blueLayer, fraction = HALF_WAY)

        fill.shouldBeInstanceOf<LayerFill.Masked>().overlay shouldBe toGradient
    }

    @Test
    fun `a still layer asks the palette only about its from end`() {
        val askedSides = mutableListOf<NetworkGraphMorphSide>()
        val palette = NetworkGraphPalette { context -> SolidColor(Color.Red).also { askedSides += context.side } }

        fillFor(palette, redLayer, redLayer, fraction = 0f)

        askedSides shouldBe listOf(From)
    }

    @Test
    fun `a finished morph asks the palette only about its to end`() {
        val askedSides = mutableListOf<NetworkGraphMorphSide>()
        val palette = NetworkGraphPalette { context -> SolidColor(Color.Red).also { askedSides += context.side } }

        fillFor(palette, redLayer, blueLayer, fraction = 1f)

        askedSides shouldBe listOf(To)
    }

    @Test
    fun `the palette sees each end's own spec, the surface size and the time`() {
        val contexts = mutableListOf<NetworkGraphLayerContext>()
        val palette = NetworkGraphPalette { context -> SolidColor(Color.Red).also { contexts += context } }

        fillFor(palette, redLayer, blueLayer, fraction = HALF_WAY)

        contexts.map { it.layer } shouldBe listOf(redLayer, blueLayer)
        contexts.map { it.size }.distinct() shouldBe listOf(size)
        contexts.map { it.timeSeconds }.distinct() shouldBe listOf(TIME_SECONDS)
    }

    @Test
    fun `a layer that snapped to the to spec asks the palette about its to end`() {
        val from = NetworkGraphSpec(seed = 1, layers = listOf(redLayer))
        val to = NetworkGraphSpec(seed = 2, layers = listOf(blueLayer))
        val askedSides = mutableListOf<NetworkGraphMorphSide>()
        val palette = NetworkGraphPalette { context -> SolidColor(Color.Red).also { askedSides += context.side } }

        layerFill(palette, morphLayers(NetworkGraphMorph(from, to, PAST_SNAP)), index = 0, TIME_SECONDS, size)

        askedSides shouldBe listOf(To)
    }

    private fun fillFor(palette: NetworkGraphPalette, fromLayer: NetworkGraphLayerSpec, toLayer: NetworkGraphLayerSpec, fraction: Float): LayerFill {
        val layer = MorphLayer(SEED, fromLayer, LayerShape.Of(fromLayer), fromLayer, toLayer, fraction)
        return layerFill(palette, listOf(layer), index = 0, TIME_SECONDS, size)
    }

    private companion object {
        const val SEED = 7
        const val HALF_WAY = 0.5f
        const val PAST_SNAP = 0.7f
        const val TIME_SECONDS = 3.5f
        const val SURFACE_WIDTH = 1080f
        const val SURFACE_HEIGHT = 1900f
    }
}
