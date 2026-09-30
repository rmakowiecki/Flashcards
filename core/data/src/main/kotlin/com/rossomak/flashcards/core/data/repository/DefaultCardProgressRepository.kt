package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.common.logw
import com.rossomak.flashcards.core.data.mapper.toDomain
import com.rossomak.flashcards.core.data.source.CardProgressRemoteDataSource
import com.rossomak.flashcards.core.data.source.ProgressSummaryRemoteDataSource
import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SubcategoryProgress
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressSummary
import com.rossomak.flashcards.core.domain.repository.CardProgressRepository
import com.rossomak.flashcards.core.domain.scoring.SubcategoryProgressDelta
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transformWhile

/**
 * Serves the cached server Card Progress and progress summary with the signed-in User's Pending
 * Sessions replayed on top ([PendingSessionProjector]), so a session studied offline counts as soon
 * as it is queued. Once a Pending Session is delivered it leaves the queue, and the server's state
 * takes its place.
 *
 * **Known limitation:** a Card Progress document stays in the Firestore cache only because it was
 * read once; no listener keeps it there. A Subcategory whose document was never read on this device
 * (a fresh install, a new phone) and is studied offline projects over an empty baseline: cards
 * studied on other devices read as new, and previously Mastered ones are not seen as Mastered. The
 * next online read or delivery corrects it.
 *
 * **Known transient:** the delivery worker refreshes the cached server state before it removes the
 * delivered entry from the queue. In between, the progress summary counts that session twice. Card
 * Progress itself is unaffected, since replaying a session over state that already includes it
 * changes nothing.
 */
class DefaultCardProgressRepository @Inject constructor(
    private val remoteDataSource: CardProgressRemoteDataSource,
    private val progressSummaryRemoteDataSource: ProgressSummaryRemoteDataSource,
    private val pendingSessionProjector: PendingSessionProjector,
) : CardProgressRepository {

    /**
     * With no Pending Session touching [subcategoryId], this is the plain remote read, failure
     * included. Otherwise a failed remote read (typically offline with no cached copy) projects over
     * an empty baseline instead of failing, so the Pending Session results still show.
     *
     * The queue is read before the remote document, so a session delivered in between is replayed
     * over state that already includes it (harmless) rather than missing from both. If the signed-in
     * User changes between the two reads, the queue and the remote document may belong to different
     * Users, so the read fails rather than projecting one User's sessions over the other's progress.
     */
    override suspend fun getProgress(subcategoryId: String): Result<SubcategoryProgress?> {
        val uidAtStart = pendingSessionProjector.signedInUid()
        val pendingSessions = pendingSessionProjector.pendingSessions().filter { session -> session.touches(subcategoryId) }
        val remoteProgress = readRemoteProgress(subcategoryId)
        if (pendingSessions.isEmpty()) return remoteProgress
        if (pendingSessionProjector.signedInUid() != uidAtStart) {
            return Result.failure(IllegalStateException("Signed-in User changed while reading Card Progress for $subcategoryId"))
        }

        val baseline = remoteProgress.getOrElse { exception ->
            logw(exception) { "Card Progress for $subcategoryId unreadable, projecting pending sessions over an empty baseline" }
            null
        }
        return Result.success(pendingSessionProjector.replay(mapOf(subcategoryId to baseline), pendingSessions).progressBySubcategory[subcategoryId])
    }

    /**
     * Completes when the remote summary flow completes (on sign-out), even though the queue is still
     * observed. The two sources are merged rather than combined, so a summary the remote flow emits
     * just before completing is never conflated away. That last summary may carry deltas from before a
     * recalculation still in flight; nothing collects it past sign-out, so it is not worth guarding.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeProgressSummary(): Flow<ProgressSummary?> = flow {
        val remoteSummary = progressSummaryRemoteDataSource.observeSummary()
            .map<_, SummaryEvent> { dto -> SummaryEvent.RemoteSummary(dto?.toDomain()) }
            .retryOnFirestorePermissionDenied()
            .onCompletion { cause -> if (cause == null) emit(SummaryEvent.RemoteCompleted) }
        val pendingDeltas = pendingSessionProjector.observePendingSessions()
            .mapLatest { pendingSessions -> SummaryEvent.PendingDeltas(pendingSummaryDeltas(pendingSessions)) }

        var latestSummary: SummaryEvent.RemoteSummary? = null
        var latestDeltas: Map<String, SubcategoryProgressDelta>? = null
        var remoteCompleted = false
        emitAll(
            merge(remoteSummary, pendingDeltas).transformWhile { event ->
                when (event) {
                    is SummaryEvent.RemoteSummary -> latestSummary = event
                    is SummaryEvent.PendingDeltas -> latestDeltas = event.summaryDeltas
                    SummaryEvent.RemoteCompleted -> remoteCompleted = true
                }
                val summary = latestSummary
                val summaryDeltas = latestDeltas
                if (summary != null && summaryDeltas != null && event != SummaryEvent.RemoteCompleted) emit(summary.summary.plus(summaryDeltas))
                // After the remote flow completes, keep going only to emit its last summary once the
                // first deltas arrive.
                !remoteCompleted || (summary != null && summaryDeltas == null)
            },
        )
    }

    private suspend fun readRemoteProgress(subcategoryId: String): Result<SubcategoryProgress?> =
        runCatchingFirestoreWrite { remoteDataSource.getProgress(subcategoryId)?.toDomain(subcategoryId) }

    /** The replay needs each touched Subcategory's Card Progress to tell new and Mastered cards apart. */
    private suspend fun pendingSummaryDeltas(pendingSessions: List<SessionResult>): Map<String, SubcategoryProgressDelta> {
        if (pendingSessions.isEmpty()) return emptyMap()
        val touchedSubcategoryIds = pendingSessions.flatMap { session -> session.cardResults.map(FlashcardResult::subcategoryId) }.toSet()
        val baselineBySubcategory = coroutineScope {
            touchedSubcategoryIds.map { subcategoryId ->
                async {
                    subcategoryId to readRemoteProgress(subcategoryId).getOrElse { exception ->
                        logw(exception) { "Card Progress for $subcategoryId unreadable, projecting the summary over an empty baseline" }
                        null
                    }
                }
            }.awaitAll().toMap()
        }
        return pendingSessionProjector.replay(baselineBySubcategory, pendingSessions).summaryDeltas
    }

    private fun SessionResult.touches(subcategoryId: String): Boolean = cardResults.any { entry -> entry.subcategoryId == subcategoryId }

    /** A count never drops below zero, even if the summary and the cached Card Progress disagree. */
    private fun ProgressSummary?.plus(summaryDeltas: Map<String, SubcategoryProgressDelta>): ProgressSummary? {
        if (summaryDeltas.isEmpty()) return this
        val subcategories = this?.subcategories.orEmpty()
        val projected = summaryDeltas.mapValues { (subcategoryId, delta) ->
            val prior = subcategories[subcategoryId]
            SubcategoryProgressSummary(
                masteredCount = ((prior?.masteredCount ?: 0) + delta.masteredDelta).coerceAtLeast(0),
                studiedCount = ((prior?.studiedCount ?: 0) + delta.studiedDelta).coerceAtLeast(0),
            )
        }
        return ProgressSummary(subcategories = subcategories + projected)
    }

    /** One update from either source [observeProgressSummary] follows, including the remote flow's normal completion. */
    private sealed interface SummaryEvent {
        data class RemoteSummary(val summary: ProgressSummary?) : SummaryEvent

        data class PendingDeltas(val summaryDeltas: Map<String, SubcategoryProgressDelta>) : SummaryEvent

        data object RemoteCompleted : SummaryEvent
    }
}
