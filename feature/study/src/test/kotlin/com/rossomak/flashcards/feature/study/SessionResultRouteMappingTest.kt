package com.rossomak.flashcards.feature.study

import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SessionSourceType
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Custom
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Quick
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.junit.Test

/**
 * Not an encoding test (the spec is explicit that route-argument encoding is not what's being
 * asserted) — this only checks that flattening onto [StudySessionSummaryRoute] and reading it back
 * loses nothing.
 */
class SessionResultRouteMappingTest {

    // studyDate/dailyGoalMinutes are deliberately not carried by StudySessionSummaryRoute —
    // toSessionResult() takes dailyGoalMinutes as a fresh parameter instead of reading it off the
    // route, and derives studyDate itself from startedAt + studyDateUtcOffsetMinutes.
    // studyDateUtcOffsetMinutes IS carried by the route, so it must round-trip unchanged — -300
    // deliberately chosen so 2026-09-06T10:00:00Z shifted by it still lands on 2026-09-06, keeping
    // studyDate below consistent with what toSessionResult() will itself derive.
    private val studyDate = "2026-09-06"
    private val studyDateUtcOffsetMinutes = -300
    private val dailyGoalMinutes = 20

    private val ratedResult = SessionResult.Rated(
        id = "session-1",
        startedAt = Instant.parse("2026-09-06T10:00:00Z"),
        durationSeconds = 90,
        abandoned = true,
        categoryId = "cat-1",
        categoryName = "Category",
        subcategoryIds = listOf("sub-1", "sub-2"),
        subcategoryNames = listOf("Subcategory 1", "Subcategory 2"),
        sourceType = Custom,
        cardResults = listOf(
            FlashcardResult.Rated(
                cardId = "card-1",
                subcategoryId = "sub-1",
                state = FlashcardStudyProgressState.Mastered,
                attemptsUsed = 1,
                wasPreviouslyMastered = false,
            ),
            FlashcardResult.Rated(
                cardId = "card-2",
                subcategoryId = "sub-2",
                state = FlashcardStudyProgressState.Partial,
                attemptsUsed = 2,
                wasPreviouslyMastered = true,
            ),
        ),
        studyDate = studyDate,
        studyDateUtcOffsetMinutes = studyDateUtcOffsetMinutes,
        dailyGoalMinutes = dailyGoalMinutes,
    )

    private val fastResult = SessionResult.Fast(
        id = "session-2",
        startedAt = Instant.parse("2026-09-06T10:00:00Z"),
        durationSeconds = 90,
        abandoned = true,
        categoryId = "cat-1",
        categoryName = "Category",
        subcategoryIds = listOf("sub-1", "sub-2"),
        subcategoryNames = listOf("Subcategory 1", "Subcategory 2"),
        sourceType = Quick,
        cardResults = listOf(
            FlashcardResult.Fast(cardId = "card-1", subcategoryId = "sub-1", state = FlashcardStudyProgressState.Seen),
            FlashcardResult.Fast(cardId = "card-2", subcategoryId = "sub-2", state = FlashcardStudyProgressState.Seen),
        ),
        studyDate = studyDate,
        studyDateUtcOffsetMinutes = studyDateUtcOffsetMinutes,
        dailyGoalMinutes = dailyGoalMinutes,
    )

    @Test
    fun `a Rated SessionResult survives a round trip through StudySessionSummaryRoute unchanged`() {
        val roundTripped = ratedResult.toSummaryRoute().toSessionResult(dailyGoalMinutes)

        roundTripped shouldBe ratedResult
    }

    @Test
    fun `empty cardResults survive the round trip as empty cardResults`() {
        val empty = ratedResult.copy(cardResults = emptyList())

        empty.toSummaryRoute().toSessionResult(dailyGoalMinutes) shouldBe empty
    }

    @Test
    fun `a Fast SessionResult flattens attemptsUsed and wasPreviouslyMastered to null and round-trips unchanged`() {
        val route = fastResult.toSummaryRoute()

        route.cardAttemptsUsed shouldBe null
        route.cardWasPreviouslyMastered shouldBe null
        route.toSessionResult(dailyGoalMinutes) shouldBe fastResult
    }

    @Test
    fun `every source type survives the round trip for both Study Modes`() {
        SessionSourceType.entries.forEach { sourceType ->
            val rated = ratedResult.copy(sourceType = sourceType)
            val fast = fastResult.copy(sourceType = sourceType)

            withClue(sourceType) {
                rated.toSummaryRoute().toSessionResult(dailyGoalMinutes) shouldBe rated
                fast.toSummaryRoute().toSessionResult(dailyGoalMinutes) shouldBe fast
            }
        }
    }
}
