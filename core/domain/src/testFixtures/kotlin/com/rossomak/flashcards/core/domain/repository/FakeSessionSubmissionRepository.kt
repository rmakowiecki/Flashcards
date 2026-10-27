package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.SessionResult
import kotlin.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

class FakeSessionSubmissionRepository : SessionSubmissionRepository {

    /** What every [submitSession] call returns; queued by default. */
    var submissionResultToReturn: Result<Unit> = Result.success(Unit)

    /** How long every [submitSession] call takes before returning; lets a test observe the Summary while it waits. */
    var submitDelay: Duration = Duration.ZERO

    /** Runs at the start of every [submitSession] call, before anything is recorded; lets a test observe what happened before submitting. */
    var onSubmit: () -> Unit = {}

    /** Every submitted session, in call order. */
    val submittedSessionResults: MutableList<SessionResult> = mutableListOf()

    /** [yield]s once, like the real repository's dispatcher hop, then waits [submitDelay]. */
    override suspend fun submitSession(sessionResult: SessionResult): Result<Unit> {
        onSubmit()
        submittedSessionResults.add(sessionResult)
        yield()
        delay(submitDelay)
        return submissionResultToReturn
    }
}
