package com.rossomak.flashcards.core.domain.model

/**
 * The User's [ScoringState] with what it means today: where they stand on the Level curve, their
 * Streak, and the seconds they have studied today. Pending Sessions are counted.
 *
 * @param streak `null` for a User who has never studied, Pending Sessions included.
 * @param todayStudiedSeconds every session of today summed, 0 when the last study day is not today.
 */
data class ObservedScoringState(
    val scoringState: ScoringState,
    val levelProgress: LevelProgress,
    val streak: Streak?,
    val todayStudiedSeconds: Long,
)

/**
 * The Streak as of today. The stored Streak is not reset when a day passes without a session, so
 * [days] reads 0 once the last study day is before yesterday.
 *
 * @param best the historical peak, never derived from today: it is kept when [days] reads 0.
 * @param isAtRisk the Streak was extended yesterday and today has no session yet, so it breaks at
 * midnight unless the User studies.
 */
data class Streak(
    val days: Int,
    val best: Int,
    val isAtRisk: Boolean,
)
