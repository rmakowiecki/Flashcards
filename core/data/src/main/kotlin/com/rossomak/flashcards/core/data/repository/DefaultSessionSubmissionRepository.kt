package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.common.logd
import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.data.SessionSubmissionDrainScheduler
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionMapper.toDto
import com.rossomak.flashcards.core.data.source.PendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.worker.sessionDeliveryStatusOf
import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus
import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus.InFlight
import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus.NotDelivered
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.repository.AuthRepository
import com.rossomak.flashcards.core.domain.repository.NetworkAvailabilityGateway
import com.rossomak.flashcards.core.domain.repository.SessionSubmissionRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull

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
 * The returned flow reports this one session's delivery, read off the drain run's WorkManager state:
 * - [NotDelivered] at once when the session could not be queued (no signed-in User, a failed local
 *   write) or when [networkAvailabilityGateway] reports no internet. Offline, the drain still waits for the
 *   network and delivers the session later; only the report ends early.
 * - Otherwise [InFlight], then the first final status the run reports for this session (see
 *   [sessionDeliveryStatusOf]).
 *
 * A failure to queue is logged and reported as [NotDelivered], never thrown: the Session Summary
 * then shows its local preview, as it would offline.
 */
class DefaultSessionSubmissionRepository @Inject constructor(
    private val localDataSource: PendingSessionSubmissionLocalDataSource,
    private val drainScheduler: SessionSubmissionDrainScheduler,
    private val authRepository: AuthRepository,
    private val networkAvailabilityGateway: NetworkAvailabilityGateway,
) : SessionSubmissionRepository {

    override suspend fun submitSession(sessionResult: SessionResult): Flow<SessionDeliveryStatus> {
        val requestId = queue(sessionResult) ?: return flowOf(NotDelivered)
        if (!networkAvailabilityGateway.isInternetAvailable()) {
            logd { "Session ${sessionResult.id} queued with no internet, it is delivered once the network returns" }
            return flowOf(NotDelivered)
        }
        return flow {
            emit(InFlight)
            emit(drainScheduler.observeDrain(requestId).mapNotNull { workInfo -> sessionDeliveryStatusOf(workInfo, sessionResult.id) }.first())
        }
    }

    // Broad on purpose: a local file write can fail with more than one anticipated exception type, and
    // narrowing this would let an unanticipated one crash the Session Summary instead of being reported.

    /** Appends [sessionResult] to the queue and schedules a drain for it, returning the drain request's id, or `null` when it could not be queued. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun queue(sessionResult: SessionResult): UUID? = try {
        val uid = requireNotNull(authRepository.getCurrentUser()?.uid) { "No signed-in User to own session ${sessionResult.id}" }
        localDataSource.append(sessionResult.toDto(uid))
        drainScheduler.scheduleDrainForFinishedSession().also {
            logd { "Session ${sessionResult.id} (mode=${sessionResult.mode}) queued, drain scheduled" }
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        loge(exception) { "Failed to queue session ${sessionResult.id} for durable delivery" }
        null
    }
}
