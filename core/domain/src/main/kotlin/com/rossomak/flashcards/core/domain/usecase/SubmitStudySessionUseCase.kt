package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus
import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus.InFlight
import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus.Scored
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SessionSubmissionResult
import com.rossomak.flashcards.core.domain.model.SessionSubmissionResult.LocalPreview
import com.rossomak.flashcards.core.domain.model.SessionSubmissionResult.ServerScored
import com.rossomak.flashcards.core.domain.repository.CardProgressRepository
import com.rossomak.flashcards.core.domain.repository.ScoringStateRepository
import com.rossomak.flashcards.core.domain.repository.SessionSubmissionRepository
import com.rossomak.flashcards.core.domain.usecase.base.UseCase
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Submits a just-finished session and decides what the Session Summary shows for it, called once on
 * arrival there. The server-authoritative `submitStudySession` Cloud Function is the sole writer of a
 * session's card progress, progress summary and scoring state, so this use case never writes any of them.
 *
 * Order of work:
 * 1. Reads the local preview's baseline: the account's [ScoringState] and the prior Card Progress of
 *    every touched Subcategory, all in parallel. This happens **before** submitting, so the baseline can
 *    never already include this session.
 * 2. Submits the session through [SessionSubmissionRepository] and waits at most [SERVER_RESULT_BUDGET]
 *    for a final delivery status. The budget covers only this wait, not the baseline read before it.
 * 3. [SessionDeliveryStatus.Scored] returns [ServerScored]. Any other final status, or the budget
 *    running out, returns [LocalPreview], computed by [CalculateSessionXpUseCase] from the baseline.
 *
 * It returns exactly once and stops observing the delivery, so a server score arriving after the
 * fallback can never replace what the Summary already shows. The session stays queued either way.
 *
 * A failed baseline read fails the result only when the fallback is needed; the session is submitted
 * regardless, since the function needs nothing from this client's reads.
 */
class SubmitStudySessionUseCase @Inject constructor(
    private val cardProgressRepository: CardProgressRepository,
    private val scoringStateRepository: ScoringStateRepository,
    private val calculateSessionXp: CalculateSessionXpUseCase,
    private val sessionSubmissionRepository: SessionSubmissionRepository,
) : UseCase<SessionResult, Result<SessionSubmissionResult>> {

    override suspend operator fun invoke(params: SessionResult): Result<SessionSubmissionResult> {
        val baseline = readPreviewBaseline(params)
        val deliveryStatus = sessionSubmissionRepository.submitSession(params)
        val finalStatus = withTimeoutOrNull(SERVER_RESULT_BUDGET) { deliveryStatus.first { status -> status != InFlight } }
        if (finalStatus is Scored) return Result.success(ServerScored(finalStatus.score))
        return baseline.map { (newCardsStudied, currentScoringState) ->
            val sessionXpResult = calculateSessionXp(
                CalculateSessionXpUseCase.Params(sessionResult = params, newCardsStudied = newCardsStudied, currentState = currentScoringState),
            )
            LocalPreview(sessionXpResult)
        }
    }

    private suspend fun readPreviewBaseline(sessionResult: SessionResult): Result<PreviewBaseline> = coroutineScope {
        val scoringStateRead = async { scoringStateRepository.getScoringState() }
        val newCardsStudiedReads = sessionResult.cardResults.groupBy(FlashcardResult::subcategoryId).map { (subcategoryId, entries) ->
            async { countNewCards(subcategoryId, entries) }
        }
        val newCardsStudied = newCardsStudiedReads.awaitAll()
            .map { read -> read.getOrElse { exception -> return@coroutineScope Result.failure(exception) } }
            .sum()
        val currentScoringState = scoringStateRead.await()
            .getOrElse { exception -> return@coroutineScope Result.failure(exception) }
            ?: ScoringState()
        Result.success(PreviewBaseline(newCardsStudied = newCardsStudied, currentScoringState = currentScoringState))
    }

    /** How many of [entries] have no prior Card Progress entry in [subcategoryId]. */
    private suspend fun countNewCards(subcategoryId: String, entries: List<FlashcardResult>): Result<Int> =
        cardProgressRepository.getProgress(subcategoryId).map { progress ->
            val priorCards = progress?.cards.orEmpty()
            entries.count { entry -> entry.cardId !in priorCards }
        }

    /** The two account reads the local preview needs, taken before the session is submitted. */
    private data class PreviewBaseline(val newCardsStudied: Int, val currentScoringState: ScoringState)

    companion object {
        /** How long the Session Summary waits for the server's score before showing the local preview. */
        val SERVER_RESULT_BUDGET: Duration = 6.seconds
    }
}
