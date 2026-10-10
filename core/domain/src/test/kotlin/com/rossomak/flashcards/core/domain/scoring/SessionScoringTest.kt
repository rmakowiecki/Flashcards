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
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
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
        scoring.score.counts.defended shouldBe 1
        scoring.score.counts.newlyMastered shouldBe 0
    }

    @Test
    fun `a card with no record scores as freshly Mastered even when the session's own flag says it was Mastered`() {
        val session = ratedSession(RatedCard(CARD_ID, Mastered, wasPreviouslyMastered = true))

        val scoring = scoreSession(emptyMap(), ScoringState(), session, CONFIG)

        scoring.score.breakdown.mastered shouldBe CONFIG.cardMastered
        scoring.score.breakdown.masteryDefenseBonus shouldBe 0
        scoring.score.counts.newlyMastered shouldBe 1
        scoring.score.counts.defended shouldBe 0
    }

    @Test
    fun `a card Failed after being Mastered before the session is de-mastered even when the session's own flag says it was not`() {
        val session = ratedSession(RatedCard(CARD_ID, Failed, wasPreviouslyMastered = false))

        val scoring = scoreSession(priorStates(CARD_ID to Mastered), ScoringState(), session, CONFIG)

        scoring.score.breakdown.demastered shouldBe CONFIG.cardDemastered
        scoring.score.counts.demastered shouldBe 1
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
        val counts = score.counts
        val rates = score.rates

        counts shouldBe SessionScoreCounts(newCardsStudied = 2, newlyMastered = 1, partial = 1, defended = 1, demastered = 1)
        score.breakdown.newCards shouldBe counts.newCardsStudied * rates.newCardStudied
        score.breakdown.mastered shouldBe requireNotNull(counts.newlyMastered) * rates.cardMastered
        score.breakdown.partial shouldBe requireNotNull(counts.partial) * rates.cardPartial
        score.breakdown.masteryDefenseBonus shouldBe requireNotNull(counts.defended) * rates.masteryDefended
        score.breakdown.demastered shouldBe requireNotNull(counts.demastered) * rates.cardDemastered
    }

    @Test
    fun `the rates come from the config`() {
        val rates = scoreSession(emptyMap(), ScoringState(), ratedSession(RatedCard(CARD_ID, Mastered)), CONFIG).score.rates

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
    fun `the Level position before the session comes from the given scoring state`() {
        val priorState = ScoringState(xp = STARTING_XP, level = 2, xpIntoCurrentLevel = PRIOR_XP_INTO_LEVEL)

        val score = scoreSession(emptyMap(), priorState, ratedSession(RatedCard(CARD_ID, Mastered)), CONFIG).score

        score.levelBefore shouldBe 2
        score.xpIntoCurrentLevelBefore shouldBe PRIOR_XP_INTO_LEVEL
        score.xpForNextLevelBefore shouldBe CONFIG.levelThreshold(2)
    }

    @Test
    fun `a new account's first session starts from the starting Level with no points and that Level's threshold`() {
        val score = scoreSession(emptyMap(), ScoringState(), ratedSession(RatedCard(CARD_ID, Mastered)), CONFIG).score

        score.levelBefore shouldBe ScoringState.STARTING_LEVEL
        score.xpIntoCurrentLevelBefore shouldBe 0L
        score.xpForNextLevelBefore shouldBe CONFIG.levelThreshold(ScoringState.STARTING_LEVEL)
    }

    @Test
    fun `the current Streak comes from the new scoring state`() {
        val priorState = ScoringState(currentStreak = 7, bestStreak = 7, lastStudyDate = PREVIOUS_STUDY_DATE)

        val scoring = scoreSession(emptyMap(), priorState, ratedSession(RatedCard(CARD_ID, Mastered)), CONFIG)

        scoring.score.currentStreak shouldBe 8
        scoring.score.currentStreak shouldBe scoring.newScoringState.currentStreak
    }

    @Test
    fun `a session scored on the previous session's new state starts where the previous session ended`() {
        val firstBaseline = ScoringState(xp = STARTING_XP, level = 1, xpIntoCurrentLevel = STARTING_XP)
        val first = scoreSession(emptyMap(), firstBaseline, ratedSession(RatedCard(CARD_ID, Mastered)), CONFIG)

        val second = scoreSession(emptyMap(), first.newScoringState, ratedSession(RatedCard(OTHER_CARD_ID, Mastered)), CONFIG).score

        second.levelBefore shouldBe first.score.level
        second.xpIntoCurrentLevelBefore shouldBe first.score.xpIntoCurrentLevel
        second.xpForNextLevelBefore shouldBe first.score.xpForNextLevel
    }

    @Test
    fun `the Card Progress merge result is returned as the merge computes it`() {
        val session = ratedSession(RatedCard(CARD_ID, Failed))
        val prior = priorStates(CARD_ID to Mastered)

        scoreSession(prior, ScoringState(), session, CONFIG).cardProgressMerge shouldBe mergeSessionIntoCardProgress(prior, session)
    }

    @Test
    fun `a session the day after the last study date pays the Streak award`() {
        val priorState = ScoringState(currentStreak = 2, bestStreak = 2, lastStudyDate = PREVIOUS_STUDY_DATE)

        val scoring = scoreSession(emptyMap(), priorState, ratedSession(RatedCard(CARD_ID, Mastered)), CONFIG)

        scoring.score.breakdown.streakBonus shouldBe 3 * CONFIG.streakPerDay
        scoring.newScoringState.currentStreak shouldBe 3
        scoring.newScoringState.lastStudyDate shouldBe STUDY_DATE
    }

    @Test
    fun `a session reaching the Daily Goal pays the Daily Goal award`() {
        val session = ratedSession(RatedCard(CARD_ID, Mastered)).copy(dailyGoalMinutes = DURATION_MINUTES)

        val scoring = scoreSession(emptyMap(), ScoringState(), session, CONFIG)

        scoring.score.breakdown.dailyGoalBonus shouldBe CONFIG.dailyGoalMet
        scoring.newScoringState.goalMetDate shouldBe STUDY_DATE
    }

    @Test
    fun `a second session the same day does not pay the Daily Goal twice`() {
        val priorState = ScoringState(lastStudyDate = STUDY_DATE, goalMetDate = STUDY_DATE, studiedSecondsOnLastStudyDate = DURATION_SECONDS.toLong())
        val session = ratedSession(RatedCard(CARD_ID, Mastered)).copy(dailyGoalMinutes = DURATION_MINUTES)

        scoreSession(emptyMap(), priorState, session, CONFIG).score.breakdown.dailyGoalBonus shouldBe 0
    }

    @Test
    fun `earlier seconds on the same day count toward the Daily Goal`() {
        val priorState = ScoringState(lastStudyDate = STUDY_DATE, studiedSecondsOnLastStudyDate = EARLIER_SECONDS)

        val scoring = scoreSession(emptyMap(), priorState, ratedSession(RatedCard(CARD_ID, Mastered)), CONFIG)

        scoring.score.breakdown.dailyGoalBonus shouldBe CONFIG.dailyGoalMet
        scoring.newScoringState.studiedSecondsOnLastStudyDate shouldBe EARLIER_SECONDS + DURATION_SECONDS
    }

    @Test
    fun `seconds studied on another day do not count toward the Daily Goal`() {
        val priorState = ScoringState(lastStudyDate = PREVIOUS_STUDY_DATE, studiedSecondsOnLastStudyDate = EARLIER_SECONDS)

        val scoring = scoreSession(emptyMap(), priorState, ratedSession(RatedCard(CARD_ID, Mastered)), CONFIG)

        scoring.score.breakdown.dailyGoalBonus shouldBe 0
        scoring.newScoringState.studiedSecondsOnLastStudyDate shouldBe DURATION_SECONDS.toLong()
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
        sourceType = SingleSubcategory,
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
        voiceAnsweringEnabled = false,
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
        sourceType = SingleSubcategory,
        cardResults = cardIds.map { cardId -> FlashcardResult.Fast(cardId = cardId, subcategoryId = SUBCATEGORY_ID, state = Seen) },
        studyDate = STUDY_DATE,
        studyDateUtcOffsetMinutes = 0,
        dailyGoalMinutes = DAILY_GOAL_MINUTES,
        readAloudEnabled = false,
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
        const val DURATION_MINUTES = 5
        const val DAILY_GOAL_MINUTES = 20

        // With the session's 5 minutes, exactly the 20-minute Daily Goal.
        const val EARLIER_SECONDS = 900L
        const val STUDY_DATE = "2026-09-06"
        const val PREVIOUS_STUDY_DATE = "2026-09-05"
        const val STARTING_XP = 600L
        const val PRIOR_XP_INTO_LEVEL = 250L
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
            dailyGoalMet = 31,
            streakPerDay = 29,
            levelCurveBase = 400.0,
            levelCurveExponent = 1.0,
        )
    }
}
