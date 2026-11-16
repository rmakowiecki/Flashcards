package com.rossomak.flashcards.web.landing

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class LandingGraphsTest {

    @Test
    fun `there is one graph per landing page`() {
        LandingGraphs.pages.size shouldBe LANDING_PAGE_COUNT
    }

    @Test
    fun `every graph stays in one morph family so the nodes stay put`() {
        val first = LandingGraphs.pages.first()
        LandingGraphs.pages.forEach { spec ->
            spec.seed shouldBe first.seed
            spec.layers.size shouldBe first.layers.size
            spec.layers.zip(first.layers).forEach { (layer, firstLayer) ->
                layer.density shouldBe firstLayer.density
                layer.jitter shouldBe firstLayer.jitter
                layer.edgeRule shouldBe firstLayer.edgeRule
                layer.strictTriangles shouldBe firstLayer.strictTriangles
            }
        }
    }
}
