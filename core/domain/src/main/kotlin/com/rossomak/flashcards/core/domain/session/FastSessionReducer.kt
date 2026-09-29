package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.FastPauseReason
import com.rossomak.flashcards.core.domain.model.FastSessionState
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.ReadAloudStep
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.TransportCommand
import com.rossomak.flashcards.core.domain.model.VoicePlaybackState
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.CancelReadAloudPause
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.Pause
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.Play
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.PresentAnswer
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.PresentQuestion
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.SessionComplete
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.StartAdvancePause
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.StartQuestionPause
import com.rossomak.flashcards.core.domain.session.FastSessionInput.AdvanceHoldReleased
import com.rossomak.flashcards.core.domain.session.FastSessionInput.AdvanceHoldRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.AdvancePauseElapsed
import com.rossomak.flashcards.core.domain.session.FastSessionInput.AnswerFinished
import com.rossomak.flashcards.core.domain.session.FastSessionInput.AnswerRevealed
import com.rossomak.flashcards.core.domain.session.FastSessionInput.JumpRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.NextCardRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.NextRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PauseRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PlayRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PlaybackChanged
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PlaybackEngineUnavailable
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PreviousRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.QuestionFinished
import com.rossomak.flashcards.core.domain.session.FastSessionInput.QuestionPauseElapsed
import com.rossomak.flashcards.core.domain.session.FastSessionInput.TemporaryPauseEnded
import com.rossomak.flashcards.core.domain.session.FastSessionInput.TemporaryPauseRequested
import javax.inject.Inject

/** Everything that can happen to a Fast Study Session, as [FastSessionReducer] takes it in. */
sealed interface FastSessionInput {

    /** The answer of [cardId] was revealed: by hand, or by the player presenting it. */
    data class AnswerRevealed(val cardId: String) : FastSessionInput

    /** The manual "next card", with read-aloud off. Ignored until the answer shows. */
    data object NextCardRequested : FastSessionInput

    /** The voice player's transport state changed. Only whether it plays is read from it. */
    data class PlaybackChanged(val playback: VoicePlaybackState) : FastSessionInput
    data object PlaybackEngineUnavailable : FastSessionInput

    /** The player read the question of [cardId] in full. */
    data class QuestionFinished(val cardId: String) : FastSessionInput

    /** The player read the answer of [cardId] in full. */
    data class AnswerFinished(val cardId: String) : FastSessionInput

    /** The pause between the presented card's question and its answer is over. */
    data object QuestionPauseElapsed : FastSessionInput

    /** The pause after the presented card's answer is over: the auto-advance point. */
    data object AdvancePauseElapsed : FastSessionInput

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
}

/** What [FastStudySessionCoordinator] must do after a [FastSessionReducer] transition, in order. */
sealed interface FastSessionEffect {
    data object Play : FastSessionEffect
    data object Pause : FastSessionEffect

    /** Present the question of the card at [index]; the player reads it while playing. */
    data class PresentQuestion(val index: Int) : FastSessionEffect

    /** Present the answer of the card at [index]; the player reads it while playing. */
    data class PresentAnswer(val index: Int) : FastSessionEffect

    /** Start the pause between a question and its answer; it ends in [FastSessionInput.QuestionPauseElapsed]. */
    data object StartQuestionPause : FastSessionEffect

    /** Start the pause after an answer; it ends in [FastSessionInput.AdvancePauseElapsed]. */
    data object StartAdvancePause : FastSessionEffect

    /** Stop the running read-aloud pause, if any, without it elapsing. */
    data object CancelReadAloudPause : FastSessionEffect

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
 * shows, read aloud or revealed by hand. With read-aloud on, this runs the question, pause, answer,
 * pause, next card loop: it tells the player which part of which card to present, starts the two
 * pauses, and decides at the auto-advance point (the end of the pause after an answer) whether to
 * move on or stop there while a hold is requested. It also decides every transport command and who
 * paused the session.
 */
@Suppress("TooManyFunctions") // one handler per input.
class FastSessionReducer @Inject constructor() {

    fun seed(cards: List<Flashcard>): FastSessionState = FastSessionState(cards = cards)

    @Suppress("CyclomaticComplexMethod") // one branch per input, exhaustive over the sealed type.
    fun reduce(state: FastSessionState, input: FastSessionInput): FastSessionTransition = when (input) {
        is AnswerRevealed -> onAnswerRevealed(state, input.cardId)
        NextCardRequested -> onNextCardRequested(state)
        is PlaybackChanged -> onPlaybackChanged(state, input.playback)
        PlaybackEngineUnavailable -> onPlaybackEngineUnavailable(state)
        is QuestionFinished -> onPartFinished(state, input.cardId, ReadAloudStep.Question)
        is AnswerFinished -> onPartFinished(state, input.cardId, ReadAloudStep.Answer)
        QuestionPauseElapsed -> if (state.readAloudStep == ReadAloudStep.QuestionPause && state.isReading) {
            presentAnswer(state, cancelsPause = false, plays = false)
        } else {
            FastSessionTransition(state)
        }
        AdvancePauseElapsed -> onAdvancePoint(state)
        PlayRequested -> onPlayRequested(state)
        PauseRequested -> onPauseRequested(state)
        NextRequested -> onNextRequested(state)
        is PreviousRequested -> onCardChangeRequested(
            state = state,
            targetIndex = if (input.restartsCard) state.currentIndex else state.currentIndex - 1,
            isIgnored = !input.restartsCard && state.currentIndex == 0,
        )
        is JumpRequested -> {
            val targetIndex = input.index.coerceIn(0, maxOf(0, state.cards.lastIndex))
            onCardChangeRequested(state, targetIndex, isIgnored = targetIndex == state.currentIndex)
        }
        TemporaryPauseRequested -> onTemporaryPauseRequested(state)
        TemporaryPauseEnded -> if (state.pauseReason == FastPauseReason.Temporary) resume(state) else FastSessionTransition(state)
        AdvanceHoldRequested -> FastSessionTransition(state.copy(isAdvanceHoldRequested = true))
        AdvanceHoldReleased -> onAdvanceHoldReleased(state)
    }

    /**
     * Revealing an answer is what makes a card Studied. The player reports the card it presented,
     * which can already be behind the presented one after a quick skip; it still counts.
     */
    private fun onAnswerRevealed(state: FastSessionState, cardId: String): FastSessionTransition {
        val seen = state.markSeen(cardId)
        return FastSessionTransition(if (state.isPresented(cardId)) seen.copy(isAnswerRevealed = true) else seen)
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
     * Only whether the player plays. The player starting to play ends any pause or hold, whatever
     * started it, and inside a read-aloud pause it goes on to the next step (an audio-focus gain
     * resumes the player without reading). The player stopping by itself inside a pause stops the
     * pause, which the next resume then skips.
     */
    private fun onPlaybackChanged(state: FastSessionState, playback: VoicePlaybackState): FastSessionTransition {
        if (!playback.isActive) return FastSessionTransition(state.copy(isPlaying = false))
        val startedPlaying = playback.isPlaying && !state.isPlaying
        val stoppedPlaying = !playback.isPlaying && state.isPlaying
        val updated = state.copy(isPlaying = playback.isPlaying)
        return when {
            startedPlaying && state.pauseReason != FastPauseReason.EngineUnavailable -> {
                val playing = updated.copy(pauseReason = null, isHeldAtAdvancePoint = false, isPausedAtAdvancePoint = false)
                if (state.readAloudStep.isPause) resume(playing) else FastSessionTransition(playing)
            }
            stoppedPlaying && state.readAloudStep.isPause -> FastSessionTransition(updated, listOf(CancelReadAloudPause))
            else -> FastSessionTransition(updated)
        }
    }

    /** A part read in full starts the pause after it. A stale report (another card or part, or a paused session) does nothing. */
    private fun onPartFinished(state: FastSessionState, cardId: String, step: ReadAloudStep): FastSessionTransition {
        if (state.readAloudStep != step || !state.isPresented(cardId) || !state.isReading) return FastSessionTransition(state)
        return if (step == ReadAloudStep.Question) {
            FastSessionTransition(state.copy(readAloudStep = ReadAloudStep.QuestionPause), listOf(StartQuestionPause))
        } else {
            FastSessionTransition(state.copy(readAloudStep = ReadAloudStep.AdvancePause), listOf(StartAdvancePause))
        }
    }

    /** Play also restarts the voice stack after an engine failure; see [resume] for the rest. */
    private fun onPlayRequested(state: FastSessionState): FastSessionTransition = when {
        state.pauseReason == FastPauseReason.EngineUnavailable -> FastSessionTransition(
            state.copy(pauseReason = null, readAloudStep = ReadAloudStep.Question, isAnswerRevealed = false),
            listOf(FastSessionEffect.RestartVoiceStack(startIndex = state.currentIndex)),
        )
        state.isReading -> FastSessionTransition(state)
        else -> resume(state)
    }

    /**
     * Every resume goes through here: play, a temporary pause ending, and the player playing again by
     * itself. Inside a read-aloud pause it goes on to the next step, and the rest of the pause is
     * dropped: the answer after the question pause, the next card (or the end) after the advance
     * pause. Inside a part, the player reads that part again from its start.
     */
    private fun resume(state: FastSessionState): FastSessionTransition {
        val plays = !state.isReading
        val resumed = state.copy(pauseReason = null, isHeldAtAdvancePoint = false, isPausedAtAdvancePoint = false)
        return when (state.readAloudStep) {
            ReadAloudStep.QuestionPause -> presentAnswer(resumed, cancelsPause = false, plays = plays)
            ReadAloudStep.AdvancePause -> moveOn(state)
            ReadAloudStep.Question, ReadAloudStep.Answer -> FastSessionTransition(resumed, if (plays) listOf(Play) else emptyList())
        }
    }

    /**
     * A pause always reaches the player, so it also drops an auto-resume the player has pending, and
     * stops a running read-aloud pause. A pause at a hold turns it into a user pause at the
     * auto-advance point.
     */
    private fun onPauseRequested(state: FastSessionState): FastSessionTransition = when {
        state.pauseReason == FastPauseReason.EngineUnavailable -> FastSessionTransition(state)
        state.isHeldAtAdvancePoint -> FastSessionTransition(
            state.copy(pauseReason = FastPauseReason.User, isHeldAtAdvancePoint = false, isPausedAtAdvancePoint = true),
            listOf(Pause),
        )
        else -> FastSessionTransition(state.copy(pauseReason = FastPauseReason.User), state.cancelPauseEffects() + Pause)
    }

    /**
     * At a question, or in the pause after it, it presents that card's answer; at an answer, in the
     * pause after it, or held at the advance point, it moves on.
     */
    private fun onNextRequested(state: FastSessionState): FastSessionTransition = when {
        !state.isReadAloudNextAvailable -> FastSessionTransition(state)
        state.isHeldAtAdvancePoint -> moveOn(state)
        state.readAloudStep == ReadAloudStep.Question || state.readAloudStep == ReadAloudStep.QuestionPause ->
            presentAnswer(state, cancelsPause = true, plays = false)
        else -> presentQuestion(state.copy(isPausedAtAdvancePoint = false), state.currentIndex + 1, cancelsPause = true, plays = false)
    }

    /** A card change ends a hold, and plays on as the hold would have. A user pause stays paused. */
    private fun onCardChangeRequested(state: FastSessionState, targetIndex: Int, isIgnored: Boolean): FastSessionTransition {
        if (isIgnored) return FastSessionTransition(state)
        return presentQuestion(
            state = state.copy(isHeldAtAdvancePoint = false, isPausedAtAdvancePoint = false),
            index = targetIndex,
            cancelsPause = true,
            plays = state.isHeldAtAdvancePoint,
        )
    }

    /** Only pauses a session that is playing; a paused one stays paused by whoever paused it. */
    private fun onTemporaryPauseRequested(state: FastSessionState): FastSessionTransition =
        if (state.pauseReason == null && state.isPlaying) {
            FastSessionTransition(state.copy(pauseReason = FastPauseReason.Temporary), state.cancelPauseEffects() + Pause)
        } else {
            FastSessionTransition(state)
        }

    /** Moves on only from a hold still in place; a pause, play or card change already resolved any other. */
    private fun onAdvanceHoldReleased(state: FastSessionState): FastSessionTransition {
        if (!state.isAdvanceHoldRequested && !state.isHeldAtAdvancePoint) return FastSessionTransition(state)
        val released = state.copy(isAdvanceHoldRequested = false)
        return if (state.isHeldAtAdvancePoint) moveOn(released) else FastSessionTransition(released)
    }

    /**
     * The pause after an answer is over. A pause that got there first wins; a requested hold stops
     * the session on the presented card, paused; otherwise it moves on.
     */
    private fun onAdvancePoint(state: FastSessionState): FastSessionTransition = when {
        state.readAloudStep != ReadAloudStep.AdvancePause || state.isHeldAtAdvancePoint -> FastSessionTransition(state)
        state.pauseReason != null -> FastSessionTransition(state.copy(isPausedAtAdvancePoint = true))
        state.isAdvanceHoldRequested -> FastSessionTransition(state.copy(isHeldAtAdvancePoint = true), listOf(Pause))
        else -> moveOn(state)
    }

    /** From the auto-advance point: the next card plays, or the session ends after the last one. */
    private fun moveOn(state: FastSessionState): FastSessionTransition {
        val moved = state.copy(pauseReason = null, isHeldAtAdvancePoint = false, isPausedAtAdvancePoint = false)
        return if (state.currentIndex >= state.cards.lastIndex) {
            FastSessionTransition(moved, listOf(SessionComplete))
        } else {
            presentQuestion(moved, state.currentIndex + 1, cancelsPause = false, plays = !state.isReading)
        }
    }

    private fun presentQuestion(state: FastSessionState, index: Int, cancelsPause: Boolean, plays: Boolean): FastSessionTransition {
        val effects = buildList {
            if (cancelsPause) addAll(state.cancelPauseEffects())
            add(PresentQuestion(index))
            if (plays) add(Play)
        }
        return FastSessionTransition(state.copy(currentIndex = index, readAloudStep = ReadAloudStep.Question, isAnswerRevealed = false), effects)
    }

    private fun presentAnswer(state: FastSessionState, cancelsPause: Boolean, plays: Boolean): FastSessionTransition {
        val effects = buildList {
            if (cancelsPause) addAll(state.cancelPauseEffects())
            add(PresentAnswer(state.currentIndex))
            if (plays) add(Play)
        }
        return FastSessionTransition(state.copy(readAloudStep = ReadAloudStep.Answer, isAnswerRevealed = true), effects)
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
            state.cancelPauseEffects() + listOf(FastSessionEffect.StopVoiceStack, FastSessionEffect.Emit(FastSessionEvent.VoicePlaybackUnavailable)),
        )
    }

    /** The player plays and nothing paused or held the session. */
    private val FastSessionState.isReading: Boolean
        get() = isPlaying && pauseReason == null && !isHeldAtAdvancePoint && !isPausedAtAdvancePoint

    private fun FastSessionState.isPresented(cardId: String): Boolean = cards.getOrNull(currentIndex)?.id == cardId

    private fun FastSessionState.cancelPauseEffects(): List<FastSessionEffect> =
        if (readAloudStep.isPause) listOf(CancelReadAloudPause) else emptyList()

    private fun FastSessionState.markSeen(cardId: String): FastSessionState =
        if (cardId in seenCardIds || cards.none { it.id == cardId }) this else copy(seenCardIds = seenCardIds + cardId)
}
