package com.rossomak.flashcards.core.domain.scoring

import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState.Failed
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState.Mastered
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState.Partial
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState.Seen
import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SessionScoreCounts
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.model.levelThreshold
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.junit.Test

class SessionScoringTest {

    @Test
    fun `a card Mastered before the session scores as defended even when the session's own flag says it was not`() {
        val session = ratedSession(RatedCard(CARD_ID, Mastered, wasPreviouslyMastered = false))

        val scoring = scoreSession(priorStates(CARD_ID to Mastered), ScoringState(), session, CONFIG)

        scoring.score.breakdown.masteryDefenseBonus shouldBe CONFIG.masteryDefended
        scoring.score.breakdown.mastered shouldBe 0
        scoring.score.counts?.defended shouldBe 1
        scoring.score.counts?.newlyMastered shouldBe 0
    }

    @Test
    fun `a card with no record scores as freshly Mastered even when the session's own flag says it was Mastered`() {
        val session = ratedSession(RatedCard(CARD_ID, Mastered, wasPreviouslyMastered = true))

        val scoring = scoreSession(emptyMap(), ScoringState(), session, CONFIG)

        scoring.score.breakdown.mastered shouldBe CONFIG.cardMastered
        scoring.score.breakdown.masteryDefenseBonus shouldBe 0
        scoring.score.counts?.newlyMastered shouldBe 1
        scoring.score.counts?.defended shouldBe 0
    }

    @Test
    fun `a card Failed after being Mastered before the session is de-mastered even when the session's own flag says it was not`() {
        val session = ratedSession(RatedCard(CARD_ID, Failed, wasPreviouslyMastered = false))

        val scoring = scoreSession(priorStates(CARD_ID to Mastered), ScoringState(), session, CONFIG)

        scoring.score.breakdown.demastered shouldBe CONFIG.cardDemastered
        scoring.score.counts?.demastered shouldBe 1
    }

    @Test
    fun `each multiplied line's count times its rate matches its amount`() {
        val session = ratedSession(
            RatedCard(CARD_ID, Mastered),
            RatedCard(DEFENDED_CARD_ID, Mastered),
            RatedCard(PARTIAL_CARD_ID, Partial),
            RatedCard(DEMASTERED_CARD_ID, Failed),
        )
        val prior = priorStates(DEFENDED_CARD_ID to Mastered, DEMASTERED_CARD_ID to Mastered)

        val score = scoreSession(prior, ScoringState(), session, CONFIG).score
        val counts = requireNotNull(score.counts)
        val rates = requireNotNull(score.rates)

        counts shouldBe SessionScoreCounts(newCardsStudied = 2, newlyMastered = 1, partial = 1, defended = 1, demastered = 1)
        score.breakdown.newCards shouldBe counts.newCardsStudied * rates.newCardStudied
        score.breakdown.mastered shouldBe requireNotNull(counts.newlyMastered) * rates.cardMastered
        score.breakdown.partial shouldBe requireNotNull(counts.partial) * rates.cardPartial
        score.breakdown.masteryDefenseBonus shouldBe requireNotNull(counts.defended) * rates.masteryDefended
        score.breakdown.demastered shouldBe requireNotNull(counts.demastered) * rates.cardDemastered
    }

    @Test
    fun `the rates come from the config`() {
        val rates = requireNotNull(scoreSession(emptyMap(), ScoringState(), ratedSession(RatedCard(CARD_ID, Mastered)), CONFIG).score.rates)

        rates.newCardStudied shouldBe CONFIG.newCardStudied
        rates.cardMastered shouldBe CONFIG.cardMastered
        rates.cardPartial shouldBe CONFIG.cardPartial
        rates.masteryDefended shouldBe CONFIG.masteryDefended
        rates.cardDemastered shouldBe CONFIG.cardDemastered
        rates.minuteStudied shouldBe CONFIG.minuteStudied
        rates.sessionCompleted shouldBe CONFIG.sessionCompleted
    }

    @Test
    fun `a Fast session counts new cards and has no Rated counts`() {
        val session = fastSession(CARD_ID, OTHER_CARD_ID)

        val scoring = scoreSession(priorStates(CARD_ID to Mastered), ScoringState(), session, CONFIG)

        scoring.score.counts shouldBe SessionScoreCounts(newCardsStudied = 1, newlyMastered = null, partial = null, defended = null, demastered = null)
        scoring.score.breakdown.newCards shouldBe CONFIG.newCardStudied
        scoring.cardProgressMerge.cardUpdatesBySubcategory[SUBCATEGORY_ID]?.keys shouldBe setOf(OTHER_CARD_ID)
    }

    @Test
    fun `the new scoring state and the Level position come from applying the award, with the next threshold from the config`() {
        val startingState = ScoringState(xp = STARTING_XP, level = 1, xpIntoCurrentLevel = STARTING_XP)
        val session = ratedSession(RatedCard(CARD_ID, Mastered))

        val scoring = scoreSession(emptyMap(), startingState, session, CONFIG)
        val xpTotal = scoring.score.breakdown.xpTotal.toLong()

        scoring.newScoringState.xp shouldBe STARTING_XP + xpTotal
        scoring.newScoringState.level shouldBe 2
        scoring.score.levelsCrossed shouldBe listOf(2)
        scoring.score.level shouldBe 2
        scoring.score.xpIntoCurrentLevel shouldBe STARTING_XP + xpTotal - CONFIG.levelThreshold(1)
        scoring.score.xpForNextLevel shouldBe CONFIG.levelThreshold(2)
    }

    @Test
    fun `the Card Progress merge result is returned as the merge computes it`() {
        val session = ratedSession(RatedCard(CARD_ID, Failed))
        val prior = priorStates(CARD_ID to Mastered)

        scoreSession(prior, ScoringState(), session, CONFIG).cardProgressMerge shouldBe mergeSessionIntoCardProgress(prior, session)
    }

    private data class RatedCard(val cardId: String, val state: FlashcardStudyProgressState, val wasPreviouslyMastered: Boolean = false)

    private fun priorStates(vararg cards: Pair<String, FlashcardStudyProgressState>) = mapOf(SUBCATEGORY_ID to cards.toMap())

    private fun ratedSession(vararg cards: RatedCard) = SessionResult.Rated(
        id = SESSION_ID,
        startedAt = STARTED_AT,
        durationSeconds = DURATION_SECONDS,
        abandoned = false,
        categoryId = CATEGORY_ID,
        categoryName = "Category",
        subcategoryIds = listOf(SUBCATEGORY_ID),
        subcategoryNames = listOf("Subcategory"),
        cardResults = cards.map { card ->
            FlashcardResult.Rated(
                cardId = card.cardId,
                subcategoryId = SUBCATEGORY_ID,
                state = card.state,
                attemptsUsed = 1,
                wasPreviouslyMastered = card.wasPreviouslyMastered,
            )
        },
        studyDate = STUDY_DATE,
        studyDateUtcOffsetMinutes = 0,
        dailyGoalMinutes = DAILY_GOAL_MINUTES,
    )

    private fun fastSession(vararg cardIds: String) = SessionResult.Fast(
        id = SESSION_ID,
        startedAt = STARTED_AT,
        durationSeconds = DURATION_SECONDS,
        abandoned = false,
        categoryId = CATEGORY_ID,
        categoryName = "Category",
        subcategoryIds = listOf(SUBCATEGORY_ID),
        subcategoryNames = listOf("Subcategory"),
        cardResults = cardIds.map { cardId -> FlashcardResult.Fast(cardId = cardId, subcategoryId = SUBCATEGORY_ID, state = Seen) },
        studyDate = STUDY_DATE,
        studyDateUtcOffsetMinutes = 0,
        dailyGoalMinutes = DAILY_GOAL_MINUTES,
    )

    private companion object {
        const val SESSION_ID = "session-1"
        const val CATEGORY_ID = "cat-1"
        const val SUBCATEGORY_ID = "sub-1"
        const val CARD_ID = "card-1"
        const val OTHER_CARD_ID = "card-2"
        const val DEFENDED_CARD_ID = "card-defended"
        const val PARTIAL_CARD_ID = "card-partial"
        const val DEMASTERED_CARD_ID = "card-demastered"
        const val DURATION_SECONDS = 300
        const val DAILY_GOAL_MINUTES = 20
        const val STUDY_DATE = "2026-09-06"
        const val STARTING_XP = 600L
        val STARTED_AT: Instant = Instant.parse("2026-09-06T10:00:00Z")

        // Distinct rates, so a line scored with the wrong rate cannot pass by coincidence, and a flat
        // curve (1000 per Level) so one session from STARTING_XP crosses exactly one Level.
        val CONFIG = XpConfig(
            newCardStudied = 11,
            cardMastered = 101,
            cardPartial = 23,
            masteryDefended = 47,
            cardDemastered = -79,
            sessionCompleted = 503,
            minuteStudied = 7,
            levelCurveBase = 400.0,
            levelCurveExponent = 1.0,
        )
    }
}
