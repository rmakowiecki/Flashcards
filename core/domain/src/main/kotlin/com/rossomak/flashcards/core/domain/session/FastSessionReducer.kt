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

    /** The user revealed the answer by hand, with read-aloud off. */
    data object AnswerRevealed : FastSessionInput

    /** The manual "next card", with read-aloud off. */
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
        AnswerRevealed -> FastSessionTransition(state.markSeen(state.currentIndex).copy(isAnswerRevealed = true))
        NextCardRequested -> onNextCardRequested(state)
        is PlaybackChanged -> onPlaybackChanged(state, input.playback)
        PlaybackEndReached -> FastSessionTransition(state, listOf(FastSessionEffect.SessionComplete))
        PlaybackEngineUnavailable -> onPlaybackEngineUnavailable(state)
        VoiceStackRestarted -> FastSessionTransition(state.copy(pauseReason = null))
    }

    /** Only reachable once the answer shows, so the last card is always Studied before this ends the session. */
    private fun onNextCardRequested(state: FastSessionState): FastSessionTransition =
        if (state.currentIndex >= state.cards.lastIndex) {
            FastSessionTransition(state, listOf(FastSessionEffect.SessionComplete))
        } else {
            FastSessionTransition(state.copy(currentIndex = state.currentIndex + 1, isAnswerRevealed = false))
        }

    /**
     * The answer phase for the current index is Studied. Playback state never ends the session:
     * a pause, a seek or a speed change can leave the player looking exactly like it finished, so
     * only [PlaybackEndReached] does.
     */
    private fun onPlaybackChanged(state: FastSessionState, playback: VoicePlaybackState): FastSessionTransition {
        if (!playback.isActive) return FastSessionTransition(state)
        val next = state.copy(currentIndex = playback.currentIndex, isAnswerRevealed = playback.phase == VoicePhase.Answer)
        return FastSessionTransition(if (playback.phase == VoicePhase.Answer) next.markSeen(playback.currentIndex) else next)
    }

    private fun onPlaybackEngineUnavailable(state: FastSessionState): FastSessionTransition {
        if (state.pauseReason != null) return FastSessionTransition(state)
        return FastSessionTransition(
            state.copy(pauseReason = SessionPauseReason.VoiceEngineUnavailable),
            listOf(FastSessionEffect.StopVoiceStack, FastSessionEffect.Emit(FastSessionEvent.VoicePlaybackUnavailable)),
        )
    }

    private fun FastSessionState.markSeen(cardIndex: Int): FastSessionState {
        val cardId = cards.getOrNull(cardIndex)?.id ?: return this
        return if (cardId in seenCardIds) this else copy(seenCardIds = seenCardIds + cardId)
    }
}
