package com.rossomak.flashcards.web.landing

import com.rossomak.morpheus.MorpheusGraphLayerSpec
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Bloom
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class LandingTuningTest {
    private val pages = LandingGraphs.pages

    @Test
    fun `an edit of a field reaches every page and leaves each page's other fields`() {
        val edited = pages[EDITED_PAGE].withFrontLayer { copy(density = EDITED_DENSITY, fillChance = EDITED_FILL_CHANCE) }

        val result = pages.withPageEdit(EDITED_PAGE, edited)

        result.forEachIndexed { index, spec ->
            spec.layers.last().density shouldBe EDITED_DENSITY
            spec.layers.last().fillChance shouldBe EDITED_FILL_CHANCE
            spec.layers.last().hotNodeShare shouldBe pages[index].layers.last().hotNodeShare
            spec.layers.last().alpha shouldBe pages[index].layers.last().alpha
            spec.layers.first() shouldBe pages[index].layers.first()
        }
    }

    @Test
    fun `an edit of the shapes stays on the edited page`() {
        val edited = pages[EDITED_PAGE].withFrontLayer { copy(shapes = shapes + Bloom(x = 0.5f, y = 0.5f, reach = 0.2f)) }

        val result = pages.withPageEdit(EDITED_PAGE, edited)

        result[EDITED_PAGE] shouldBe edited
        result.forEachIndexed { index, spec -> if (index != EDITED_PAGE) spec shouldBe pages[index] }
    }

    @Test
    fun `every page stays in one morph family after an edit of the grid`() {
        val edited = pages[EDITED_PAGE].withFrontLayer { copy(density = EDITED_DENSITY, jitter = EDITED_JITTER) }

        val result = pages.withPageEdit(EDITED_PAGE, edited)

        result.forEach { spec ->
            spec.layers.last().density shouldBe EDITED_DENSITY
            spec.layers.last().jitter shouldBe EDITED_JITTER
        }
    }

    @Test
    fun `adding a layer adds it to every page`() {
        val edited = pages[EDITED_PAGE].copy(layers = listOf(pages[EDITED_PAGE].layers.first()) + pages[EDITED_PAGE].layers)

        val result = pages.withPageEdit(EDITED_PAGE, edited)

        result.forEach { spec -> spec.layers.size shouldBe pages[EDITED_PAGE].layers.size + 1 }
    }

    private fun MorpheusGraphSpec.withFrontLayer(change: MorpheusGraphLayerSpec.() -> MorpheusGraphLayerSpec): MorpheusGraphSpec =
        copy(layers = layers.dropLast(1) + layers.last().change())

    private companion object {
        const val EDITED_PAGE = 3
        const val EDITED_DENSITY = 31f
        const val EDITED_FILL_CHANCE = 0.55f
        const val EDITED_JITTER = 0.3f
    }
}
