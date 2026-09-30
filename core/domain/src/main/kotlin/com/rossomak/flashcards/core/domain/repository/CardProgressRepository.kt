package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.SubcategoryProgress
import kotlinx.coroutines.flow.Flow

/**
 * Reads a User's packed per-Subcategory progress document and their per-user progress-summary
 * singleton (both [ADR-0016](docs/adr/0016-card-progress-model.md)). Both reads include the User's
 * sessions that are finished but not yet delivered to the server, so a session studied offline counts
 * at once; how that happens is the implementation's concern, not the caller's.
 *
 * Readers:
 * - [getProgress]: session start, through
 *   [com.rossomak.flashcards.core.domain.usecase.GetSessionStartDataUseCase], which derives each
 *   Rated card's previously-Mastered flag from it
 *   ([com.rossomak.flashcards.core.domain.session.RatedStudySessionCoordinator]); and
 *   [com.rossomak.flashcards.core.domain.usecase.SubmitStudySessionUseCase], whose local preview scores
 *   the session against it.
 * - [observeProgressSummary]: the Studied/Mastered rings on Browse and Category Details.
 *
 * Any future selection that depends on which cards are Mastered (such as Mastery Defense's floor and
 * shield) must read Card Progress through [getProgress] too, so it sees undelivered sessions like
 * session start does.
 *
 * Writing is not exposed here, nor anywhere else on the client: the server-authoritative
 * `submitStudySession` Cloud Function is the sole writer of both documents, inside its own
 * Firestore transaction alongside the session document itself.
 */
interface CardProgressRepository {
    /** `null` when the User has never studied a card in this Subcategory yet. */
    suspend fun getProgress(subcategoryId: String): Result<SubcategoryProgress?>

    /**
     * A live Firestore listener on the summary singleton, not a one-shot read — re-emits on every
     * remote change and re-attaches on reconnect after a network drop, so a caller never has to
     * retry it manually. `null` when the User has never finished a session at all — every ring
     * renders empty in that case. Never surfaces a failure: transient errors retry internally, and
     * the flow completes silently on sign-out.
     */
    fun observeProgressSummary(): Flow<ProgressSummary?>
}
