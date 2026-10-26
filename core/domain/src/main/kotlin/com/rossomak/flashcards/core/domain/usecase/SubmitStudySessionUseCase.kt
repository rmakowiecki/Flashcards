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
 * Queues a just-finished session and scores it on the device for the Session Summary, never waiting for
 * the server, which records it once the queue delivers it (ADR-0055).
 *
 * 1. Reads the baseline, the [ScoringState] and each touched Subcategory's Card Progress, from the device
 *    cache only, with the User's other Pending Sessions on top and this session left out (a restored
 *    Summary submits again after it is queued). It reads before queuing, since the delivery run's cache
 *    refresh could already include the session.
 * 2. Queues the session. A failure returns [SessionNotSavedException] and no score.
 * 3. Scores it with [scoreSession] and the cached XP configuration. A failed baseline read fails the
 *    score, but the session stays queued.
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
        val scoringStateRead = async { scoringStateRepository.getScoringState(excludedSessionId = sessionResult.id, cacheOnly = true) }
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
        cardProgressRepository.getProgress(subcategoryId, excludedSessionId = sessionId, cacheOnly = true).map { progress ->
            progress?.cards.orEmpty().mapValues { (_, entry) -> entry.state }
        }

    private data class ScoreBaseline(
        val priorCardStatesBySubcategory: Map<String, Map<String, FlashcardStudyProgressState>>,
        val scoringState: ScoringState,
    )
}
