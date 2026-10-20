package com.rossomak.flashcards.feature.study

import com.rossomak.flashcards.core.ui.backdrop.BrandMorpheusGraphSpecs
import com.rossomak.morpheus.isSameMorphFamily
import io.kotest.matchers.shouldBe
import org.junit.Test

class StudyMorpheusGraphsTest {

    private val graphs = with(StudyMorpheusGraphs) { listOf(session, summaryCounting, summary) }

    @Test
    fun `every brand study graph morphs within the brand mesh, the Summary's own morph included`() {
        graphs.forEach { graph -> graph.isSameMorphFamily(BrandMorpheusGraphSpecs.topAppBar) shouldBe true }
        StudyMorpheusGraphs.summaryCounting.isSameMorphFamily(StudyMorpheusGraphs.summary) shouldBe true
    }

    @Test
    fun `every brand study graph is the brand layer in a shape of its own`() {
        graphs.forEach { graph -> graph.layers.single().copy(shapes = BrandMorpheusGraphSpecs.base.shapes) shouldBe BrandMorpheusGraphSpecs.base }
        graphs.distinct().size shouldBe graphs.size
    }
}
