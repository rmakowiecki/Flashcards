package com.rossomak.flashcards.core.domain.scoring

import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.SessionResult
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Runs every Card Progress merge case shared with the Cloud Functions test suite through
 * [mergeSessionIntoCardProgress], so the client's projection and the server's authoritative merge are
 * checked against the same expectations. Add new scenarios to the shared file (see
 * [CardProgressMergeCases]), not here.
 */
@RunWith(Parameterized::class)
class CardProgressMergeTest(
    @Suppress("UNUSED_PARAMETER") caseName: String,
    private val mergeCase: CardProgressMergeCase,
) {

    @Test
    fun `matches the shared expectation`() {
        val expected = mergeCase.expected

        val mergeResult = mergeSessionIntoCardProgress(mergeCase.input.priorCards, mergeCase.input.session.toSessionResult())

        mergeResult.cardUpdatesBySubcategory shouldBe expected.cardUpdates.mapValues { (_, cards) -> cards.mapValues { (_, update) -> update.toDomain() } }
        mergeResult.summaryDeltas shouldBe expected.summaryDeltas.mapValues { (_, delta) -> delta.toDomain() }
        mergeResult.newCardsStudied shouldBe expected.newCardsStudied
        mergeResult.previouslyMasteredByCardId shouldBe expected.previouslyMastered
    }

    private fun CardProgressMergeCaseSession.toSessionResult(): SessionResult {
        val subcategoryIds = cardResults.map(CardProgressMergeCaseCardResult::subcategoryId).distinct()
        return when (studyMode) {
            RATED_MODE -> SessionResult.Rated(
                id = SESSION_ID,
                startedAt = STARTED_AT,
                durationSeconds = DURATION_SECONDS,
                abandoned = false,
                categoryId = CATEGORY_ID,
                categoryName = CATEGORY_NAME,
                subcategoryIds = subcategoryIds,
                subcategoryNames = subcategoryIds,
                cardResults = cardResults.map { cardResult ->
                    FlashcardResult.Rated(
                        cardId = cardResult.cardId,
                        subcategoryId = cardResult.subcategoryId,
                        state = cardResult.state,
                        attemptsUsed = 1,
                        wasPreviouslyMastered = requireNotNull(cardResult.wasPreviouslyMastered) { "a Rated card result needs wasPreviouslyMastered" },
                    )
                },
                studyDate = STUDY_DATE,
                studyDateUtcOffsetMinutes = 0,
                dailyGoalMinutes = DAILY_GOAL_MINUTES,
            )
            FAST_MODE -> SessionResult.Fast(
                id = SESSION_ID,
                startedAt = STARTED_AT,
                durationSeconds = DURATION_SECONDS,
                abandoned = false,
                categoryId = CATEGORY_ID,
                categoryName = CATEGORY_NAME,
                subcategoryIds = subcategoryIds,
                subcategoryNames = subcategoryIds,
                cardResults = cardResults.map { cardResult ->
                    FlashcardResult.Fast(cardId = cardResult.cardId, subcategoryId = cardResult.subcategoryId, state = cardResult.state)
                },
                studyDate = STUDY_DATE,
                studyDateUtcOffsetMinutes = 0,
                dailyGoalMinutes = DAILY_GOAL_MINUTES,
            )
            else -> error("unknown study mode \"$studyMode\"")
        }
    }

    companion object {
        private const val RATED_MODE = "Rated"
        private const val FAST_MODE = "Fast"

        // Identity, timing and calendar fields the merge never reads; the shared cases leave them out.
        private const val SESSION_ID = "session-1"
        private const val CATEGORY_ID = "cat-1"
        private const val CATEGORY_NAME = "Category"
        private const val DURATION_SECONDS = 60
        private const val STUDY_DATE = "2026-09-06"
        private const val DAILY_GOAL_MINUTES = 20
        private val STARTED_AT = Instant.parse("2026-09-06T10:00:00Z")

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = CardProgressMergeCases.file.cases.map { arrayOf(it.name, it) }
    }
}
