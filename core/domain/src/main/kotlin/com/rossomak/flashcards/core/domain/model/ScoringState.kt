package com.rossomak.flashcards.core.domain.model

/**
 * The User's account-wide scoring state: a client-owned per-user singleton,
 * `users/{uid}/progress/user-stats`, alongside the progress summary (`progress/summary`) and the
 * packed per-Subcategory documents already living under `progress/` (ADR-0014, ADR-0016). Read once,
 * on arrival at the Session Summary, by [com.rossomak.flashcards.core.domain.repository.ScoringStateRepository] —
 * never mid-session, same reasoning as [SessionResult.xpConfig]'s own snapshot rule — and updated by
 * [com.rossomak.flashcards.core.domain.scoring.calculateSessionXp] into the state this same
 * commit writes back, in the same batch as everything else.
 *
 * [currentStreak], [bestStreak], [lastStudyDate] and [goalMetDate] exist already but stay at
 * their defaults until a later change makes them move — declaring them now means this document's
 * shape never has to change later. The two dates are calendar days in the device's local zone
 * (`yyyy-MM-dd`), not instants; an empty string means "no study day recorded yet".
 *
 * @param xp total points currently held.
 * @param level denormalized from [xp] via the configured curve ([XpConfig.levelThreshold]); never
 * decreases, even when [xp] drops.
 * @param xpIntoCurrentLevel points earned since [level] was last reached; never negative.
 */
data class ScoringState(
    val xp: Long = 0,
    val level: Int = STARTING_LEVEL,
    val xpIntoCurrentLevel: Long = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastStudyDate: String = "",
    val goalMetDate: String = "",
) {
    companion object {
        /** Every account starts here: level 1, no points into it, before a session has ever committed. */
        const val STARTING_LEVEL = 1
    }
}
