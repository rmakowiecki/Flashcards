package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus
import com.rossomak.flashcards.core.domain.model.SessionResult
import kotlinx.coroutines.flow.Flow

/**
 * Delivers a finished session to the server-authoritative `submitStudySession` Cloud Function,
 * which becomes the sole writer of the session's XP, level and progress —
 * `sessions/{sessionId}`, `progress/details/subcategories/{subcategoryId}`, `progress/summary` and
 * `progress/user-stats` are no longer written by this client at all.
 *
 * Named "submission", not "report": this codebase's curation feature already owns "report" for a
 * flagged-content signal ([com.rossomak.flashcards.core.domain.usecase.SubmitCurationReportUseCase]),
 * so a finished session is *submitted*, never *reported*, to keep the two concepts from colliding.
 *
 * A durable, WorkManager-backed queue sits behind this, so an offline or killed-app submission still
 * lands once connectivity returns.
 */
interface SessionSubmissionRepository {

    /**
     * Queues [sessionResult] for delivery and starts a delivery run, then returns a report on this
     * session's delivery: [SessionDeliveryStatus.InFlight] while waiting, then exactly one final status,
     * then completion. Queuing happens before this returns, so cancelling the collection of the
     * returned flow never un-queues the session. Submitting the same session id again is safe: it is
     * queued once, and the server answers a repeated submission with the score it already recorded.
     */
    suspend fun submitSession(sessionResult: SessionResult): Flow<SessionDeliveryStatus>
}
