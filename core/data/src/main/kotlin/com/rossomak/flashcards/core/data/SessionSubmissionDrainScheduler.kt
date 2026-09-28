package com.rossomak.flashcards.core.data

import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.rossomak.flashcards.core.data.worker.SessionSubmissionDeliveryWorker
import javax.inject.Inject

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
 * [ExistingWorkPolicy.KEEP]: if a drain is already enqueued or running, [scheduleDrain] is a no-op.
 * This is safe, not lossy — the worker always re-reads the full pending list from
 * [com.rossomak.flashcards.core.data.source.PendingSessionSubmissionLocalDataSource] at the *start* of
 * its own run, so any entry appended before that read is picked up in the same run; an entry appended
 * after a run has already started its read simply waits for the *next* [scheduleDrain] call (the next
 * session finishing, or the next sign-in, app starts included) — by then the previous run has
 * completed and the unique work slot is free again, so `KEEP` no longer blocks the new enqueue.
 *
 * [NetworkType.CONNECTED]: WorkManager itself holds the request unstarted until a network is
 * present, instead of letting it run offline, fail immediately, and burn its first retry/backoff
 * slot for nothing — the request only starts once connectivity is actually plausible.
 */
class SessionSubmissionDrainScheduler @Inject constructor(
    private val workManager: WorkManager,
) {

    fun scheduleDrain() {
        Log.d(TAG, "Scheduling drain worker (unique work=$UNIQUE_WORK_NAME, policy=KEEP, requires network)")
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<SessionSubmissionDeliveryWorker>()
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "session_submission_drain"
        private const val TAG = "SessionSubmissionDrainScheduler"
    }
}
