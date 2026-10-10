package com.rossomak.flashcards.core.data.worker

import com.google.firebase.firestore.Source
import com.rossomak.flashcards.core.common.logw
import com.rossomak.flashcards.core.data.source.CardProgressRemoteDataSource
import com.rossomak.flashcards.core.data.source.ScoringStateRemoteDataSource
import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.SessionResult
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Refreshes the Firestore cache after [SessionSubmissionDeliveryWorker] delivers a session: reads the
 * User's scoring state and the Card Progress document of every Subcategory the session's Flashcard
 * Results touch with [Source.SERVER], so every screen reading through the cache sees the server's new
 * state. A Subcategory with no document reads as success.
 *
 * Every read is attempted even after one fails, so one failure never leaves the others stale. The
 * result is a failure if any read failed: the worker keeps the session queued until a later run
 * refreshes everything, because the projection relies on the cache including a session once it leaves
 * the queue.
 */
class SessionServerStateRefresher @Inject constructor(
    private val scoringStateRemoteDataSource: ScoringStateRemoteDataSource,
    private val cardProgressRemoteDataSource: CardProgressRemoteDataSource,
) {

    suspend fun refresh(sessionResult: SessionResult): Result<Unit> {
        val touchedSubcategoryIds = sessionResult.cardResults.map(FlashcardResult::subcategoryId).toSet()
        val failures = listOfNotNull(
            failureOfRead("scoring state") { scoringStateRemoteDataSource.getScoringState(Source.SERVER) },
        ) + touchedSubcategoryIds.mapNotNull { subcategoryId ->
            failureOfRead("Card Progress of $subcategoryId") { cardProgressRemoteDataSource.getProgress(subcategoryId, Source.SERVER) }
        }
        return failures.firstOrNull()?.let { failure -> Result.failure(failure) } ?: Result.success(Unit)
    }

    // Broad on purpose: any failed read means the cache may be stale, whatever the cause.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun failureOfRead(description: String, read: suspend () -> Any?): Exception? = try {
        read()
        null
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        logw(exception) { "Post-delivery refresh of $description failed" }
        exception
    }
}
