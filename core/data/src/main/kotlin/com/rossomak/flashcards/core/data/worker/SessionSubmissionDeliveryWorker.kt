package com.rossomak.flashcards.core.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.google.firebase.functions.FirebaseFunctionsException
import com.rossomak.flashcards.core.common.logd
import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.common.logw
import com.rossomak.flashcards.core.data.mapper.toDto
import com.rossomak.flashcards.core.data.model.DeadLetteredSessionSubmissionDto
import com.rossomak.flashcards.core.data.model.DeliveredSessionDto
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionDto
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionMapper.toDomain
import com.rossomak.flashcards.core.data.source.DeadLetteredSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.PendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.SessionSubmissionRemoteDataSource
import com.rossomak.flashcards.core.data.worker.EntryDeliveryResult.Continue
import com.rossomak.flashcards.core.data.worker.EntryDeliveryResult.StopAndRetry
import com.rossomak.flashcards.core.data.worker.SessionDeliveryFailure.Permanent
import com.rossomak.flashcards.core.data.worker.SessionDeliveryFailure.Transient
import com.rossomak.flashcards.core.domain.repository.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * Drains the pending-session-submission queue. See
 * [com.rossomak.flashcards.core.data.repository.DefaultSessionSubmissionRepository]'s class doc for
 * the append side of this same queue, and
 * [com.rossomak.flashcards.core.data.source.FilePendingSessionSubmissionLocalDataSource]'s for the
 * local store's shape and concurrency guarantee. Scheduled exclusively via
 * [com.rossomak.flashcards.core.data.SessionSubmissionDrainScheduler] — never constructed or enqueued
 * any other way.
 *
 * **Ownership**: every entry carries the uid of the User who finished the session. [doWork] reads the
 * signed-in uid first. With nobody signed in it returns [Result.success] at once, delivering nothing:
 * [com.rossomak.flashcards.core.data.SignedInWorkRunner] schedules a new drain on the next sign-in.
 * Otherwise it delivers only that User's entries. Other Users' entries stay in the file untouched and
 * never cause a retry, so a run with only foreign entries left ends in [Result.success]; each one is
 * delivered once its own User signs in again. The signed-in uid is checked again before each
 * submission: the callable sends whoever is signed in *now*, so if the User changed mid-run, [doWork]
 * stops and returns [Result.retry] instead of crediting the new User with the old User's sessions.
 * That check can't close the gap between itself and the SDK attaching the ID token inside the call, so
 * each submission also carries the entry's uid and the server rejects a mismatch as `UNAUTHENTICATED`,
 * a transient failure that leaves the entry queued. The
 * retried run reads the new uid; this also covers the drain the new sign-in could not schedule, since
 * `ExistingWorkPolicy.KEEP` ignores it while this run is active.
 *
 * [doWork] sorts the signed-in User's entries FIFO by
 * [com.rossomak.flashcards.core.domain.model.SessionResult.startedAt] (oldest first) and submits each
 * one in turn to [sessionSubmissionRemoteDataSource] — the network-only
 * [com.rossomak.flashcards.core.data.source.SessionSubmissionRemoteDataSource], not the
 * [com.rossomak.flashcards.core.domain.repository.SessionSubmissionRepository] interface, so this
 * worker can never accidentally re-enqueue what it is itself draining. FIFO ordering is a
 * plausibility, not a correctness, requirement: the mastery/demastery/defense-bonus outcomes the
 * `submitStudySession` function computes read the account's *current* state at commit time, so
 * delivering an earlier-started session before a later one keeps those outcomes closer to what they
 * would have been if both had been submitted live.
 *
 * **Failures** are split by [classifySessionDeliveryFailure]:
 * - [SessionDeliveryFailure.Transient] (network, outage, expired token, any non-Functions exception):
 *   [doWork] stops and returns [Result.retry]. The failed entry and every later one stay queued. There
 *   is no attempt limit: a flaky connection can never make the queue give up on a session.
 *   [androidx.work.WorkManager]'s own backoff policy decides when the next run happens.
 * - [SessionDeliveryFailure.Permanent] (the server rejected the session itself): the entry moves to
 *   [deadLetterLocalDataSource] with the failure code, message and a timestamp, is logged at error
 *   level, and the run continues with the next entry.
 *
 * A retried run re-reads [localDataSource] from scratch, so an entry already cleared is never
 * resubmitted; an entry that *was* delivered but failed to clear locally before a crash gets resent on
 * the next run — harmless, since `submitStudySession` is idempotent per session id.
 *
 * **Delivery report**: after each entry the server scored or rejected, [doWork] adds it to
 * [deliveredSessions] and publishes the whole map as progress with [setProgress], right after the
 * submission and before the refresh and removal below, so a Session Summary waiting on this session sees
 * its result as soon as possible. A run that ends in [Result.success] also returns the same map as output
 * data. See [SessionDeliveryReport] for the format and its size cap. WorkManager discards progress and
 * output of a run that returns [Result.retry]; a waiting Summary then shows its local preview instead.
 * Publishing progress is best-effort: a failure to do so is logged and never stops the drain. A session
 * the server recorded but answered unreadably is delivered without a score: it is refreshed and removed
 * like any delivered session but left out of the report, so a waiting Summary shows its local preview.
 *
 * **Post-delivery refresh**: after each successful submission, [doWork] asks
 * [sessionServerStateRefresher] to re-read the User's scoring state and the session's Card Progress
 * documents from the server, updating the Firestore cache. Every failure of this step is ignored; the
 * entry is removed from the queue either way.
 *
 * A queue entry that fails to convert back to a domain [com.rossomak.flashcards.core.domain.model.SessionResult]
 * (no owning uid, unknown `mode`/`state`, or a `Rated` card result missing
 * `attemptsUsed`/`wasPreviouslyMastered`) is malformed beyond repair: [doWork] moves it to
 * [deadLetterLocalDataSource] exactly as stored, with the conversion exception as its failure, and
 * continues to the next entry. A blank-uid entry keeps its blank uid; it is never assigned to the
 * signed-in User.
 *
 * [localDataSource] and [deadLetterLocalDataSource] can throw an [IOException] if a file exists but
 * can't be read (see [com.rossomak.flashcards.core.data.source.FilePendingSessionSubmissionLocalDataSource]
 * for why that's deliberately not swallowed there). [doWork] turns that into [Result.retry]: an
 * unreadable file must never look like a drained, empty queue to WorkManager.
 *
 * Recovery after the app (or the process WorkManager was running in) is killed mid-drain needs no
 * separate code path: [com.rossomak.flashcards.core.data.SignedInWorkRunner] calls
 * [com.rossomak.flashcards.core.data.SessionSubmissionDrainScheduler.scheduleDrain] on every sign-in,
 * the session Firebase restores at app start included; `ExistingWorkPolicy.KEEP` makes that call a
 * safe no-op if a drain is already pending — this worker's next run always starts by reading the file fresh.
 */
@HiltWorker
class SessionSubmissionDeliveryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val sessionSubmissionRemoteDataSource: SessionSubmissionRemoteDataSource,
    private val localDataSource: PendingSessionSubmissionLocalDataSource,
    private val deadLetterLocalDataSource: DeadLetteredSessionSubmissionLocalDataSource,
    private val sessionServerStateRefresher: SessionServerStateRefresher,
    private val authRepository: AuthRepository,
) : CoroutineWorker(context, workerParameters) {

    /** Every session this run resolved, in delivery order: its server score, or the rejection marker. */
    private val deliveredSessions = LinkedHashMap<String, DeliveredSessionDto>()

    override suspend fun doWork(): Result {
        val signedInUid = authRepository.getCurrentUser()?.uid
        if (signedInUid == null) {
            logd { "Drain skipped: nobody is signed in, the next sign-in schedules a new drain" }
            return Result.success()
        }
        return try {
            drain(signedInUid)
        } catch (exception: IOException) {
            loge(exception) { "Session submission queue files could not be read or written, retrying the drain" }
            Result.retry()
        }
    }

    private suspend fun drain(signedInUid: String): Result {
        // A blank uid is not another User's entry but a malformed one: keep it in the run so the
        // conversion below removes it.
        val ownEntries = localDataSource.listAll()
            .filter { entry -> entry.uid == signedInUid || entry.uid.isBlank() }
            .sortedBy { it.startedAtEpochMillis }
        logd { "Drain started: ${ownEntries.size} pending entries for the signed-in User (attempt ${runAttemptCount + 1})" }
        for (entry in ownEntries) {
            if (authRepository.getCurrentUser()?.uid != signedInUid) {
                logd { "Signed-in User changed mid-drain, returning retry so the next run drains the new User's entries" }
                return Result.retry()
            }
            if (deliver(entry) == StopAndRetry) return Result.retry()
        }
        logd { "Drain finished: all ${ownEntries.size} entries for the signed-in User resolved" }
        return Result.success(SessionDeliveryReport.toData(deliveredSessions))
    }

    private suspend fun deliver(entry: PendingSessionSubmissionDto): EntryDeliveryResult {
        val sessionResult = try {
            entry.toDomain()
        } catch (exception: IllegalArgumentException) {
            deadLetter(entry, exception)
            return Continue
        }
        val failure = sessionSubmissionRemoteDataSource.submitSession(entry.uid, sessionResult).fold(
            onSuccess = { score ->
                if (score != null) report(entry.id, DeliveredSessionDto.Scored(score.toDto()))
                sessionServerStateRefresher.refresh(sessionResult)
                localDataSource.remove(entry.id)
                logd { "Session ${entry.id} delivered and removed from queue" }
                return Continue
            },
            onFailure = { exception -> exception },
        )
        return when (classifySessionDeliveryFailure(failure)) {
            Transient -> {
                logw(failure) { "Submission of session ${entry.id} failed transiently, stopping drain and returning retry" }
                StopAndRetry
            }
            Permanent -> {
                report(entry.id, DeliveredSessionDto.Rejected)
                deadLetter(entry, failure)
                Continue
            }
        }
    }

    // Broad on purpose: the report is best-effort, so no failure to publish it may stop the drain.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun report(sessionId: String, deliveredSession: DeliveredSessionDto) {
        deliveredSessions[sessionId] = deliveredSession
        try {
            setProgress(SessionDeliveryReport.toData(deliveredSessions))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logw(exception) { "Could not publish the delivery report after session $sessionId" }
        }
    }

    /**
     * Appends to the dead-letter record before removing from the queue, so a crash in between duplicates the
     * entry instead of losing it. [failure] is either the server's permanent rejection or the
     * [IllegalArgumentException] of an entry that could not be converted.
     */
    private suspend fun deadLetter(entry: PendingSessionSubmissionDto, failure: Throwable) {
        val failureCode = (failure as? FirebaseFunctionsException)?.code?.name ?: failure.javaClass.simpleName
        deadLetterLocalDataSource.append(
            DeadLetteredSessionSubmissionDto(
                entry = entry,
                failureCode = failureCode,
                failureMessage = failure.message,
                deadLetteredAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        localDataSource.remove(entry.id)
        loge(failure) { "Session ${entry.id} is undeliverable ($failureCode), moved to the dead-letter record" }
    }
}

/** What the drain does after one entry: move on to the next one, or stop the run and retry later. */
private enum class EntryDeliveryResult { Continue, StopAndRetry }
