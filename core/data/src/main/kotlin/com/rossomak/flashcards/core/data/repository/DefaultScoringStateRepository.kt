package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.repository.ScoringStateRepository
import javax.inject.Inject

/**
 * Serves the cached server scoring state with the signed-in User's Pending Sessions replayed on top
 * ([PendingSessionProjector.projectScoringState]), so a session studied offline counts toward XP and
 * Level as soon as it is queued. With an empty queue it is the plain remote read.
 *
 * **Known transient:** the delivery worker refreshes the cached server state before it removes the
 * delivered entry from the queue. In between, that session counts twice.
 */
class DefaultScoringStateRepository @Inject constructor(
    private val pendingSessionProjector: PendingSessionProjector,
) : ScoringStateRepository {

    override suspend fun getScoringState(): Result<ScoringState?> = pendingSessionProjector.projectScoringState()
}
