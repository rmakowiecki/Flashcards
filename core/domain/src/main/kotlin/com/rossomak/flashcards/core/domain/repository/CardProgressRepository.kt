package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.SubcategoryProgress

/**
 * Reads a User's packed per-Subcategory progress document and their per-user progress-summary
 * singleton (both [ADR-0016](docs/adr/0016-card-progress-model.md)).
 * [com.rossomak.flashcards.core.domain.usecase.SubmitStudySessionUseCase] is one caller of
 * [getProgress] — this read is what feeds its optimistic new-cards-studied estimate — but it also
 * serves Subcategory Details and the Preview screen's defense selection; [getProgressSummary] serves
 * Category Details', Browse's search results and the Home screen's progress rings. Both live in `core:domain` rather
 * than inside a single feature module because more than one feature reads each.
 *
 * Writing is not exposed here, nor anywhere else on the client: the server-authoritative
 * `submitStudySession` Cloud Function is the sole writer of both documents, inside its own
 * Firestore transaction alongside the session document itself.
 */
interface CardProgressRepository {
    /** `null` when the User has never studied a card in this Subcategory yet. */
    suspend fun getProgress(subcategoryId: String): Result<SubcategoryProgress?>

    /** `null` when the User has never finished a session at all — every ring renders empty in that case. */
    suspend fun getProgressSummary(): Result<ProgressSummary?>
}
