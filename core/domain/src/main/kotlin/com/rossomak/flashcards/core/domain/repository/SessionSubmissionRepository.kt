package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.SessionResult

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
     * Queues [sessionResult] for delivery and starts a delivery run, without waiting for it. A failure
     * means the session could not be queued, so nothing on the device holds it. Submitting the same
     * session id again is safe: it is queued once.
     */
    suspend fun submitSession(sessionResult: SessionResult): Result<Unit>
}
