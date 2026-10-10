package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.RecentSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.yield

class FakeRecentSessionsRepository : RecentSessionsRepository {
    private val recentSessions = MutableStateFlow<List<RecentSession>>(emptyList())

    /**
     * When set, [observeRecentSessions] suspends on this before its first emission, so a test can park
     * the Recents read to assert an in-between state, mirroring [FakeUserFavoritesRepository.favoritesReadGate].
     * `null` (the default) keeps a single [yield].
     */
    var recentSessionsReadGate: CompletableDeferred<Unit>? = null

    /** When set, [observeRecentSessions] throws this before its first emission, like a failing snapshot listener. */
    var recentSessionsReadFailure: Throwable? = null

    /**
     * When set, [observeRecentSessions] completes after this many emissions, like a listener torn down on permission
     * denied; `0` completes before the first. `null` (the default) never completes.
     */
    var recentSessionsEmissionLimit: Int? = null

    fun setRecentSessions(sessions: List<RecentSession>) {
        recentSessions.value = sessions
    }

    override fun observeRecentSessions(): Flow<List<RecentSession>> = flow {
        recentSessionsReadGate?.await() ?: yield()
        recentSessionsReadFailure?.let { throw it }
        when (val limit = recentSessionsEmissionLimit) {
            null -> emitAll(recentSessions)
            0 -> Unit
            else -> emitAll(recentSessions.take(limit))
        }
    }
}
