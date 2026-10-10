package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.ScoringState
import kotlinx.coroutines.yield

class FakeScoringStateRepository : ScoringStateRepository {

    /** `null` (the default) simulates a brand-new account with no `progress/user-stats` document yet. */
    var resultToReturn: Result<ScoringState?> = Result.success(null)

    /**
     * Simulates queued sessions: while a session id here is not excluded, a read returns its result
     * (the state including that session) instead of [resultToReturn].
     */
    var resultsIncludingQueuedSession: Map<String, Result<ScoringState?>> = emptyMap()

    /** Simulates sessions the cached server state already includes: excluding one of them fails the read. */
    var appliedSessionIds: Set<String> = emptySet()

    /** The excluded session id of every [getScoringState] call, in call order. */
    val excludedSessionIds: MutableList<String?> = mutableListOf()

    /** How many times [getScoringState] was called. */
    var getScoringStateCallCount: Int = 0
        private set

    /**
     * [yield]s once before returning, mirroring [com.rossomak.flashcards.core.data.repository.DefaultScoringStateRepository]'s
     * genuine `withContext(Dispatchers.IO)` dispatcher hop — the same reasoning as
     * [FakeSessionSubmissionRepository]'s own [yield].
     */
    override suspend fun getScoringState(excludedSessionId: String?): Result<ScoringState?> {
        getScoringStateCallCount++
        excludedSessionIds.add(excludedSessionId)
        yield()
        if (excludedSessionId in appliedSessionIds) return Result.failure(IllegalStateException("Session $excludedSessionId is already applied"))
        return resultsIncludingQueuedSession.entries.firstOrNull { (sessionId, _) -> sessionId != excludedSessionId }?.value ?: resultToReturn
    }
}
