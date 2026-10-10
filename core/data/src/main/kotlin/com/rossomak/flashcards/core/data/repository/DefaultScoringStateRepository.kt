package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.repository.ScoringStateRepository
import javax.inject.Inject

/**
 * Serves the cached server scoring state with the signed-in User's Pending Sessions replayed on top
 * ([PendingSessionProjector.projectScoringState]), so a session studied offline counts toward XP and
 * Level as soon as it is queued. With an empty queue it is the plain remote read. A queued session the
 * cached state already lists as applied counts once, through the cache.
 */
class DefaultScoringStateRepository @Inject constructor(
    private val pendingSessionProjector: PendingSessionProjector,
) : ScoringStateRepository {

    override suspend fun getScoringState(excludedSessionId: String?): Result<ScoringState?> = pendingSessionProjector.projectScoringState(excludedSessionId)
}
