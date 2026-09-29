// Named for the queue rules it holds, not for its one small result type.
@file:Suppress("MatchingDeclarationName")

package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.model.FlashcardTerminalRating
import com.rossomak.flashcards.core.domain.model.QueueMove
import com.rossomak.flashcards.core.domain.model.RatedSessionCardRecord
import com.rossomak.flashcards.core.domain.model.RatedSessionState
import com.rossomak.flashcards.core.domain.model.ResolvedRatedCard
import com.rossomak.flashcards.core.domain.model.StudySessionConfig
import kotlin.random.Random

/**
 * One [rate] call's result: the next snapshot, alongside the [FlashcardTerminalRating] the rated card
 * resolved to, or `null` when it was re-inserted rather than finished.
 */
internal data class RatedSessionAttemptRatingResult(val state: RatedSessionState, val terminal: FlashcardTerminalRating?)

/** Applies [rating] to [state]'s current (head) card and moves the queue at once. */
internal fun rate(state: RatedSessionState, rating: FlashcardAttemptRating, random: Random): RatedSessionAttemptRatingResult {
    val cardId = state.queue.first().card.id
    val recorded = recordRating(state, rating, random)
    return RatedSessionAttemptRatingResult(state = applyPendingMove(recorded), terminal = recorded.terminalStates[cardId]?.terminalState)
}

/**
 * Records [rating] on [state]'s current (head) card without moving the queue: the head keeps its
 * place, carrying the new Rating, and the move it earned waits in [RatedSessionState.pendingMove]
 * until [applyPendingMove]. A card the Rating resolves joins [RatedSessionState.terminalStates] now.
 * The re-insertion gap is drawn here, so the draw order does not depend on when the move applies.
 */
internal fun recordRating(state: RatedSessionState, rating: FlashcardAttemptRating, random: Random): RatedSessionState {
    val ratedRecord = state.queue.first().let { it.copy(ratings = it.ratings + rating) }
    val ratedQueue = listOf(ratedRecord) + state.queue.drop(1)
    val terminal = resolveFlashcardTerminalRating(state, ratedRecord, rating)
    if (terminal == null) {
        return state.copy(queue = ratedQueue, pendingMove = QueueMove.Requeue(reinsertionGap(random, rating)))
    }
    val resolved = ResolvedRatedCard(record = ratedRecord, terminalState = terminal)
    return state.copy(
        queue = ratedQueue,
        terminalStates = state.terminalStates + (ratedRecord.card.id to resolved),
        pendingMove = QueueMove.Remove,
    )
}

/**
 * A silence timeout on [state]'s current (head) card: no Attempt, no Rating — the record comes back
 * unchanged, using the Failed gap range. A card nobody answered still needs asking, and Failed's gap
 * is the shortest one available. Never terminal — an un-rated card cannot exhaust its Attempts. A
 * voice answer that could not be graded, and a skipped card, are requeued the same way: neither
 * produced a Rating. The move waits in [RatedSessionState.pendingMove] until [applyPendingMove].
 */
internal fun recordSilence(state: RatedSessionState, random: Random): RatedSessionState =
    state.copy(pendingMove = QueueMove.Requeue(silenceGap(random)))

/** [recordSilence], with the queue moved at once. */
internal fun requeueAfterSilence(state: RatedSessionState, random: Random): RatedSessionState =
    applyPendingMove(recordSilence(state, random))

/** Moves the head as [RatedSessionState.pendingMove] says, then clears it. No pending move, no change. */
internal fun applyPendingMove(state: RatedSessionState): RatedSessionState {
    val move = state.pendingMove ?: return state
    val head = state.queue.first()
    val remainingQueue = state.queue.drop(1)
    val nextQueue = when (move) {
        QueueMove.Remove -> remainingQueue
        is QueueMove.Requeue -> remainingQueue.toMutableList().apply { add(move.gap.coerceAtMost(size), head) }
    }
    return state.copy(queue = nextQueue, pendingMove = null)
}

/** Resolution order per ADR-0044: Correct, then an immediately-terminal Partial, then Attempts-exhausted. */
private fun resolveFlashcardTerminalRating(
    state: RatedSessionState,
    record: RatedSessionCardRecord,
    rating: FlashcardAttemptRating,
): FlashcardTerminalRating? = when {
    rating == FlashcardAttemptRating.Correct -> FlashcardTerminalRating.Mastered
    rating == FlashcardAttemptRating.PartiallyCorrect && !state.partialRatingCardRequeueingEnabled -> FlashcardTerminalRating.Partial
    record.attemptsUsed >= state.attemptsLimit ->
        if (record.bestRating == FlashcardAttemptRating.PartiallyCorrect) FlashcardTerminalRating.Partial else FlashcardTerminalRating.Failed
    else -> null
}

/** Draws the re-insertion gap for [rating] (ADR-0046). */
private fun reinsertionGap(random: Random, rating: FlashcardAttemptRating): Int = when (rating) {
    FlashcardAttemptRating.Failed -> silenceGap(random)
    FlashcardAttemptRating.PartiallyCorrect ->
        random.nextInt(StudySessionConfig.PARTIAL_REQUEUE_MIN_GAP, StudySessionConfig.PARTIAL_REQUEUE_MAX_GAP + 1)
    FlashcardAttemptRating.Correct ->
        error("Correct never re-inserts — it resolves Mastered immediately in resolveFlashcardTerminalRating")
}

/** Draws a gap from the Failed range, the one an unanswered card uses too. */
private fun silenceGap(random: Random): Int =
    random.nextInt(StudySessionConfig.FAILED_REQUEUE_MIN_GAP, StudySessionConfig.FAILED_REQUEUE_MAX_GAP + 1)
