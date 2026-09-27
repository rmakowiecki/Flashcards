package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.model.levelThreshold
import com.rossomak.flashcards.core.domain.xpscoring.ExpectedLevelThreshold
import com.rossomak.flashcards.core.domain.xpscoring.ExpectedSessionXp
import com.rossomak.flashcards.core.domain.xpscoring.XpScoringCase
import com.rossomak.flashcards.core.domain.xpscoring.XpScoringCaseSession
import com.rossomak.flashcards.core.domain.xpscoring.XpScoringCases
import io.kotest.matchers.shouldBe
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Runs every XP scoring case shared with the Cloud Functions test suite through
 * [CalculateSessionXpUseCase] (and [levelThreshold], which the use case's level-up loop reads), so this
 * preview calculation and the server's authoritative one are checked against the same expectations.
 * Add new scenarios to the shared file (see [XpScoringCases]), not here.
 */
@RunWith(Parameterized::class)
class CalculateSessionXpUseCaseTest(
    @Suppress("UNUSED_PARAMETER") caseName: String,
    private val scoringCase: XpScoringCase,
) {

    private val useCase = CalculateSessionXpUseCase()

    @Test
    fun `matches the shared expectation`() = runTest {
        val skipReason = scoringCase.skip[XpScoringCases.RUNNER]
        assumeTrue(skipReason.orEmpty(), skipReason == null)

        when (scoringCase.kind) {
            SESSION_XP_KIND -> assertSessionXp()
            LEVEL_THRESHOLD_KIND -> assertLevelThreshold()
            else -> error("unknown case kind \"${scoringCase.kind}\"")
        }
    }

    private suspend fun assertSessionXp() = with(scoringCase.input) {
        val expected = Json.decodeFromJsonElement<ExpectedSessionXp>(scoringCase.expected)
        val sessionResult = requireNotNull(session) { "a ${scoringCase.kind} case needs a session" }
            .toSessionResult(XpScoringCases.resolveConfig(this))
        val currentState = priorState?.toDomain() ?: ScoringState()

        val xpResult = useCase(CalculateSessionXpUseCase.Params(sessionResult, newCardsStudied, currentState))

        xpResult.breakdown shouldBe expected.breakdown.toDomain()
        xpResult.breakdown.xpTotal shouldBe expected.breakdown.xpTotal
        xpResult.newScoringState shouldBe expected.newScoringState.toDomain()
        xpResult.levelsCrossed shouldBe expected.levelsCrossed
    }

    private fun assertLevelThreshold() = with(scoringCase.input) {
        val expected = Json.decodeFromJsonElement<ExpectedLevelThreshold>(scoringCase.expected)
        val config = XpScoringCases.resolveConfig(this)

        config.levelThreshold(requireNotNull(level) { "a ${scoringCase.kind} case needs a level" }) shouldBe expected.threshold
    }

    private fun XpScoringCaseSession.toSessionResult(config: XpConfig): SessionResult = when (studyMode) {
        RATED_MODE -> SessionResult.Rated(
            id = SESSION_ID,
            startedAt = STARTED_AT,
            durationSeconds = durationSeconds,
            abandoned = abandoned,
            categoryId = CATEGORY_ID,
            categoryName = CATEGORY_NAME,
            subcategoryIds = listOf(SUBCATEGORY_ID),
            subcategoryNames = listOf(SUBCATEGORY_NAME),
            cardResults = cardResults.mapIndexed { index, cardResult ->
                FlashcardResult.Rated(
                    cardId = cardId(index),
                    subcategoryId = SUBCATEGORY_ID,
                    state = FlashcardStudyProgressState.valueOf(cardResult.state),
                    attemptsUsed = 1,
                    wasPreviouslyMastered = cardResult.wasPreviouslyMastered,
                )
            },
            studyDate = STUDY_DATE,
            studyDateUtcOffsetMinutes = 0,
            dailyGoalMinutes = DAILY_GOAL_MINUTES,
            xpConfig = config,
        )
        FAST_MODE -> SessionResult.Fast(
            id = SESSION_ID,
            startedAt = STARTED_AT,
            durationSeconds = durationSeconds,
            abandoned = abandoned,
            categoryId = CATEGORY_ID,
            categoryName = CATEGORY_NAME,
            subcategoryIds = listOf(SUBCATEGORY_ID),
            subcategoryNames = listOf(SUBCATEGORY_NAME),
            cardResults = cardResults.mapIndexed { index, cardResult ->
                FlashcardResult.Fast(
                    cardId = cardId(index),
                    subcategoryId = SUBCATEGORY_ID,
                    state = FlashcardStudyProgressState.valueOf(cardResult.state),
                )
            },
            studyDate = STUDY_DATE,
            studyDateUtcOffsetMinutes = 0,
            dailyGoalMinutes = DAILY_GOAL_MINUTES,
            xpConfig = config,
        )
        else -> error("unknown study mode \"$studyMode\"")
    }

    companion object {
        private const val SESSION_XP_KIND = "sessionXp"
        private const val LEVEL_THRESHOLD_KIND = "levelThreshold"
        private const val RATED_MODE = "Rated"
        private const val FAST_MODE = "Fast"

        // Identity and calendar fields the calculation never reads; the shared cases leave them out.
        private const val SESSION_ID = "session-1"
        private const val CATEGORY_ID = "cat-1"
        private const val CATEGORY_NAME = "Category"
        private const val SUBCATEGORY_ID = "sub-1"
        private const val SUBCATEGORY_NAME = "Subcategory"
        private const val STUDY_DATE = "2026-09-06"
        private const val DAILY_GOAL_MINUTES = 20
        private val STARTED_AT = Instant.parse("2026-09-06T10:00:00Z")

        private fun cardId(index: Int) = "card-${index + 1}"

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = XpScoringCases.file.cases.map { arrayOf(it.name, it) }
    }
}
