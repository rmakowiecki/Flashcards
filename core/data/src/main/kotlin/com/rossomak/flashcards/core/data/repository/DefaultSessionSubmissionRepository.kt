package com.rossomak.flashcards.core.data.repository

import android.util.Log
import com.rossomak.flashcards.core.data.SessionSubmissionDrainScheduler
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionMapper.toDto
import com.rossomak.flashcards.core.data.source.PendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.repository.SessionSubmissionRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Durable decorator over [SessionSubmissionRepository] — the class
 * [com.rossomak.flashcards.core.data.di.RepositoryModule.bindSessionSubmissionRepository] now binds
 * to that interface, a role the network-only predecessor of
 * [com.rossomak.flashcards.core.data.network.SessionSubmissionApi] held alone before this queue
 * existed.
 * [com.rossomak.flashcards.core.domain.usecase.SubmitStudySessionUseCase]'s call site is unaware of
 * any of this: it still just calls `submitSession`. This is the queue's *write* side —
 * [submitSession] appends the session to [localDataSource]'s local durable store and asks
 * [drainScheduler] to schedule delivery, then returns success once the session is durably *queued*,
 * not once it has actually been *delivered*. Delivery itself is
 * [com.rossomak.flashcards.core.data.worker.SessionSubmissionDeliveryWorker]'s job, reading straight
 * from [localDataSource] on its own schedule — see that class's doc for the drain loop, its FIFO
 * ordering, and its retry policy; see
 * [com.rossomak.flashcards.core.data.source.FilePendingSessionSubmissionLocalDataSource] for the
 * local store's shape and concurrency guarantee.
 *
 * A local-write failure (e.g. disk full) is caught here rather than propagated — matching this app's
 * existing fire-and-forget submission UX, where [SubmitStudySessionUseCase]'s caller never surfaces
 * `submitSession`'s result to the UI either way — but is logged non-fatally rather than dropped with
 * zero trace.
 */
class DefaultSessionSubmissionRepository @Inject constructor(
    private val localDataSource: PendingSessionSubmissionLocalDataSource,
    private val drainScheduler: SessionSubmissionDrainScheduler,
) : SessionSubmissionRepository {

    // Broad on purpose, matching RealSessionSubmissionApi's own suppression: a local file
    // write can fail with more than one anticipated exception type, and this call site has no
    // surrounding try/catch of its own — narrowing this would let an unanticipated exception type
    // crash instead of surfacing as a logged, non-fatal Result.failure.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun submitSession(sessionResult: SessionResult): Result<Unit> = try {
        Log.d(TAG, "Queuing session ${sessionResult.id} (mode=${sessionResult.mode}) for durable delivery")
        localDataSource.append(sessionResult.toDto())
        drainScheduler.scheduleDrain()
        Log.d(TAG, "Session ${sessionResult.id} queued, drain scheduled")
        Result.success(Unit)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Log.e(TAG, "Failed to queue session ${sessionResult.id} for durable delivery", exception)
        Result.failure(exception)
    }

    private companion object {
        const val TAG = "SessionSubmissionQueue"
    }
}
