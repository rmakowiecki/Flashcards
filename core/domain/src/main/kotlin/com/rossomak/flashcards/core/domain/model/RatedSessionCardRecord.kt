package com.rossomak.flashcards.core.domain.model

/**
 * One card's history within a [RatedSessionState]'s queue: the card itself, every Rating recorded
 * across its Attempts so far in order, and whether it entered the session already Mastered — stamped
 * at seed time from the session-start progress read.
 * Mastery Defense is what finally *uses* the flag; this is only where it
 * starts being set.
 *
 * [attemptsUsed] and [bestRating] are both *derived* from [ratings] rather than stored as separate
 * fields — one source of truth, no risk of a scalar drifting out of sync with the log.
 */
data class RatedSessionCardRecord(
    val card: Flashcard,
    val ratings: List<FlashcardAttemptRating> = emptyList(),
    val wasPreviouslyMastered: Boolean = false,
) {
    val attemptsUsed: Int get() = ratings.size

    /**
     * The best Rating this card has achieved, compared by [masteryRank] rather than enum ordinal —
     * see [masteryRank] for why ([ADR-0044](../../../../../../../docs/adr/0044-three-valued-terminal-state.md)).
     * `null` only before the first Attempt.
     */
    val bestRating: FlashcardAttemptRating? get() = ratings.maxByOrNull { it.masteryRank() }
}

/**
 * Explicit best-rating-wins ranking, deliberately independent of [FlashcardAttemptRating]'s declaration
 * order: that order happens to run Failed → PartiallyCorrect → Correct today, which would make an
 * ordinal comparison work by coincidence, but reordering the enum must not silently invert
 * best-rating-wins (ADR-0044).
 */
private fun FlashcardAttemptRating.masteryRank(): Int = when (this) {
    FlashcardAttemptRating.Failed -> 0
    FlashcardAttemptRating.PartiallyCorrect -> 1
    FlashcardAttemptRating.Correct -> 2
}
