package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.ScoringState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield

class FakeScoringStateRepository : ScoringStateRepository {
    private val scoringStateUpdates = MutableSharedFlow<ScoringState>(replay = 1)

    /** When set, [observeScoringState] throws this before its first emission, like a failing snapshot listener. */
    var scoringStateReadFailure: Throwable? = null

    /** When `true`, [observeScoringState] completes without emitting, like a collection started while signed out. */
    var completesWithoutEmitting: Boolean = false

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

    /** The `cacheOnly` flag of every [getScoringState] call, in call order. */
    val cacheOnlyFlags: MutableList<Boolean> = mutableListOf()

    /** How many times [getScoringState] was called. */
    var getScoringStateCallCount: Int = 0
        private set

    /**
     * [yield]s once before returning, mirroring [com.rossomak.flashcards.core.data.repository.DefaultScoringStateRepository]'s
     * genuine `withContext(Dispatchers.IO)` dispatcher hop — the same reasoning as
     * [FakeSessionSubmissionRepository]'s own [yield].
     */
    override suspend fun getScoringState(excludedSessionId: String?, cacheOnly: Boolean): Result<ScoringState?> {
        getScoringStateCallCount++
        excludedSessionIds.add(excludedSessionId)
        cacheOnlyFlags.add(cacheOnly)
        yield()
        if (excludedSessionId in appliedSessionIds) return Result.failure(IllegalStateException("Session $excludedSessionId is already applied"))
        return resultsIncludingQueuedSession.entries.firstOrNull { (sessionId, _) -> sessionId != excludedSessionId }?.value ?: resultToReturn
    }

    /**
     * Emits [scoringState] to every active [observeScoringState] collector and replays it to the next
     * one, mirroring a snapshot listener that re-fires after a change. Independent of the one-shot
     * [resultToReturn].
     */
    suspend fun emit(scoringState: ScoringState) {
        scoringStateUpdates.emit(scoringState)
    }

    override fun observeScoringState(): Flow<ScoringState> = flow {
        scoringStateReadFailure?.let { throw it }
        if (completesWithoutEmitting) return@flow
        emitAll(scoringStateUpdates)
    }
}
