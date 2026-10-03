package com.rossomak.flashcards.core.data

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.rossomak.flashcards.core.common.logd
import com.rossomak.flashcards.core.data.worker.SessionSubmissionDeliveryWorker
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * The one place [SessionSubmissionDeliveryWorker] gets enqueued from. Two call sites
 * share this single class rather than each building their own [androidx.work.OneTimeWorkRequest]:
 * [com.rossomak.flashcards.core.data.repository.DefaultSessionSubmissionRepository] (right after
 * appending a freshly finished session to the local queue) and [SignedInWorkRunner] (on every
 * sign-in, the session Firebase restores at app start included). The sign-in call is both the
 * recovery path for a session queued by a previous process that never got to drain it, and what
 * delivers a User's sessions queued before a sign-out as soon as that User signs in again, with no
 * app restart. An app start with nobody signed in needs no drain: the worker would deliver nothing.
 * One shared definition keeps those call sites' work requests — constraints, backoff policy — from silently drifting apart if only one of
 * them were ever edited.
 *
 * The worker retries transient delivery failures with no attempt limit, so a drain can stay enqueued
 * through a long outage; only a session the server permanently rejects stops being retried.
 *
 * [ExistingWorkPolicy.KEEP] ([scheduleDrain], for sign-in): if a drain is already enqueued or running,
 * [scheduleDrain] is a no-op.
 * This is safe, not lossy — the worker always re-reads the full pending list from
 * [com.rossomak.flashcards.core.data.source.PendingSessionSubmissionLocalDataSource] at the *start* of
 * its own run, so any entry appended before that read is picked up in the same run; an entry appended
 * after a run has already started its read simply waits for the next drain: the next sign-in, app
 * starts included, or the next session finishing. By then the previous run has completed and the
 * unique work slot is free again, so `KEEP` no longer blocks the new enqueue.
 *
 * [ExistingWorkPolicy.REPLACE] ([scheduleDrainForFinishedSession], for a just-finished session): a new
 * drain replaces any drain already enqueued or running. It is needed because under `KEEP`, a drain
 * waiting out a retry backoff, or already past its queue read, would not attempt the new session soon,
 * and the Session Summary waiting on it would fall back to its local preview for nothing. It is safe
 * because the replaced run loses nothing: the new run re-reads the whole queue, and an entry the
 * replaced run delivered but had not yet removed is resubmitted, which `submitStudySession` answers
 * idempotently per session id.
 *
 * [NetworkType.CONNECTED]: WorkManager itself holds the request unstarted until a network is
 * present, instead of letting it run offline, fail immediately, and burn its first retry/backoff
 * slot for nothing — the request only starts once connectivity is actually plausible.
 */
class SessionSubmissionDrainScheduler @Inject constructor(
    private val workManager: WorkManager,
) {

    fun scheduleDrain() {
        logd { "Scheduling drain worker (unique work=$UNIQUE_WORK_NAME, policy=KEEP, requires network)" }
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, buildDrainRequest())
    }

    /** Schedules a drain for a session just queued, replacing any drain already pending, and returns the new request's id to observe with [observeDrain]. */
    fun scheduleDrainForFinishedSession(): UUID {
        logd { "Scheduling drain worker (unique work=$UNIQUE_WORK_NAME, policy=REPLACE, requires network)" }
        val request = buildDrainRequest()
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        return request.id
    }

    /** The state, progress and output of the drain request [requestId]; `null` once WorkManager no longer knows it. */
    fun observeDrain(requestId: UUID): Flow<WorkInfo?> = workManager.getWorkInfoByIdFlow(requestId)

    private fun buildDrainRequest(): OneTimeWorkRequest {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        return OneTimeWorkRequestBuilder<SessionSubmissionDeliveryWorker>()
            .setConstraints(constraints)
            .build()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "session_submission_drain"
    }
}
