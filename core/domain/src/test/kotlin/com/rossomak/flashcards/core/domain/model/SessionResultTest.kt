package com.rossomak.flashcards.core.domain.model

import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.junit.Test

class SessionResultTest {

    private fun ratedEntry(
        cardId: String,
        state: FlashcardStudyProgressState,
        attemptsUsed: Int = 0,
        wasPreviouslyMastered: Boolean = false,
    ): FlashcardResult.Rated = FlashcardResult.Rated(
        cardId = cardId,
        subcategoryId = "sub-1",
        state = state,
        attemptsUsed = attemptsUsed,
        wasPreviouslyMastered = wasPreviouslyMastered,
    )

    private fun ratedResult(cardResults: List<FlashcardResult.Rated>): SessionResult.Rated = SessionResult.Rated(
        id = "session-1",
        startedAt = Instant.parse("2026-09-06T10:00:00Z"),
        durationSeconds = 60,
        abandoned = false,
        categoryId = "cat-1",
        categoryName = "Category",
        subcategoryIds = listOf("sub-1"),
        subcategoryNames = listOf("Subcategory"),
        sourceType = SingleSubcategory,
        cardResults = cardResults,
        studyDate = "2026-09-06",
        dailyGoalMinutes = 20,
        studyDateUtcOffsetMinutes = 0,
        voiceAnsweringEnabled = false,
    )

    private fun fastResult(cardResults: List<FlashcardResult.Fast>): SessionResult.Fast = SessionResult.Fast(
        id = "session-2",
        startedAt = Instant.parse("2026-09-06T10:00:00Z"),
        durationSeconds = 60,
        abandoned = false,
        categoryId = "cat-1",
        categoryName = "Category",
        subcategoryIds = listOf("sub-1"),
        subcategoryNames = listOf("Subcategory"),
        sourceType = SingleSubcategory,
        cardResults = cardResults,
        studyDate = "2026-09-06",
        dailyGoalMinutes = 20,
        studyDateUtcOffsetMinutes = 0,
        readAloudEnabled = false,
    )

    @Test
    fun `mode is derived from the sealed branch, never a stored field`() {
        ratedResult(cardResults = emptyList()).mode shouldBe StudyMode.Rated
        fastResult(cardResults = emptyList()).mode shouldBe StudyMode.Fast
    }

    @Test
    fun `a Fast entry carries Seen and no Attempts or previously-mastered field at all`() {
        val entry = FlashcardResult.Fast(cardId = "card-1", subcategoryId = "sub-1", state = FlashcardStudyProgressState.Seen)

        entry.state shouldBe FlashcardStudyProgressState.Seen
    }

    @Test
    fun `Mastered, Partial and Failed counts derive from cardResults and cannot disagree with it`() {
        val session = ratedResult(
            cardResults = listOf(
                ratedEntry(cardId = "card-1", state = FlashcardStudyProgressState.Mastered, attemptsUsed = 1),
                ratedEntry(cardId = "card-2", state = FlashcardStudyProgressState.Mastered, attemptsUsed = 2),
                ratedEntry(cardId = "card-3", state = FlashcardStudyProgressState.Partial, attemptsUsed = 3),
                ratedEntry(cardId = "card-4", state = FlashcardStudyProgressState.Failed, attemptsUsed = 3),
            ),
        )

        session.masteredCount shouldBe 2
        session.partialCount shouldBe 1
        session.failedCount shouldBe 1
        session.studiedCount shouldBe 4
    }

    @Test
    fun `empty cardResults reports every count as zero`() {
        val session = ratedResult(cardResults = emptyList())

        session.studiedCount shouldBe 0
        session.masteredCount shouldBe 0
        session.partialCount shouldBe 0
        session.failedCount shouldBe 0
    }

    @Test
    fun `a Fast result's studiedCount counts every seen card, with no mastered, partial or failed count to read at all`() {
        val session = fastResult(
            cardResults = listOf(
                FlashcardResult.Fast(cardId = "card-1", subcategoryId = "sub-1", state = FlashcardStudyProgressState.Seen),
                FlashcardResult.Fast(cardId = "card-2", subcategoryId = "sub-1", state = FlashcardStudyProgressState.Seen),
            ),
        )

        session.studiedCount shouldBe 2
        // No masteredCount/partialCount/failedCount assertion here on purpose: SessionResult.Fast has
        // no such properties to read — sealing makes that a compile error, not a runtime zero.
    }
}
