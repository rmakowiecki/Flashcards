package com.rossomak.flashcards.core.domain.scoring

import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import com.rossomak.flashcards.core.domain.model.levelThreshold
import io.kotest.matchers.shouldBe
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Runs every XP scoring case shared with the Cloud Functions test suite through [calculateSessionXp],
 * [calculateStreakAndGoalAwards] and [levelThreshold], with the same inputs the TypeScript runner
 * gives its counterparts, so the client's calculation and the server's authoritative one are checked
 * against the same expectations. Add new scenarios to the shared file (see [XpScoringCases]), not here.
 */
@RunWith(Parameterized::class)
class XpScoringCasesTest(
    @Suppress("UNUSED_PARAMETER") caseName: String,
    private val scoringCase: XpScoringCase,
) {

    @Test
    fun `matches the shared expectation`() {
        val skipReason = scoringCase.skip[XpScoringCases.RUNNER]
        assumeTrue(skipReason.orEmpty(), skipReason == null)

        when (scoringCase.kind) {
            SESSION_XP_KIND -> assertSessionXp()
            LEVEL_THRESHOLD_KIND -> assertLevelThreshold()
            STREAK_AND_GOAL_KIND -> assertStreakAndGoal()
            else -> error("unknown case kind \"${scoringCase.kind}\"")
        }
    }

    private fun assertSessionXp() = with(scoringCase.input) {
        val expected = Json.decodeFromJsonElement<ExpectedSessionXp>(scoringCase.expected)
        val sessionResult = requireNotNull(session) { "a ${scoringCase.kind} case needs a session" }.toSessionResult()
        val currentState = priorState?.toDomain() ?: ScoringState()

        val streakAndGoalInput = streakAndGoal?.toDomain() ?: NO_STREAK_OR_GOAL

        val xpResult = calculateSessionXp(sessionResult, newCardsStudied, currentState, XpScoringCases.resolveConfig(this), streakAndGoalInput)

        xpResult.breakdown shouldBe expected.breakdown.toDomain()
        xpResult.breakdown.xpTotal shouldBe expected.breakdown.xpTotal
        xpResult.newScoringState shouldBe expected.newScoringState.toDomain()
        xpResult.levelsCrossed shouldBe expected.levelsCrossed
    }

    private fun assertStreakAndGoal() = with(scoringCase.input) {
        val expected = Json.decodeFromJsonElement<ExpectedStreakAndGoal>(scoringCase.expected)
        val input = requireNotNull(streakAndGoal) { "a ${scoringCase.kind} case needs a streakAndGoal input" }.toDomain()

        calculateStreakAndGoalAwards(priorState?.toDomain() ?: ScoringState(), input, XpScoringCases.resolveConfig(this)) shouldBe expected.toDomain()
    }

    private fun assertLevelThreshold() = with(scoringCase.input) {
        val expected = Json.decodeFromJsonElement<ExpectedLevelThreshold>(scoringCase.expected)
        val config = XpScoringCases.resolveConfig(this)

        config.levelThreshold(requireNotNull(level) { "a ${scoringCase.kind} case needs a level" }) shouldBe expected.threshold
    }

    private fun XpScoringCaseSession.toSessionResult(): SessionResult = when (studyMode) {
        RATED_MODE -> SessionResult.Rated(
            id = SESSION_ID,
            startedAt = STARTED_AT,
            durationSeconds = durationSeconds,
            abandoned = abandoned,
            categoryId = CATEGORY_ID,
            categoryName = CATEGORY_NAME,
            subcategoryIds = listOf(SUBCATEGORY_ID),
            subcategoryNames = listOf(SUBCATEGORY_NAME),
            sourceType = SingleSubcategory,
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
            sourceType = SingleSubcategory,
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
        )
        else -> error("unknown study mode \"$studyMode\"")
    }

    companion object {
        private const val SESSION_XP_KIND = "sessionXp"
        private const val LEVEL_THRESHOLD_KIND = "levelThreshold"
        private const val STREAK_AND_GOAL_KIND = "streakAndGoal"
        private const val RATED_MODE = "Rated"
        private const val FAST_MODE = "Fast"

        // Scores without either award: an empty study date is never later than a stored one.
        private val NO_STREAK_OR_GOAL = StreakAndGoalInput(studyDate = "", dailyGoalMinutes = 0, todayTotalSeconds = 0)

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
