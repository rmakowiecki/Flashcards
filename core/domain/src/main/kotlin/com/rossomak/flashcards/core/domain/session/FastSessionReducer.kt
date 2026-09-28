package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.FastSessionState
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.SessionPauseReason
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.VoicePhase
import com.rossomak.flashcards.core.domain.model.VoicePlaybackState
import com.rossomak.flashcards.core.domain.session.FastSessionInput.AnswerRevealed
import com.rossomak.flashcards.core.domain.session.FastSessionInput.NextCardRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PlaybackChanged
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PlaybackEndReached
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PlaybackEngineUnavailable
import com.rossomak.flashcards.core.domain.session.FastSessionInput.VoiceStackRestarted
import javax.inject.Inject

/** Everything that can happen to a Fast Study Session, as [FastSessionReducer] takes it in. */
sealed interface FastSessionInput {

    /** The answer of [cardId] was revealed: by hand, or by the player reading it. */
    data class AnswerRevealed(val cardId: String) : FastSessionInput

    /** The manual "next card", with read-aloud off. Ignored until the answer shows. */
    data object NextCardRequested : FastSessionInput
    data class PlaybackChanged(val playback: VoicePlaybackState) : FastSessionInput

    /** Read-aloud played past the last card's answer by itself. */
    data object PlaybackEndReached : FastSessionInput
    data object PlaybackEngineUnavailable : FastSessionInput

    /** The voice stack started again after a text-to-speech engine failure. */
    data object VoiceStackRestarted : FastSessionInput
}

/** What [FastStudySessionCoordinator] must do after a [FastSessionReducer] transition, in order. */
sealed interface FastSessionEffect {
    data object StopVoiceStack : FastSessionEffect
    data class Emit(val event: FastSessionEvent) : FastSessionEffect

    /** The last card's answer has been shown or read in full; the session is over. */
    data object SessionComplete : FastSessionEffect
}

/** One-shot things a Fast Study Session reports to its screen. */
sealed interface FastSessionEvent {

    /** A text-to-speech engine could not start; the session is paused until played again. */
    data object VoicePlaybackUnavailable : FastSessionEvent

    /** The session ended, completed or abandoned. Sent exactly once. */
    data class SessionEnded(val result: SessionResult) : FastSessionEvent
}

/** A [FastSessionReducer] step: the next state and what the coordinator must do, in order. */
data class FastSessionTransition(val state: FastSessionState, val effects: List<FastSessionEffect> = emptyList())

/**
 * Every rule of a Fast Study Session, as a pure function. A card becomes Studied once its answer
 * shows, read aloud or revealed by hand. With read-aloud on, the player runs its own fixed-list
 * loop; this only observes it.
 */
class FastSessionReducer @Inject constructor() {

    fun seed(cards: List<Flashcard>): FastSessionState = FastSessionState(cards = cards)

    fun reduce(state: FastSessionState, input: FastSessionInput): FastSessionTransition = when (input) {
        is AnswerRevealed -> onAnswerRevealed(state, input.cardId)
        NextCardRequested -> onNextCardRequested(state)
        is PlaybackChanged -> onPlaybackChanged(state, input.playback)
        PlaybackEndReached -> FastSessionTransition(state, listOf(FastSessionEffect.SessionComplete))
        PlaybackEngineUnavailable -> onPlaybackEngineUnavailable(state)
        VoiceStackRestarted -> FastSessionTransition(state.copy(pauseReason = null))
    }

    /**
     * Revealing an answer is what makes a card Studied. The player reports the card it read, which
     * can already be behind the presented one after a quick skip; it still counts.
     */
    private fun onAnswerRevealed(state: FastSessionState, cardId: String): FastSessionTransition {
        val seen = state.markSeen(cardId)
        val isPresentedCard = state.cards.getOrNull(state.currentIndex)?.id == cardId
        return FastSessionTransition(if (isPresentedCard) seen.copy(isAnswerRevealed = true) else seen)
    }

    /** Ignored until the answer shows, so the last card is always Studied before this ends the session. */
    private fun onNextCardRequested(state: FastSessionState): FastSessionTransition =
        if (!state.isAnswerRevealed) {
            FastSessionTransition(state)
        } else if (state.currentIndex >= state.cards.lastIndex) {
            FastSessionTransition(state, listOf(FastSessionEffect.SessionComplete))
        } else {
            FastSessionTransition(state.copy(currentIndex = state.currentIndex + 1, isAnswerRevealed = false))
        }

    /**
     * Only what the screen shows. Playback state marks nothing Studied, since a quick skip can
     * replace an answer phase before it is observed ([AnswerRevealed] does that), and never ends
     * the session, since a pause, a seek or a speed change can leave the player looking exactly
     * like it finished ([PlaybackEndReached] does that).
     */
    private fun onPlaybackChanged(state: FastSessionState, playback: VoicePlaybackState): FastSessionTransition {
        if (!playback.isActive) return FastSessionTransition(state)
        return FastSessionTransition(state.copy(currentIndex = playback.currentIndex, isAnswerRevealed = playback.phase == VoicePhase.Answer))
    }

    private fun onPlaybackEngineUnavailable(state: FastSessionState): FastSessionTransition {
        if (state.pauseReason != null) return FastSessionTransition(state)
        return FastSessionTransition(
            state.copy(pauseReason = SessionPauseReason.VoiceEngineUnavailable),
            listOf(FastSessionEffect.StopVoiceStack, FastSessionEffect.Emit(FastSessionEvent.VoicePlaybackUnavailable)),
        )
    }

    private fun FastSessionState.markSeen(cardId: String): FastSessionState =
        if (cardId in seenCardIds || cards.none { it.id == cardId }) this else copy(seenCardIds = seenCardIds + cardId)
}
