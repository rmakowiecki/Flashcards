package com.rossomak.flashcards.core.domain.model

/**
 * One Subcategory's progress as a screen shows it, resolved against a User's [ProgressSummary].
 *
 * Distinct from [SubcategoryProgressDetails], the Subcategory's packed per-card Card Progress document: this
 * type only ever holds the summary's two counts, and says whether they are known yet.
 */
sealed interface SubcategoryProgressState {
    /** The summary read has not resolved yet, or failed — the counts are unknown. */
    data object Unresolved : SubcategoryProgressState

    /**
     * The counts are known. A Subcategory never studied is `Resolved(0, 0)`, a normal "0% studied" —
     * not [Unresolved].
     */
    data class Resolved(val studiedCount: Int, val masteredCount: Int) : SubcategoryProgressState {

        /**
         * The share of [cardCount] the User has studied, in `0f..1f`. `0f` for a Subcategory with no
         * cards rather than dividing by zero. Clamped because Pending Session replay can briefly count
         * more Studied cards than the Subcategory holds.
         */
        fun studiedFraction(cardCount: Int): Float = if (cardCount > 0) (studiedCount / cardCount.toFloat()).coerceIn(0f, 1f) else 0f
    }
}

/**
 * Resolves one Subcategory's [SubcategoryProgressState] against a per-User summary, so every screen
 * agrees on the unresolved and absent-summary rules: [isResolved] `false` is [SubcategoryProgressState.Unresolved];
 * resolved with a Subcategory missing from the summary, or no summary document at all, is all-zero.
 */
fun ProgressSummary?.subcategoryProgressFor(subcategoryId: String, isResolved: Boolean): SubcategoryProgressState {
    if (!isResolved) return SubcategoryProgressState.Unresolved
    val summary = this?.subcategories?.get(subcategoryId)
    return SubcategoryProgressState.Resolved(
        studiedCount = summary?.studiedCount ?: 0,
        masteredCount = summary?.masteredCount ?: 0,
    )
}
