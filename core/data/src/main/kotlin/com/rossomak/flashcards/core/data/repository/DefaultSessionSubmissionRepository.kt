package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.common.logd
import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.data.SessionSubmissionDrainScheduler
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionMapper.toDto
import com.rossomak.flashcards.core.data.source.PendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.repository.AuthRepository
import com.rossomak.flashcards.core.domain.repository.SessionSubmissionRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * The durable delivery queue's write side, bound to [SessionSubmissionRepository]. [submitSession]
 * appends the session to [localDataSource]'s local durable store, stamped with the uid of the User
 * signed in right now — a Study Session cannot finish without one — so the queue delivers it only to
 * that User's account, even after a sign-out and a different User signing in on the same device. It then
 * starts a delivery run through [drainScheduler], replacing any drain already pending. Delivery itself
 * is [com.rossomak.flashcards.core.data.worker.SessionSubmissionDeliveryWorker]'s job — see that class's
 * doc for the drain loop, its FIFO ordering, its per-User filtering and its failure handling; see
 * [com.rossomak.flashcards.core.data.source.FilePendingSessionSubmissionLocalDataSource] for the local
 * store's shape and concurrency guarantee.
 *
 * A failure to queue is logged and returned as a failure, never thrown: the Session Summary then tells
 * the User the session was not saved. Nothing waits for the delivery itself: the Summary shows its own
 * local score (ADR-0055).
 */
class DefaultSessionSubmissionRepository @Inject constructor(
    private val localDataSource: PendingSessionSubmissionLocalDataSource,
    private val drainScheduler: SessionSubmissionDrainScheduler,
    private val authRepository: AuthRepository,
) : SessionSubmissionRepository {

    // Broad on purpose: a local file write can fail with more than one anticipated exception type, and
    // narrowing this would let an unanticipated one crash the Session Summary instead of being reported.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun submitSession(sessionResult: SessionResult): Result<Unit> = try {
        val uid = requireNotNull(authRepository.getCurrentUser()?.uid) { "No signed-in User to own session ${sessionResult.id}" }
        localDataSource.append(sessionResult.toDto(uid))
        drainScheduler.scheduleDrainForFinishedSession()
        logd { "Session ${sessionResult.id} (mode=${sessionResult.mode}) queued, drain scheduled" }
        Result.success(Unit)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        loge(exception) { "Failed to queue session ${sessionResult.id} for durable delivery" }
        Result.failure(exception)
    }
}
