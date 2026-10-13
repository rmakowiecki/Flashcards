package com.rossomak.networkgraph.tuner

import androidx.compose.ui.graphics.Color
import com.rossomak.networkgraph.NetworkGraphLayerSpec
import com.rossomak.networkgraph.NetworkGraphPresets
import com.rossomak.networkgraph.NetworkGraphSpec
import com.rossomak.networkgraph.ShapePrimitive
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlin.test.Test

/** The hand-written number formatting behind [toKotlinSource], and the source it produces. */
class NetworkGraphSpecSourceTest {

    @Test
    fun `toFixed pads, rounds half up and keeps the sign like a US-locale format`() {
        0.5f.toFixed(2) shouldBe "0.50"
        1.005f.toFixed(1) shouldBe "1.0"
        2.675f.toFixed(0) shouldBe "3"
        0.125f.toFixed(2) shouldBe "0.13"
        (-12.5f).toFixed(1) shouldBe "-12.5"
        16.666f.toFixed(1) shouldBe "16.7"
        0f.toFixed(3) shouldBe "0.000"
    }

    @Test
    fun `float literals read back within the three-decimal rounding of what was written`() {
        sampleValues.forEach { value ->
            val literal = value.toSource()

            literal.removeSuffix("f").toFloat() shouldBe (value plusOrMinus SOURCE_TOLERANCE)
        }
    }

    @Test
    fun `float literals trim trailing zeros but keep one digit after the point`() {
        2f.toSource() shouldBe "2.0f"
        0.5f.toSource() shouldBe "0.5f"
        0.1234f.toSource() shouldBe "0.123f"
        (-45f).toSource() shouldBe "-45.0f"
    }

    @Test
    fun `colors print as eight uppercase ARGB hex digits`() {
        Color(0xFF2A2E8F).toArgbHex() shouldBe "FF2A2E8F"
        Color(0x0A000000).toArgbHex() shouldBe "0A000000"
        Color.White.toArgbHex() shouldBe "FFFFFFFF"
    }

    @Test
    fun `a default layer prints as a bare constructor call`() {
        val spec = NetworkGraphSpec(seed = 1, layers = listOf(NetworkGraphLayerSpec()))

        spec.toKotlinSource() shouldContain "        NetworkGraphLayerSpec(),\n"
    }

    @Test
    fun `only fields that differ from the defaults are printed`() {
        val layer = NetworkGraphLayerSpec(density = 12.5f, surfaceColor = Color(0xFF2A2E8F), shapes = listOf(ShapePrimitive.Bloom(x = 0.25f)))
        val source = NetworkGraphSpec(seed = 9, layers = listOf(layer)).toKotlinSource()

        source shouldContain "density = 12.5f"
        source shouldContain "surfaceColor = Color(0xFF2A2E8F)"
        source shouldContain "shapes = listOf(ShapePrimitive.Bloom(x = 0.25f))"
        source shouldNotContain "fillChance"
        source shouldContain "NetworkGraphSpec(\n    seed = 9,"
    }

    @Test
    fun `every preset exports without a JVM formatter`() {
        NetworkGraphPresets.all.forEach { spec ->
            spec.toKotlinSource() shouldContain "seed = ${spec.seed},"
        }
    }

    private companion object {
        const val SOURCE_TOLERANCE = 0.0005f
        val sampleValues = listOf(0f, 0.001f, 0.0049f, 0.3f, 0.95f, 1f, 2.75f, 12.5f, 48f, -45f, -0.6667f)
    }
}
