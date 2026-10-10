package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.data.mapper.toDomain
import com.rossomak.flashcards.core.data.source.ScoringStateRemoteDataSource
import com.rossomak.flashcards.core.domain.model.LevelProgress
import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.model.toLevelProgress
import com.rossomak.flashcards.core.domain.repository.LevelProgressRepository
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
 * Follows the User's scoring state live and replays their deliverable Pending Sessions on top
 * ([PendingSessionProjector]), so a session studied offline counts toward XP and Level as soon as it
 * is queued. Once a Pending Session is delivered it leaves the queue, and the server's state takes its
 * place.
 *
 * Each collector registers its own snapshot listener; nothing is shared or kept alive between
 * collectors. The Firestore client serves duplicate listeners on one document from a single watch, so
 * a second collector costs only its own replay.
 *
 * **Known transient:** the delivery worker refreshes the cached server state before it removes the
 * delivered entry from the queue. In between, that session counts twice. A session the server recorded
 * but whose response never reached the device counts twice until a later delivery attempt removes it.
 */
class DefaultLevelProgressRepository @Inject constructor(
    private val scoringStateRemoteDataSource: ScoringStateRemoteDataSource,
    private val pendingSessionProjector: PendingSessionProjector,
    private val xpConfigRepository: XpConfigRepository,
) : LevelProgressRepository {

    /**
     * Completes, with no further emission, when the remote flow completes (on sign-out), even though
     * the queue is still observed: the completion passes through the projection step and cancels a
     * projection still in flight. Each emission reads the XP configuration once and uses it for both
     * the replay and the Level threshold, so one value never mixes two curves.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeLevelProgress(): Flow<LevelProgress> = flow {
        val remoteScoringState = scoringStateRemoteDataSource.observeScoringState()
            .map<_, LevelProgressEvent> { dto -> LevelProgressEvent.RemoteScoringState(dto?.toDomain()) }
            .retryOnFirestorePermissionDenied()
            .onCompletion { cause -> if (cause == null) emit(LevelProgressEvent.RemoteCompleted) }
        val pendingSessions = pendingSessionProjector.observeDeliverablePendingSessions()
            .map { sessions -> LevelProgressEvent.PendingSessions(sessions) }

        var latestRemoteScoringState: LevelProgressEvent.RemoteScoringState? = null
        var latestPendingSessions: List<SessionResult>? = null
        val projectionSteps = merge(remoteScoringState, pendingSessions).transformWhile { event ->
            when (event) {
                is LevelProgressEvent.RemoteScoringState -> latestRemoteScoringState = event
                is LevelProgressEvent.PendingSessions -> latestPendingSessions = event.sessions
                LevelProgressEvent.RemoteCompleted -> {
                    emit(ProjectionStep.Stop)
                    return@transformWhile false
                }
            }
            val remoteState = latestRemoteScoringState
            val queuedSessions = latestPendingSessions
            if (remoteState != null && queuedSessions != null) emit(ProjectionStep.Project(remoteState.scoringState, queuedSessions))
            true
        }
        emitAll(
            projectionSteps
                .mapLatest { step ->
                    when (step) {
                        is ProjectionStep.Project -> project(step.remoteScoringState, step.pendingSessions)
                        ProjectionStep.Stop -> ProjectionResult.Stopped
                    }
                }
                .transformWhile { result ->
                    if (result is ProjectionResult.Projected) emit(result.levelProgress)
                    result != ProjectionResult.Stopped
                }
                .distinctUntilChanged(),
        )
    }

    /** A stale projection (see [PendingSessionProjector.projectScoringStateOver]) is dropped; the queue change that made it stale re-emits. */
    private suspend fun project(remoteScoringState: ScoringState?, pendingSessions: List<SessionResult>): ProjectionResult {
        val config = xpConfigRepository.getXpConfig().getOrDefault(XpConfig())
        val scoringState = pendingSessionProjector.projectScoringStateOver(remoteScoringState, pendingSessions, config) ?: return ProjectionResult.Stale
        return ProjectionResult.Projected(scoringState.toLevelProgress(config))
    }

    /** One update from either source [observeLevelProgress] follows, including the remote flow's normal completion. */
    private sealed interface LevelProgressEvent {
        data class RemoteScoringState(val scoringState: ScoringState?) : LevelProgressEvent

        data class PendingSessions(val sessions: List<SessionResult>) : LevelProgressEvent

        data object RemoteCompleted : LevelProgressEvent
    }

    /** What to project next: the latest value of each source once both are known, or nothing more once the remote flow completed. */
    private sealed interface ProjectionStep {
        data class Project(val remoteScoringState: ScoringState?, val pendingSessions: List<SessionResult>) : ProjectionStep

        data object Stop : ProjectionStep
    }

    private sealed interface ProjectionResult {
        data class Projected(val levelProgress: LevelProgress) : ProjectionResult

        data object Stale : ProjectionResult

        data object Stopped : ProjectionResult
    }
}
