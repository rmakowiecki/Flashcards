package com.rossomak.flashcards.core.data.worker

import com.google.firebase.firestore.Source
import com.rossomak.flashcards.core.common.logw
import com.rossomak.flashcards.core.data.source.CardProgressRemoteDataSource
import com.rossomak.flashcards.core.data.source.ScoringStateRemoteDataSource
import com.rossomak.flashcards.core.domain.model.SessionResult
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Best-effort refresh of the Firestore cache after [SessionSubmissionDeliveryWorker] delivers a
 * session: reads the User's scoring state and the Card Progress document of each of the session's
 * Subcategories with [Source.SERVER], so every screen reading through the cache sees the server's new
 * state as soon as possible. Every failure is logged and ignored, one read at a time, so one failed
 * read never skips the others.
 */
class SessionServerStateRefresher @Inject constructor(
    private val scoringStateRemoteDataSource: ScoringStateRemoteDataSource,
    private val cardProgressRemoteDataSource: CardProgressRemoteDataSource,
) {

    suspend fun refresh(sessionResult: SessionResult) {
        ignoringFailure("scoring state") { scoringStateRemoteDataSource.getScoringState(Source.SERVER) }
        sessionResult.subcategoryIds.forEach { subcategoryId ->
            ignoringFailure("Card Progress of $subcategoryId") { cardProgressRemoteDataSource.getProgress(subcategoryId, Source.SERVER) }
        }
    }

    // Broad on purpose: the refresh is best-effort, so no failure of it may stop the drain.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun ignoringFailure(description: String, read: suspend () -> Any?) {
        try {
            read()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logw(exception) { "Post-delivery refresh of $description failed, ignoring it" }
        }
    }
}
