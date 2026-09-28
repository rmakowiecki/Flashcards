package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.FastPauseReason
import com.rossomak.flashcards.core.domain.model.FastSessionState
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.TransportCommand
import com.rossomak.flashcards.core.domain.model.VoicePhase
import com.rossomak.flashcards.core.domain.model.VoicePlaybackState
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.MoveToNextCard
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.Pause
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.Play
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.SessionComplete
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.SetAdvanceGate
import com.rossomak.flashcards.core.domain.session.FastSessionInput.AdvanceGateReached
import com.rossomak.flashcards.core.domain.session.FastSessionInput.AdvanceHoldReleased
import com.rossomak.flashcards.core.domain.session.FastSessionInput.AdvanceHoldRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.AnswerRevealed
import com.rossomak.flashcards.core.domain.session.FastSessionInput.JumpRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.NextCardRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.NextRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PauseRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PlayRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PlaybackChanged
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PlaybackEndReached
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PlaybackEngineUnavailable
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PreviousRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.TemporaryPauseEnded
import com.rossomak.flashcards.core.domain.session.FastSessionInput.TemporaryPauseRequested
import javax.inject.Inject

/** Everything that can happen to a Fast Study Session, as [FastSessionReducer] takes it in. */
sealed interface FastSessionInput {

    /** The answer of [cardId] was revealed: by hand, or by the player reading it. */
    data class AnswerRevealed(val cardId: String) : FastSessionInput

    /** The manual "next card", with read-aloud off. Ignored until the answer shows. */
    data object NextCardRequested : FastSessionInput

    /** The voice player's transport state changed. What the screen shows; it never decides a pause. */
    data class PlaybackChanged(val playback: VoicePlaybackState) : FastSessionInput

    /** Read-aloud played past the last card's answer by itself. */
    data object PlaybackEndReached : FastSessionInput
    data object PlaybackEngineUnavailable : FastSessionInput

    /** Play, from the app or from outside it. Also restarts the voice stack after an engine failure. */
    data object PlayRequested : FastSessionInput

    /** Pause, from the app or from outside it. */
    data object PauseRequested : FastSessionInput

    /** The read-aloud Next, from the app or from outside it. */
    data object NextRequested : FastSessionInput

    /** Restart the presented card when [restartsCard], go back one card otherwise. */
    data class PreviousRequested(val restartsCard: Boolean) : FastSessionInput

    /** Jump to the card at [index], from outside the app. */
    data class JumpRequested(val index: Int) : FastSessionInput
    data object TemporaryPauseRequested : FastSessionInput
    data object TemporaryPauseEnded : FastSessionInput
    data object AdvanceHoldRequested : FastSessionInput
    data object AdvanceHoldReleased : FastSessionInput

    /** The player stopped at the closed advance gate, on the presented card. */
    data object AdvanceGateReached : FastSessionInput
}

/** What [FastStudySessionCoordinator] must do after a [FastSessionReducer] transition, in order. */
sealed interface FastSessionEffect {
    data object Play : FastSessionEffect
    data object Pause : FastSessionEffect
    data object ShowAnswer : FastSessionEffect
    data object MoveToNextCard : FastSessionEffect
    data object MoveToPreviousCard : FastSessionEffect
    data object RestartCurrentCard : FastSessionEffect
    data class JumpTo(val index: Int) : FastSessionEffect

    /** Close or open the player's gate at the auto-advance point. */
    data class SetAdvanceGate(val closed: Boolean) : FastSessionEffect

    /** Start the voice stack again at [startIndex], after a text-to-speech engine failure. */
    data class RestartVoiceStack(val startIndex: Int) : FastSessionEffect
    data object StopVoiceStack : FastSessionEffect
    data class Emit(val event: FastSessionEvent) : FastSessionEffect

    /** The last card's answer has been shown or read in full; the session is over. */
    data object SessionComplete : FastSessionEffect
}

/** One-shot things a Fast Study Session reports to its screen. */
sealed interface FastSessionEvent {

    /** A text-to-speech engine could not start; the session is paused until played again. */
    data object VoicePlaybackUnavailable : FastSessionEvent

    /**
     * A transport [command] from outside the app changed the session. Sent after it was applied,
     * and never for a command the session ignored.
     */
    data class ExternalTransportCommand(val command: TransportCommand) : FastSessionEvent

    /** The session ended, completed or abandoned. Sent exactly once. */
    data class SessionEnded(val result: SessionResult) : FastSessionEvent
}

/** A [FastSessionReducer] step: the next state and what the coordinator must do, in order. */
data class FastSessionTransition(val state: FastSessionState, val effects: List<FastSessionEffect> = emptyList())

/**
 * Every rule of a Fast Study Session, as a pure function. A card becomes Studied once its answer
 * shows, read aloud or revealed by hand. With read-aloud on, the player runs its own fixed-list
 * loop; this decides every transport command, who paused the session, and whether the loop stops at
 * the auto-advance point (the end of the pause after an answer) while a hold is requested.
 */
class FastSessionReducer @Inject constructor() {

    fun seed(cards: List<Flashcard>): FastSessionState = FastSessionState(cards = cards)

    @Suppress("CyclomaticComplexMethod") // one branch per input, exhaustive over the sealed type.
    fun reduce(state: FastSessionState, input: FastSessionInput): FastSessionTransition = when (input) {
        is AnswerRevealed -> onAnswerRevealed(state, input.cardId)
        NextCardRequested -> onNextCardRequested(state)
        is PlaybackChanged -> onPlaybackChanged(state, input.playback)
        PlaybackEndReached -> FastSessionTransition(state, listOf(SessionComplete))
        PlaybackEngineUnavailable -> onPlaybackEngineUnavailable(state)
        PlayRequested -> onPlayRequested(state)
        PauseRequested -> onPauseRequested(state)
        NextRequested -> onNextRequested(state)
        is PreviousRequested -> onCardChangeRequested(
            state = state,
            isIgnored = !input.restartsCard && state.currentIndex == 0,
            effect = if (input.restartsCard) FastSessionEffect.RestartCurrentCard else FastSessionEffect.MoveToPreviousCard,
        )
        is JumpRequested -> onCardChangeRequested(state, isIgnored = input.index == state.currentIndex, effect = FastSessionEffect.JumpTo(input.index))
        TemporaryPauseRequested -> onTemporaryPauseRequested(state)
        TemporaryPauseEnded -> if (state.pauseReason == FastPauseReason.Temporary) {
            FastSessionTransition(state.copy(pauseReason = null), listOf(Play))
        } else {
            FastSessionTransition(state)
        }
        AdvanceHoldRequested -> if (state.isAdvanceHoldRequested) {
            FastSessionTransition(state)
        } else {
            FastSessionTransition(state.copy(isAdvanceHoldRequested = true), listOf(SetAdvanceGate(closed = true)))
        }
        AdvanceHoldReleased -> onAdvanceHoldReleased(state)
        AdvanceGateReached -> onAdvanceGateReached(state)
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
            FastSessionTransition(state, listOf(SessionComplete))
        } else {
            FastSessionTransition(state.copy(currentIndex = state.currentIndex + 1, isAnswerRevealed = false))
        }

    /**
     * Only what the screen shows, plus whether the player is playing. Playback state marks nothing
     * Studied, since a quick skip can replace an answer phase before it is observed ([AnswerRevealed]
     * does that), and never ends the session, since a pause, a seek or a speed change can leave the
     * player looking exactly like it finished ([PlaybackEndReached] does that). The player starting
     * to play ends any pause or hold, whatever started it.
     */
    private fun onPlaybackChanged(state: FastSessionState, playback: VoicePlaybackState): FastSessionTransition {
        if (!playback.isActive) return FastSessionTransition(state.copy(isPlaying = false))
        val startedPlaying = playback.isPlaying && !state.isPlaying
        val resumed = if (startedPlaying && state.pauseReason != FastPauseReason.EngineUnavailable) {
            state.copy(pauseReason = null, isHeldAtAdvancePoint = false, isPausedAtAdvancePoint = false)
        } else {
            state
        }
        return FastSessionTransition(
            resumed.copy(
                currentIndex = playback.currentIndex,
                isAnswerRevealed = playback.phase == VoicePhase.Answer,
                isPlaying = playback.isPlaying,
            ),
        )
    }

    /** Play also moves on from the auto-advance point, and restarts the voice stack after an engine failure. */
    private fun onPlayRequested(state: FastSessionState): FastSessionTransition = when {
        state.pauseReason == FastPauseReason.EngineUnavailable -> FastSessionTransition(
            state.copy(pauseReason = null),
            listOf(FastSessionEffect.RestartVoiceStack(startIndex = state.currentIndex)),
        )
        state.isHeldAtAdvancePoint || state.isPausedAtAdvancePoint -> moveOn(state)
        state.pauseReason != null || !state.isPlaying -> FastSessionTransition(state.copy(pauseReason = null), listOf(Play))
        else -> FastSessionTransition(state)
    }

    /**
     * A pause always reaches the player, so it also drops an auto-resume the player has pending.
     * A pause at a hold turns it into a user pause at the auto-advance point.
     */
    private fun onPauseRequested(state: FastSessionState): FastSessionTransition = when {
        state.pauseReason == FastPauseReason.EngineUnavailable -> FastSessionTransition(state)
        state.isHeldAtAdvancePoint -> FastSessionTransition(
            state.copy(pauseReason = FastPauseReason.User, isHeldAtAdvancePoint = false, isPausedAtAdvancePoint = true),
            listOf(Pause),
        )
        else -> FastSessionTransition(state.copy(pauseReason = FastPauseReason.User), listOf(Pause))
    }

    /** At a question it reveals that card's answer; at an answer, or held at the advance point, it moves on. */
    private fun onNextRequested(state: FastSessionState): FastSessionTransition = when {
        !state.isReadAloudNextAvailable -> FastSessionTransition(state)
        state.isHeldAtAdvancePoint -> moveOn(state)
        !state.isAnswerRevealed -> FastSessionTransition(state, listOf(FastSessionEffect.ShowAnswer))
        else -> FastSessionTransition(state.copy(isPausedAtAdvancePoint = false), listOf(MoveToNextCard))
    }

    /** A card change ends a hold, and plays on as the hold would have. A user pause stays paused. */
    private fun onCardChangeRequested(state: FastSessionState, isIgnored: Boolean, effect: FastSessionEffect): FastSessionTransition {
        if (isIgnored) return FastSessionTransition(state)
        val effects = if (state.isHeldAtAdvancePoint) listOf(effect, Play) else listOf(effect)
        return FastSessionTransition(state.copy(isHeldAtAdvancePoint = false, isPausedAtAdvancePoint = false), effects)
    }

    /** Only pauses a session that is playing; a paused one stays paused by whoever paused it. */
    private fun onTemporaryPauseRequested(state: FastSessionState): FastSessionTransition =
        if (state.pauseReason == null && state.isPlaying) {
            FastSessionTransition(state.copy(pauseReason = FastPauseReason.Temporary), listOf(Pause))
        } else {
            FastSessionTransition(state)
        }

    /** Moves on only from a hold still in place; a pause, play or card change already resolved any other. */
    private fun onAdvanceHoldReleased(state: FastSessionState): FastSessionTransition {
        if (!state.isAdvanceHoldRequested && !state.isHeldAtAdvancePoint) return FastSessionTransition(state)
        val released = state.copy(isAdvanceHoldRequested = false)
        val gate = listOf(SetAdvanceGate(closed = false))
        if (!state.isHeldAtAdvancePoint) return FastSessionTransition(released, gate)
        val movedOn = moveOn(released)
        return movedOn.copy(effects = gate + movedOn.effects)
    }

    /**
     * The player stopped at the gate. A user pause that got there first wins; a hold released
     * meanwhile moves on at once.
     */
    private fun onAdvanceGateReached(state: FastSessionState): FastSessionTransition = when {
        state.pauseReason != null -> FastSessionTransition(state.copy(isPausedAtAdvancePoint = true))
        state.isAdvanceHoldRequested -> FastSessionTransition(state.copy(isHeldAtAdvancePoint = true))
        else -> moveOn(state)
    }

    /** From the auto-advance point: the next card plays, or the session ends after the last one. */
    private fun moveOn(state: FastSessionState): FastSessionTransition {
        val moved = state.copy(pauseReason = null, isHeldAtAdvancePoint = false, isPausedAtAdvancePoint = false)
        return if (state.currentIndex >= state.cards.lastIndex) {
            FastSessionTransition(moved, listOf(SessionComplete))
        } else {
            FastSessionTransition(moved, listOf(MoveToNextCard, Play))
        }
    }

    private fun onPlaybackEngineUnavailable(state: FastSessionState): FastSessionTransition {
        if (state.pauseReason == FastPauseReason.EngineUnavailable) return FastSessionTransition(state)
        return FastSessionTransition(
            state.copy(
                pauseReason = FastPauseReason.EngineUnavailable,
                isPlaying = false,
                isHeldAtAdvancePoint = false,
                isPausedAtAdvancePoint = false,
            ),
            listOf(FastSessionEffect.StopVoiceStack, FastSessionEffect.Emit(FastSessionEvent.VoicePlaybackUnavailable)),
        )
    }

    private fun FastSessionState.markSeen(cardId: String): FastSessionState =
        if (cardId in seenCardIds || cards.none { it.id == cardId }) this else copy(seenCardIds = seenCardIds + cardId)
}
