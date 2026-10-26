package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.data.model.ScoringStateDto
import com.rossomak.flashcards.core.data.repository.DefaultScoringStateRepository.ProjectionResult.Projected
import com.rossomak.flashcards.core.data.repository.DefaultScoringStateRepository.ProjectionResult.Stale
import com.rossomak.flashcards.core.data.repository.DefaultScoringStateRepository.ProjectionResult.Stopped
import com.rossomak.flashcards.core.data.repository.DefaultScoringStateRepository.ProjectionStep.Project
import com.rossomak.flashcards.core.data.repository.DefaultScoringStateRepository.ProjectionStep.Stop
import com.rossomak.flashcards.core.data.repository.DefaultScoringStateRepository.ScoringStateEvent.PendingSessions
import com.rossomak.flashcards.core.data.repository.DefaultScoringStateRepository.ScoringStateEvent.RemoteCompleted
import com.rossomak.flashcards.core.data.repository.DefaultScoringStateRepository.ScoringStateEvent.RemoteScoringState
import com.rossomak.flashcards.core.data.source.ScoringStateRemoteDataSource
import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.repository.ScoringStateRepository
import com.rossomak.flashcards.core.domain.repository.XpConfigRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transformWhile

/**
 * Serves the User's scoring state with their Pending Sessions replayed on top ([PendingSessionProjector]),
 * so a session studied offline counts toward XP, Level, Streak and Daily Goal as soon as it is queued.
 * With an empty queue it is the plain remote state. A Pending Session the server state already lists as
 * applied adds nothing, so a delivered session counts once while it waits to leave the queue (ADR-0061).
 *
 * Each collector of [observeScoringState] registers its own snapshot listener; nothing is shared or kept
 * alive between collectors. The Firestore client serves duplicate listeners on one document from a
 * single watch, so a second collector costs only its own replay.
 */
class DefaultScoringStateRepository @Inject constructor(
    private val scoringStateRemoteDataSource: ScoringStateRemoteDataSource,
    private val pendingSessionProjector: PendingSessionProjector,
    private val xpConfigRepository: XpConfigRepository,
) : ScoringStateRepository {

    override suspend fun getScoringState(excludedSessionId: String?, cacheOnly: Boolean): Result<ScoringState?> =
        pendingSessionProjector.projectScoringState(excludedSessionId, cacheOnly)

    /**
     * Completes, with no further emission, when the remote flow completes (on sign-out), even though
     * the queue is still observed: the completion passes through the projection step and cancels a
     * projection still in flight. Each projection reads the XP configuration once for its replay.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeScoringState(): Flow<ScoringState> = flow {
        val remoteScoringState = scoringStateRemoteDataSource.observeScoringState()
            .map<_, ScoringStateEvent> { dto -> RemoteScoringState(dto) }
            .retryOnFirestorePermissionDenied()
            .onCompletion { cause -> if (cause == null) emit(RemoteCompleted) }
        val pendingSessions = pendingSessionProjector.observePendingSessions()
            .map { sessions -> PendingSessions(sessions) }

        var latestRemoteScoringState: RemoteScoringState? = null
        var latestPendingSessions: List<SessionResult>? = null
        val projectionSteps = merge(remoteScoringState, pendingSessions).transformWhile { event ->
            when (event) {
                is RemoteScoringState -> latestRemoteScoringState = event
                is PendingSessions -> latestPendingSessions = event.sessions
                RemoteCompleted -> {
                    emit(Stop)
                    return@transformWhile false
                }
            }
            val remoteState = latestRemoteScoringState
            val queuedSessions = latestPendingSessions
            if (remoteState != null && queuedSessions != null) emit(Project(remoteState.scoringState, queuedSessions))
            true
        }
        emitAll(
            projectionSteps
                .mapLatest { step ->
                    when (step) {
                        is Project -> project(step.remoteScoringState, step.pendingSessions)
                        Stop -> Stopped
                    }
                }
                .transformWhile { result ->
                    if (result is Projected) emit(result.scoringState)
                    result != Stopped
                }
                .distinctUntilChanged(),
        )
    }

    /** A stale projection (see [PendingSessionProjector.projectScoringStateOver]) is dropped; the queue change that made it stale re-emits. */
    private suspend fun project(remoteScoringState: ScoringStateDto?, pendingSessions: List<SessionResult>): ProjectionResult {
        val config = xpConfigRepository.getXpConfig().getOrDefault(XpConfig())
        val scoringState = pendingSessionProjector.projectScoringStateOver(remoteScoringState, pendingSessions, config) ?: return Stale
        return Projected(scoringState)
    }

    /** One update from either source [observeScoringState] follows, including the remote flow's normal completion. */
    private sealed interface ScoringStateEvent {
        data class RemoteScoringState(val scoringState: ScoringStateDto?) : ScoringStateEvent

        data class PendingSessions(val sessions: List<SessionResult>) : ScoringStateEvent

        data object RemoteCompleted : ScoringStateEvent
    }

    /** What to project next: the latest value of each source once both are known, or nothing more once the remote flow completed. */
    private sealed interface ProjectionStep {
        data class Project(val remoteScoringState: ScoringStateDto?, val pendingSessions: List<SessionResult>) : ProjectionStep

        data object Stop : ProjectionStep
    }

    private sealed interface ProjectionResult {
        data class Projected(val scoringState: ScoringState) : ProjectionResult

        data object Stale : ProjectionResult

        data object Stopped : ProjectionResult
    }
}
