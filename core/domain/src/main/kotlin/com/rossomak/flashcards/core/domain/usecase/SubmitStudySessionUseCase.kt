package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.SessionNotSavedException
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SessionScore
import com.rossomak.flashcards.core.domain.repository.CardProgressRepository
import com.rossomak.flashcards.core.domain.repository.ScoringStateRepository
import com.rossomak.flashcards.core.domain.repository.SessionSubmissionRepository
import com.rossomak.flashcards.core.domain.scoring.scoreSession
import com.rossomak.flashcards.core.domain.usecase.base.UseCase
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Queues a just-finished session for delivery and scores it on the device for the Session Summary,
 * called once on arrival there. The server-authoritative `submitStudySession` Cloud Function is the sole
 * writer of a session's card progress, progress summary and scoring state, so this use case never writes
 * any of them, and it never waits for the server: the Summary always shows this local score, and the
 * queue delivers the session in the background (ADR-0055).
 *
 * Order of work:
 * 1. Reads the score's baseline: the account's [ScoringState] and the prior Card Progress of every
 *    touched Subcategory, all in parallel. Both repositories include the User's other Pending Sessions,
 *    so the score builds on them. This happens **before** queuing, and both reads exclude this session's
 *    own id: after process death the Summary submits again while the session is already queued, and the
 *    baseline must still never include it. Reading after queuing would race the delivery run, whose
 *    cache refresh can already include the session; the scoring-state read then fails, and so does the
 *    score.
 * 2. Queues the session through [SessionSubmissionRepository]. A failure to queue fails with
 *    [SessionNotSavedException]: the session is lost, so no score is offered for XP that will never be
 *    awarded.
 * 3. Scores the session with [scoreSession] from the baseline and the cached XP configuration
 *    ([GetXpConfigUseCase]). A failed baseline read fails the result, but the session stays queued.
 */
class SubmitStudySessionUseCase @Inject constructor(
    private val cardProgressRepository: CardProgressRepository,
    private val scoringStateRepository: ScoringStateRepository,
    private val getXpConfig: GetXpConfigUseCase,
    private val sessionSubmissionRepository: SessionSubmissionRepository,
) : UseCase<SessionResult, Result<SessionScore>> {

    override suspend operator fun invoke(params: SessionResult): Result<SessionScore> {
        val baseline = readBaseline(params)
        sessionSubmissionRepository.submitSession(params).onFailure { exception ->
            return Result.failure(SessionNotSavedException(params.id, exception))
        }
        return baseline.map { (priorCardStatesBySubcategory, scoringState) ->
            scoreSession(priorCardStatesBySubcategory, scoringState, params, getXpConfig()).score
        }
    }

    private suspend fun readBaseline(sessionResult: SessionResult): Result<ScoreBaseline> = coroutineScope {
        val scoringStateRead = async { scoringStateRepository.getScoringState(excludedSessionId = sessionResult.id) }
        val touchedSubcategoryIds = sessionResult.cardResults.map(FlashcardResult::subcategoryId).distinct()
        val cardStateReads = touchedSubcategoryIds.map { subcategoryId -> async { subcategoryId to readPriorCardStates(subcategoryId, sessionResult.id) } }
        val priorCardStatesBySubcategory = cardStateReads.awaitAll().associate { (subcategoryId, read) ->
            subcategoryId to read.getOrElse { exception -> return@coroutineScope Result.failure(exception) }
        }
        val scoringState = scoringStateRead.await()
            .getOrElse { exception -> return@coroutineScope Result.failure(exception) }
            ?: ScoringState()
        Result.success(ScoreBaseline(priorCardStatesBySubcategory = priorCardStatesBySubcategory, scoringState = scoringState))
    }

    /** Card id to its Card Progress state in [subcategoryId] without [sessionId]'s own results; empty when nothing was studied there yet. */
    private suspend fun readPriorCardStates(subcategoryId: String, sessionId: String): Result<Map<String, FlashcardStudyProgressState>> =
        cardProgressRepository.getProgress(subcategoryId, excludedSessionId = sessionId).map { progress ->
            progress?.cards.orEmpty().mapValues { (_, entry) -> entry.state }
        }

    /** The account reads the score needs, taken before the session is queued. */
    private data class ScoreBaseline(
        val priorCardStatesBySubcategory: Map<String, Map<String, FlashcardStudyProgressState>>,
        val scoringState: ScoringState,
    )
}
