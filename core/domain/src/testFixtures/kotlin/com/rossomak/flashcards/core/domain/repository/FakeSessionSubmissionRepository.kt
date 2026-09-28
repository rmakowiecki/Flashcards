package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus
import com.rossomak.flashcards.core.domain.model.SessionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.yield

class FakeSessionSubmissionRepository : SessionSubmissionRepository {

    /** The delivery report every [submitSession] call returns; no server result by default. */
    var deliveryStatusToReturn: Flow<SessionDeliveryStatus> = flowOf(SessionDeliveryStatus.NotDelivered)

    /** Runs at the start of every [submitSession] call, before anything is recorded; lets a test observe what happened before submitting. */
    var onSubmit: () -> Unit = {}

    /** Every submitted session, in call order. */
    val submittedSessionResults: MutableList<SessionResult> = mutableListOf()

    /**
     * [yield]s once before reporting anything back, mirroring
     * [com.rossomak.flashcards.core.data.repository.DefaultSessionSubmissionRepository]'s genuine
     * dispatcher hop when it queues the session.
     */
    override suspend fun submitSession(sessionResult: SessionResult): Flow<SessionDeliveryStatus> {
        onSubmit()
        submittedSessionResults.add(sessionResult)
        yield()
        return deliveryStatusToReturn
    }
}
