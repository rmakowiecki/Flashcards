package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.ScoringState
import kotlinx.coroutines.yield

class FakeScoringStateRepository : ScoringStateRepository {

    /** `null` (the default) simulates a brand-new account with no `progress/user-stats` document yet. */
    var resultToReturn: Result<ScoringState?> = Result.success(null)

    /** How many times [getScoringState] was called. */
    var getScoringStateCallCount: Int = 0
        private set

    /**
     * [yield]s once before returning, mirroring [com.rossomak.flashcards.core.data.repository.DefaultScoringStateRepository]'s
     * genuine `withContext(Dispatchers.IO)` dispatcher hop — the same reasoning as
     * [FakeSessionSubmissionRepository]'s own [yield].
     */
    override suspend fun getScoringState(): Result<ScoringState?> {
        getScoringStateCallCount++
        yield()
        return resultToReturn
    }
}
