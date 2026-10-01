package com.rossomak.flashcards.core.domain.model

import java.time.Instant

/**
 * One card's persisted progress entry inside a [SubcategoryProgressDetails]'s packed `cards` map
 * ([ADR-0016](../../../../../../../docs/adr/0016-card-progress-model.md)). [masteredAt] is the most
 * recent time the card reached [FlashcardStudyProgressState.Mastered]; it is retained after a later
 * de-mastery, never cleared, so it only ever changes by being overwritten with a later mastery.
 */
data class CardProgressEntry(
    val state: FlashcardStudyProgressState,
    val firstStudiedAt: Instant,
    val masteredAt: Instant?,
)

/**
 * One User's packed per-Subcategory progress document (ADR-0016):
 * `users/{uid}/progress/details/subcategories/{subcategoryId}`. Only cards the User has actually studied appear in
 * [cards]. [categoryId] rides along denormalized, so a document identifies its own scope without a
 * further lookup.
 *
 * Written only by the server-authoritative `submitStudySession` Cloud Function — this
 * client only ever reads it, via [com.rossomak.flashcards.core.domain.repository.CardProgressRepository.getProgress].
 */
data class SubcategoryProgressDetails(
    val subcategoryId: String,
    val categoryId: String,
    val cards: Map<String, CardProgressEntry>,
)
