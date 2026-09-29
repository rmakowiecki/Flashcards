package com.rossomak.flashcards.core.domain.scoring

import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.XpConfig
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * What the Streak and Daily Goal awards are judged on, mirroring the `submitStudySession` Cloud
 * Function's `StreakAndGoalInput` (`functions/src/lib/xpScoring.ts`).
 *
 * @param studyDate the session's local calendar day, `yyyy-MM-dd`.
 * @param dailyGoalMinutes the Daily Goal in effect when the session ended.
 * @param todayTotalSeconds every session's duration summed for [studyDate], this session's included.
 */
data class StreakAndGoalInput(
    val studyDate: String,
    val dailyGoalMinutes: Int,
    val todayTotalSeconds: Long,
)

/** The two awards, and the Streak fields of [ScoringState] after them. */
data class StreakAndGoalAwards(
    val streakBonus: Int,
    val dailyGoalBonus: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val lastStudyDate: String,
    val goalMetDate: String,
    val studiedSecondsOnLastStudyDate: Long,
)

/**
 * The Streak and Daily Goal awards, mirroring the Cloud Function's `computeStreakAndGoalAwards`. Both
 * run the shared cases in `testdata/xp-scoring/`.
 *
 * Both awards are forward-only. A [StreakAndGoalInput.studyDate] not later than
 * [ScoringState.lastStudyDate] never advances the Streak, and one earlier than
 * [ScoringState.goalMetDate] never pays the Daily Goal. The goal pays at most once per day, when the
 * day's whole minutes reach it. [ScoringState.studiedSecondsOnLastStudyDate] becomes
 * [StreakAndGoalInput.todayTotalSeconds] for a study date on or after [ScoringState.lastStudyDate],
 * and stays unchanged for an earlier one.
 */
fun calculateStreakAndGoalAwards(state: ScoringState, input: StreakAndGoalInput, config: XpConfig): StreakAndGoalAwards {
    val advancesStreak = input.studyDate > state.lastStudyDate
    val currentStreak = when {
        !advancesStreak -> state.currentStreak
        state.lastStudyDate.isNotEmpty() && daysBetween(state.lastStudyDate, input.studyDate) == 1L -> state.currentStreak + 1
        else -> 1
    }

    val meetsGoal = input.studyDate != state.goalMetDate &&
        input.studyDate >= state.goalMetDate &&
        input.todayTotalSeconds / SECONDS_PER_MINUTE >= input.dailyGoalMinutes

    return StreakAndGoalAwards(
        streakBonus = if (advancesStreak) minOf(currentStreak * config.streakPerDay, config.streakMaxPerDay) else 0,
        dailyGoalBonus = if (meetsGoal) config.dailyGoalMet else 0,
        currentStreak = currentStreak,
        bestStreak = if (advancesStreak) maxOf(state.bestStreak, currentStreak) else state.bestStreak,
        lastStudyDate = if (advancesStreak) input.studyDate else state.lastStudyDate,
        goalMetDate = if (meetsGoal) input.studyDate else state.goalMetDate,
        studiedSecondsOnLastStudyDate = if (input.studyDate >= state.lastStudyDate) input.todayTotalSeconds else state.studiedSecondsOnLastStudyDate,
    )
}

private const val SECONDS_PER_MINUTE = 60

private fun daysBetween(earlier: String, later: String): Long = ChronoUnit.DAYS.between(LocalDate.parse(earlier), LocalDate.parse(later))
