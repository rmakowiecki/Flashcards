package com.rossomak.flashcards.core.domain.scoring

import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState.Failed
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState.Mastered
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState.Partial
import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SessionScore
import com.rossomak.flashcards.core.domain.model.SessionScoreCounts
import com.rossomak.flashcards.core.domain.model.SessionScoreRates
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.model.levelThreshold

/**
 * Everything one session changes for a User, as [scoreSession] derives it.
 *
 * @property cardProgressMerge the session's Card Progress changes.
 * @property newScoringState the account's scoring state after the session.
 * @property score the award, line by line, with the counts and rates behind each line.
 */
data class SessionScoring(
    val cardProgressMerge: CardProgressMergeResult,
    val newScoringState: ScoringState,
    val score: SessionScore,
)

/**
 * Scores [session] the way the `submitStudySession` Cloud Function does when it records it, so the
 * client can show and project a session the server has not scored yet. The client's only scoring
 * code: the Session Summary's fallback preview and the replay of Pending Sessions both call it.
 *
 * Steps, as on the server:
 * 1. [mergeSessionIntoCardProgress] over [priorCardStatesBySubcategory] gives the Card Progress
 *    changes, the new-card count and each Rated card's previously-Mastered flag;
 * 2. that flag replaces the one the session carries, which was read when the session started;
 * 3. [calculateSessionXp] scores the session with [config] against [scoringState].
 *
 * The counts come from the same flags the award used, so each line's `count × rate` matches its amount.
 *
 * The Daily Goal is judged on the session's own seconds plus [ScoringState.studiedSecondsOnLastStudyDate]
 * when [scoringState] was last advanced on the session's study date. The server sums every session it
 * holds for that day instead. The two agree except where [scoringState] does not include a session the
 * server has, so the preview may under-count the day:
 * - a Pending Session dated earlier than [ScoringState.lastStudyDate] counts only its own seconds;
 * - sessions delivered from another device count only once this device reads the scoring state again;
 * - a session this device delivered, whose scoring-state read after delivery failed, has left the
 *   queue but is not in the cached scoring state until the next successful read.
 *
 * @param priorCardStatesBySubcategory Subcategory id to card id to the card's state before the
 * session; a missing Subcategory or card has no record.
 * @param scoringState the account's state before the session.
 */
fun scoreSession(
    priorCardStatesBySubcategory: Map<String, Map<String, FlashcardStudyProgressState>>,
    scoringState: ScoringState,
    session: SessionResult,
    config: XpConfig,
): SessionScoring {
    val cardProgressMerge = mergeSessionIntoCardProgress(priorCardStatesBySubcategory, session)
    val authoritativeSession = session.withPreviouslyMastered(cardProgressMerge.previouslyMasteredByCardId)
    val sessionXp = calculateSessionXp(authoritativeSession, cardProgressMerge.newCardsStudied, scoringState, config, scoringState.streakAndGoalInput(session))
    val newScoringState = sessionXp.newScoringState

    val score = SessionScore(
        breakdown = sessionXp.breakdown,
        level = newScoringState.level,
        xpIntoCurrentLevel = newScoringState.xpIntoCurrentLevel,
        xpForNextLevel = config.levelThreshold(newScoringState.level),
        levelsCrossed = sessionXp.levelsCrossed,
        levelBefore = scoringState.level,
        xpIntoCurrentLevelBefore = scoringState.xpIntoCurrentLevel,
        xpForNextLevelBefore = config.levelThreshold(scoringState.level),
        currentStreak = newScoringState.currentStreak,
        counts = authoritativeSession.scoreCounts(cardProgressMerge.newCardsStudied),
        rates = config.toScoreRates(),
    )
    return SessionScoring(cardProgressMerge = cardProgressMerge, newScoringState = newScoringState, score = score)
}

private fun ScoringState.streakAndGoalInput(session: SessionResult): StreakAndGoalInput {
    val priorSecondsThatDay = if (lastStudyDate == session.studyDate) studiedSecondsOnLastStudyDate else 0
    return StreakAndGoalInput(
        studyDate = session.studyDate,
        dailyGoalMinutes = session.dailyGoalMinutes,
        todayTotalSeconds = priorSecondsThatDay + session.durationSeconds,
    )
}

private fun SessionResult.withPreviouslyMastered(previouslyMasteredByCardId: Map<String, Boolean>): SessionResult = when (this) {
    is SessionResult.Rated -> copy(
        cardResults = cardResults.map { entry -> entry.copy(wasPreviouslyMastered = previouslyMasteredByCardId[entry.cardId] ?: false) },
    )
    is SessionResult.Fast -> this
}

/** The Rated-only counts are `null` for a Fast session, which has no Terminal States. */
private fun SessionResult.scoreCounts(newCardsStudied: Int): SessionScoreCounts = when (this) {
    is SessionResult.Rated -> SessionScoreCounts(
        newCardsStudied = newCardsStudied,
        newlyMastered = cardResults.count { entry -> entry.state == Mastered && !entry.wasPreviouslyMastered },
        partial = cardResults.count { entry -> entry.state == Partial },
        defended = cardResults.count { entry -> entry.state == Mastered && entry.wasPreviouslyMastered },
        demastered = cardResults.count { entry -> entry.state == Failed && entry.wasPreviouslyMastered },
    )
    is SessionResult.Fast -> SessionScoreCounts(newCardsStudied = newCardsStudied, newlyMastered = null, partial = null, defended = null, demastered = null)
}

private fun XpConfig.toScoreRates(): SessionScoreRates = SessionScoreRates(
    newCardStudied = newCardStudied,
    cardMastered = cardMastered,
    cardPartial = cardPartial,
    masteryDefended = masteryDefended,
    cardDemastered = cardDemastered,
    minuteStudied = minuteStudied,
    sessionCompleted = sessionCompleted,
)
