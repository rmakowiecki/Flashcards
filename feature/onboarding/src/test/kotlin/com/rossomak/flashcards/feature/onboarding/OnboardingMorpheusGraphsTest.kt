package com.rossomak.flashcards.feature.onboarding

import com.rossomak.morpheus.EdgeRule
import com.rossomak.morpheus.isSameMorphFamily
import io.kotest.matchers.shouldBe
import org.junit.Test

class OnboardingMorpheusGraphsTest {

    private val pages = OnboardingMorpheusGraphs.pages

    @Test
    fun `every step's graph is one fixed, strict layer that inherits its fill`() {
        pages.forEach { spec ->
            spec.layers.size shouldBe 1
            spec.fill shouldBe null
            with(spec.layers.single()) {
                edgeRule shouldBe EdgeRule.FixedTriangulation
                strictTriangles shouldBe true
                fill shouldBe null
            }
        }
    }

    @Test
    fun `any two steps morph within one mesh, so a jump from the first to the last does too`() {
        pages.forEach { from -> pages.forEach { to -> from.isSameMorphFamily(to) shouldBe true } }
    }

    @Test
    fun `every step has a graph of its own`() {
        pages.distinct().size shouldBe OnboardingStep.entries.size
    }
}
