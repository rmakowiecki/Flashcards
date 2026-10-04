package com.rossomak.flashcards.core.domain.model

import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.junit.Test

class SessionTerminationTest {

    private val startedAt = Instant.parse("2026-09-06T10:00:00Z")

    private fun cardResult(cardId: String): FlashcardResult.Rated = FlashcardResult.Rated(
        cardId = cardId,
        subcategoryId = "sub-1",
        state = FlashcardStudyProgressState.Seen,
        attemptsUsed = 0,
        wasPreviouslyMastered = false,
    )

    private fun placeholderResult(cardResults: List<FlashcardResult.Rated> = listOf(cardResult("card-1"))): SessionResult.Rated =
        SessionResult.Rated(
            id = "session-1",
            startedAt = startedAt,
            durationSeconds = -1, // deliberately wrong, so a passing test proves it was overwritten
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

    @Test
    fun `sealSessionResult stops the clock and overwrites durationSeconds with its elapsed seconds`() {
        val runningClock = startClock(SessionClock(), startedAt)

        val result = sealSessionResult(
            result = placeholderResult(),
            clock = runningClock,
            at = startedAt.plusSeconds(90),
        )

        result.durationSeconds shouldBe 90
    }

    @Test
    fun `sealSessionResult carries every other field of result straight through unchanged`() {
        val cardResults = listOf(cardResult("card-1"), cardResult("card-2"))
        val placeholder = placeholderResult(cardResults).copy(abandoned = true)

        val result = sealSessionResult(result = placeholder, clock = SessionClock(), at = startedAt)

        val resolved = result as SessionResult.Rated
        resolved.copy(durationSeconds = placeholder.durationSeconds) shouldBe placeholder
    }

    @Test
    fun `sealSessionResult on a clock that never started reports zero duration`() {
        val result = sealSessionResult(
            result = placeholderResult(cardResults = emptyList()),
            clock = SessionClock(),
            at = startedAt.plusSeconds(999),
        )

        result.durationSeconds shouldBe 0
    }
}
