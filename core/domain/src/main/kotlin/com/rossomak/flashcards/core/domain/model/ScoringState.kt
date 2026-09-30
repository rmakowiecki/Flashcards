package com.rossomak.flashcards.core.domain.model

/**
 * The User's account-wide scoring state: the per-user singleton `users/{uid}/progress/user-stats`,
 * alongside the progress summary (`progress/summary`) and the packed per-Subcategory documents under
 * `progress/` (ADR-0014, ADR-0016). The server-authoritative `submitStudySession` Cloud Function is its
 * only writer. The client reads it through
 * [com.rossomak.flashcards.core.domain.repository.ScoringStateRepository], with its Pending Sessions
 * replayed on top by [com.rossomak.flashcards.core.domain.scoring.scoreSession], for the Session
 * Summary's fallback preview.
 *
 * [currentStreak], [bestStreak], [lastStudyDate], [goalMetDate] and [studiedSecondsOnLastStudyDate]
 * advance by the Streak and Daily Goal rules
 * ([com.rossomak.flashcards.core.domain.scoring.calculateStreakAndGoalAwards]): the server applies
 * them when it records a session, and the client applies the same rules when it replays a Pending
 * Session. The two dates are calendar days in the device's local zone (`yyyy-MM-dd`), not instants; an
 * empty string means "no study day recorded yet". A replay can under-count the Daily Goal's day in a few
 * cases, listed on [com.rossomak.flashcards.core.domain.scoring.scoreSession].
 *
 * @param xp total points currently held.
 * @param level denormalized from [xp] via the configured curve ([XpConfig.levelThreshold]); never
 * decreases, even when [xp] drops.
 * @param xpIntoCurrentLevel points earned since [level] was last reached; never negative.
 * @param studiedSecondsOnLastStudyDate every recorded session's duration summed for [lastStudyDate].
 */
data class ScoringState(
    val xp: Long = 0,
    val level: Int = STARTING_LEVEL,
    val xpIntoCurrentLevel: Long = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastStudyDate: String = "",
    val goalMetDate: String = "",
    val studiedSecondsOnLastStudyDate: Long = 0,
) {
    companion object {
        /** Every account starts here: level 1, no points into it, before a session has ever committed. */
        const val STARTING_LEVEL = 1
    }
}
