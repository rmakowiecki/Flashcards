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
    data object PlaybackEngineUnavailable : FastSessionInput

    /** The voice stack started again after a text-to-speech engine failure. */
    data object VoiceStackRestarted : FastSessionInput
}

/** What [FastStudySessionCoordinator] must do after a [FastSessionReducer] transition, in order. */
sealed interface FastSessionEffect {
    data object StopVoiceStack : FastSessionEffect
    data class Emit(val event: FastSessionEvent) : FastSessionEffect

    /** The last card's answer has been shown; the session is over. */
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
     * The answer phase for the current index is Studied, recorded before the natural-end check,
     * which relies on the last card already being Seen.
     */
    private fun onPlaybackChanged(state: FastSessionState, playback: VoicePlaybackState): FastSessionTransition {
        if (!playback.isActive) return FastSessionTransition(state)
        var next = state.copy(currentIndex = playback.currentIndex, isAnswerRevealed = playback.phase == VoicePhase.Answer)
        if (playback.phase == VoicePhase.Answer) next = next.markSeen(playback.currentIndex)
        val effects = if (next.isReadAloudNaturalEnd(playback)) listOf(FastSessionEffect.SessionComplete) else emptyList()
        return FastSessionTransition(next, effects)
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

    /**
     * The player settling back on [VoicePhase.Question], not playing, at the last card is unique to
     * its own natural-end branch — a user pause never resets the phase back to Question this way,
     * and requiring the last card to already be Seen rules out the otherwise-identical "never
     * started playing" resting state.
     */
    private fun FastSessionState.isReadAloudNaturalEnd(playback: VoicePlaybackState): Boolean =
        !playback.isPlaying &&
            playback.phase == VoicePhase.Question &&
            playback.totalCards > 0 &&
            playback.currentIndex == playback.totalCards - 1 &&
            cards.getOrNull(playback.currentIndex)?.id in seenCardIds
}
