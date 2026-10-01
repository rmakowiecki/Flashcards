package com.rossomak.flashcards.core.domain.model

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test

class SubcategoryProgressStateTest {

    private val subcategoryId = "sub-1"

    private val noSummary: ProgressSummary? = null

    private val summary = ProgressSummary(
        subcategories = mapOf(subcategoryId to SubcategoryProgressSummary(masteredCount = 2, studiedCount = 5)),
    )

    @Test
    fun `studiedFraction is zero when nothing is studied`() {
        SubcategoryProgressState.Resolved(studiedCount = 0, masteredCount = 0).studiedFraction(cardCount = 10) shouldBe 0f
    }

    @Test
    fun `studiedFraction is studied over card count`() {
        SubcategoryProgressState.Resolved(studiedCount = 5, masteredCount = 0).studiedFraction(cardCount = 10) shouldBe 0.5f
    }

    @Test
    fun `studiedFraction is one when every card is studied`() {
        SubcategoryProgressState.Resolved(studiedCount = 10, masteredCount = 0).studiedFraction(cardCount = 10) shouldBe 1f
    }

    @Test
    fun `studiedFraction clamps to one when studied exceeds card count`() {
        SubcategoryProgressState.Resolved(studiedCount = 12, masteredCount = 0).studiedFraction(cardCount = 10) shouldBe 1f
    }

    @Test
    fun `studiedFraction is zero for a zero card count`() {
        SubcategoryProgressState.Resolved(studiedCount = 3, masteredCount = 0).studiedFraction(cardCount = 0) shouldBe 0f
    }

    @Test
    fun `not resolved gives Unresolved even when the summary holds an entry`() {
        summary.subcategoryProgressFor(subcategoryId, isResolved = false) shouldBe SubcategoryProgressState.Unresolved
    }

    @Test
    fun `resolved with a present entry carries its counts`() {
        summary.subcategoryProgressFor(subcategoryId, isResolved = true) shouldBe
            SubcategoryProgressState.Resolved(studiedCount = 5, masteredCount = 2)
    }

    @Test
    fun `resolved with an absent entry is resolved zero`() {
        summary.subcategoryProgressFor("sub-other", isResolved = true) shouldBe
            SubcategoryProgressState.Resolved(studiedCount = 0, masteredCount = 0)
    }

    @Test
    fun `resolved with a null summary is resolved zero`() {
        noSummary.subcategoryProgressFor(subcategoryId, isResolved = true) shouldBe
            SubcategoryProgressState.Resolved(studiedCount = 0, masteredCount = 0)
    }

    @Test
    fun `resolved zero is distinguishable from Unresolved`() {
        noSummary.subcategoryProgressFor(subcategoryId, isResolved = true) shouldNotBe
            noSummary.subcategoryProgressFor(subcategoryId, isResolved = false)
    }
}
