package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.common.logw
import com.rossomak.flashcards.core.data.mapper.toDomainOrNull
import com.rossomak.flashcards.core.data.source.RecentsRemoteDataSource
import com.rossomak.flashcards.core.domain.model.RecentSession
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.repository.RecentSessionsRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

/**
 * Merges the server's `recents/state` with the signed-in User's Pending Sessions. A server entry wins
 * over a Pending Session with the same id, and Pending Sessions with no Flashcard Results are dropped,
 * since the server rejects them. The cap applies after sorting, so the list is as long offline as online.
 *
 * On delivery, the queue entry may go before the server entry arrives, so a row can blink. Accepted.
 */
class DefaultRecentSessionsRepository @Inject constructor(
    private val remoteDataSource: RecentsRemoteDataSource,
    private val pendingSessionProjector: PendingSessionProjector,
) : RecentSessionsRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRecentSessions(): Flow<List<RecentSession>> = combine(
        remoteDataSource.observeRecents()
            .map { dto ->
                dto.entries.mapNotNull { entry ->
                    entry.toDomainOrNull().also { recent ->
                        if (recent == null) logw { "Skipping unmappable recents entry ${entry.sessionId}" }
                    }
                }
            }
            .retryOnFirestorePermissionDenied(),
        pendingSessionProjector.observeDeliverablePendingSessions(),
    ) { remoteSessions, pendingSessions -> remoteSessions to pendingSessions }
        .transformLatest { (remoteSessions, pendingSessions) ->
            val xpTotals = pendingSessionProjector.projectSessionXpTotals(pendingSessions) ?: return@transformLatest
            emit(merge(remoteSessions, pendingSessions, xpTotals))
        }
        .distinctUntilChanged()

    private fun merge(remoteSessions: List<RecentSession>, pendingSessions: List<SessionResult>, xpTotals: Map<String, Int>): List<RecentSession> {
        val remoteIds = remoteSessions.map(RecentSession::id).toSet()
        val pendingRecents = pendingSessions
            .filterNot { session -> session.id in remoteIds }
            .map { session -> session.toRecentSession(xpTotals.getValue(session.id)) }
        return (remoteSessions + pendingRecents)
            .sortedWith(compareByDescending(RecentSession::startedAt).thenByDescending(RecentSession::id))
            .take(MAX_RECENT_SESSIONS)
    }

    private fun SessionResult.toRecentSession(xpTotal: Int): RecentSession = when (this) {
        is SessionResult.Rated -> RecentSession.Rated(
            id = id,
            startedAt = startedAt,
            durationSeconds = durationSeconds,
            sourceType = sourceType,
            categoryId = categoryId,
            categoryName = categoryName,
            subcategoryIds = subcategoryIds,
            subcategoryNames = subcategoryNames,
            studiedCount = cardResults.size,
            xpTotal = xpTotal,
            voiceAnsweringEnabled = voiceAnsweringEnabled,
        )
        is SessionResult.Fast -> RecentSession.Fast(
            id = id,
            startedAt = startedAt,
            durationSeconds = durationSeconds,
            sourceType = sourceType,
            categoryId = categoryId,
            categoryName = categoryName,
            subcategoryIds = subcategoryIds,
            subcategoryNames = subcategoryNames,
            studiedCount = cardResults.size,
            xpTotal = xpTotal,
            readAloudEnabled = readAloudEnabled,
        )
    }

    private companion object {
        /** The server keeps the same number in `recents/state`. */
        const val MAX_RECENT_SESSIONS = 15
    }
}
