package com.rossomak.flashcards.core.domain.model

/**
 * One Subcategory's rollup inside a User's [ProgressSummary]
 * ([ADR-0016](../../../../../../../docs/adr/0016-card-progress-model.md)): how many of its cards the
 * User has ever studied, and how many they currently have Mastered. Neither is the ring
 * denominator — that is `Subcategory.cardCount`, read from the taxonomy instead of stored here.
 */
data class SubcategoryProgressSummary(
    val masteredCount: Int,
    val studiedCount: Int,
)

/**
 * The User's per-Subcategory progress-summary (ADR-0016):
 * `users/{uid}/progress/summary`. One document answers every ring on Category Details, Browse Screen and the
 * Home screen's progress displays, whatever the Category — the alternative, reading each
 * Subcategory's packed [SubcategoryProgress], would cost one read per subcategory instead of one per screen.
 *
 * A Subcategory absent from [subcategories] has never been studied; a `null` [ProgressSummary] itself
 * means the User has never finished a session at all. Both render as an empty ring — the display's concern, not this type's.
 *
 * Written only by the server-authoritative `submitStudySession` Cloud Function — this
 * client only ever reads it, via [com.rossomak.flashcards.core.domain.repository.CardProgressRepository.getProgressSummary].
 */
data class ProgressSummary(
    val subcategories: Map<String, SubcategoryProgressSummary>,
)
