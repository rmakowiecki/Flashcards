package com.rossomak.flashcards.core.data.source

import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SessionScore

/**
 * Client-side contract for the `submitStudySession` Firebase Callable — the raw network leg only,
 * following the same split as [VoiceGradingRemoteDataSource] already in this codebase: [FirebaseSessionSubmissionRemoteDataSource]
 * talks to the deployment; nothing else in this module needs to depend on
 * [com.google.firebase.functions.FirebaseFunctions] directly.
 *
 * [com.rossomak.flashcards.core.data.worker.SessionSubmissionDeliveryWorker] is the sole caller — it
 * drains the durable local queue and calls this once per entry.
 * [com.rossomak.flashcards.core.data.repository.DefaultSessionSubmissionRepository] (the
 * [com.rossomak.flashcards.core.domain.repository.SessionSubmissionRepository] binding) never calls
 * this itself: its job stops at durably enqueuing, not delivering.
 */
interface SessionSubmissionRemoteDataSource {

    /**
     * Submits [sessionResult] to the `submitStudySession` callable and returns the score it answered
     * with. A successful call whose response cannot be read as a [SessionScore] returns success with
     * `null`: the server already recorded the session, so it is delivered even though its score is
     * unknown. Only a failed call returns a failure.
     */
    suspend fun submitSession(sessionResult: SessionResult): Result<SessionScore?>
}
