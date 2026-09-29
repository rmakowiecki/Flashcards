package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.model.FlashcardTerminalRating
import com.rossomak.flashcards.core.domain.model.RatedSessionState
import kotlin.random.Random

/**
 * One [rate] call's result: the next state, alongside the [FlashcardTerminalRating] the rated card
 * resolved to, or `null` when it was re-inserted rather than finished.
 */
internal data class RatedSessionAttemptRatingResult(val state: RatedSessionState, val terminal: FlashcardTerminalRating?)

/** Applies [rating] to [state]'s current (head) card and moves the queue at once. */
internal fun rate(state: RatedSessionState, rating: FlashcardAttemptRating, random: Random): RatedSessionAttemptRatingResult {
    val cardId = state.queue.first().card.id
    val recorded = recordRating(state, rating, random)
    return RatedSessionAttemptRatingResult(state = applyPendingMove(recorded), terminal = recorded.terminalStates[cardId]?.terminalState)
}

/** [recordSilence], with the queue moved at once. */
internal fun requeueAfterSilence(state: RatedSessionState, random: Random): RatedSessionState =
    applyPendingMove(recordSilence(state, random))
