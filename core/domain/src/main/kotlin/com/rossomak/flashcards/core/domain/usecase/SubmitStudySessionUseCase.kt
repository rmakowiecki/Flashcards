package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.annotation.ArchConventionExempt
import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SessionXpResult
import com.rossomak.flashcards.core.domain.repository.CardProgressRepository
import com.rossomak.flashcards.core.domain.repository.ScoringStateRepository
import com.rossomak.flashcards.core.domain.repository.SessionSubmissionRepository
import javax.inject.Inject

/**
 * Replaces `CommitStudySessionUseCase`'s write responsibility: the server-authoritative
 * `submitStudySession` Cloud Function is now the sole writer of a session's card progress, progress
 * summary and scoring state, so this use case never writes any of them itself. It only does two
 * independent things, called once, on arrival at the Session Summary screen:
 *
 * 1. Computes an **optimistic preview** via the kept, unchanged [CalculateSessionXpUseCase] — from
 *    [sessionResult]'s own data and the account's last-known [ScoringState] — and hands it to
 *    [onPreviewReady] immediately, before [SessionSubmissionRepository.submitSession] is even
 *    attempted.
 * 2. Hands [sessionResult] to [SessionSubmissionRepository] for delivery.
 *
 * The two are deliberately decoupled: the preview's local reads (this account's prior progress and
 * scoring state) exist only to produce a display number, and unlike `CommitStudySessionUseCase`'s old
 * write path, a failure reading either one no longer blocks submitting the session at all — the
 * function needs nothing from this client's own reads to compute its own authoritative answer.
 * [onPreviewReady] therefore receives its own [Result] independently of whatever
 * [SessionSubmissionRepository.submitSession] returns; by design, that
 * outcome carries no further authority here; the delivery queue is what makes delivery durable against being
 * offline or the app being killed.
 */
// TODO: fit onPreviewReady's two-callback shape into UseCase/NoParamUseCase, then drop this exemption.
@ArchConventionExempt
class SubmitStudySessionUseCase @Inject constructor(
    private val cardProgressRepository: CardProgressRepository,
    private val scoringStateRepository: ScoringStateRepository,
    private val calculateSessionXp: CalculateSessionXpUseCase,
    private val sessionSubmissionRepository: SessionSubmissionRepository,
) {

    suspend operator fun invoke(sessionResult: SessionResult, onPreviewReady: (Result<SessionXpResult>) -> Unit) {
        onPreviewReady(computePreview(sessionResult))
        sessionSubmissionRepository.submitSession(sessionResult)
    }

    private suspend fun computePreview(sessionResult: SessionResult): Result<SessionXpResult> {
        val newCardsStudied = countNewCardsStudied(sessionResult)
            .getOrElse { exception -> return Result.failure(exception) }
        val currentScoringState = scoringStateRepository.getScoringState()
            .getOrElse { exception -> return Result.failure(exception) }
            ?: ScoringState()
        val xpResult = calculateSessionXp(
            CalculateSessionXpUseCase.Params(
                sessionResult = sessionResult,
                newCardsStudied = newCardsStudied,
                currentState = currentScoringState,
            ),
        )
        return Result.success(xpResult)
    }

    /**
     * The one piece of `CommitStudySessionUseCase`'s old prior-progress read this preview still
     * needs: how many of [sessionResult]'s cards have no prior entry at all, across every touched
     * Subcategory. Everything else that read once produced — per-card progress updates, mastery
     * deltas — is gone, because the function recomputes and writes all of that itself.
     */
    private suspend fun countNewCardsStudied(sessionResult: SessionResult): Result<Int> {
        var newCardsStudied = 0
        for ((subcategoryId, entries) in sessionResult.cardResults.groupBy(FlashcardResult::subcategoryId)) {
            val priorCards = cardProgressRepository.getProgress(subcategoryId)
                .getOrElse { exception -> return Result.failure(exception) }
                ?.cards
                .orEmpty()
            entries.forEach { entry -> if (entry.cardId !in priorCards) newCardsStudied++ }
        }
        return Result.success(newCardsStudied)
    }
}
