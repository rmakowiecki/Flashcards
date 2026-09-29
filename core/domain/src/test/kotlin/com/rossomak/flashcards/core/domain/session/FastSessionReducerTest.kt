package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.FastPauseReason
import com.rossomak.flashcards.core.domain.model.FastSessionState
import com.rossomak.flashcards.core.domain.model.Flashcard
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
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.Test

class FastSessionReducerTest {

    private val reducer = FastSessionReducer()

    private fun flashcard(id: String): Flashcard = Flashcard(
        id = id,
        subcategoryId = "sub-1",
        tags = emptyList(),
        question = "q-$id",
        answer = "a-$id",
        difficulty = 5,
        questionCode = null,
        answerCode = null,
        questionSpoken = null,
        answerSpoken = null,
        extendedContext = null,
    )

    private val session: FastSessionState = reducer.seed((1..CARD_COUNT).map { flashcard("card-$it") })

    private fun playback(index: Int, phase: VoicePhase, isPlaying: Boolean = true) = VoicePlaybackState(
        isActive = true,
        isPlaying = isPlaying,
        currentIndex = index,
        totalCards = CARD_COUNT,
        phase = phase,
    )

    private fun FastSessionState.after(vararg inputs: FastSessionInput): FastSessionState =
        inputs.fold(this) { state, input -> reducer.reduce(state, input).state }

    @Test
    fun `a manual reveal shows the answer and marks the card Seen`() {
        val revealed = session.after(AnswerRevealed("card-1"))

        revealed.isAnswerRevealed shouldBe true
        revealed.seenCardIds shouldBe listOf("card-1")
    }

    @Test
    fun `the answer phase alone marks nothing Seen`() {
        val state = session.after(PlaybackChanged(playback(index = 1, phase = VoicePhase.Answer)))

        state.currentIndex shouldBe 1
        state.isAnswerRevealed shouldBe true
        state.seenCardIds.shouldBeEmpty()
    }

    @Test
    fun `a revealed answer the player already moved past still marks that card Seen`() {
        val movedOn = session.after(PlaybackChanged(playback(index = 1, phase = VoicePhase.Question)))

        val state = movedOn.after(AnswerRevealed("card-1"))

        state.seenCardIds shouldBe listOf("card-1")
        state.isAnswerRevealed shouldBe false
    }

    @Test
    fun `a revealed answer for a card outside the session marks nothing`() {
        session.after(AnswerRevealed("unknown")).seenCardIds.shouldBeEmpty()
    }

    @Test
    fun `a revisited card is not recorded twice, and first-seen order holds`() {
        val state = session.after(AnswerRevealed("card-2"), AnswerRevealed("card-1"), AnswerRevealed("card-2"))

        state.seenCardIds shouldBe listOf("card-2", "card-1")
    }

    @Test
    fun `manual next moves to the next card with the answer hidden`() {
        val transition = reducer.reduce(session.after(AnswerRevealed("card-1")), NextCardRequested)

        transition.state.currentIndex shouldBe 1
        transition.state.isAnswerRevealed shouldBe false
        transition.effects.shouldBeEmpty()
    }

    @Test
    fun `manual next before the answer shows is ignored`() {
        val transition = reducer.reduce(session, NextCardRequested)

        transition.state shouldBe session
        transition.effects.shouldBeEmpty()
    }

    @Test
    fun `manual next on the last card ends the session once its answer shows`() {
        val onLast = session.copy(currentIndex = CARD_COUNT - 1)

        reducer.reduce(onLast, NextCardRequested).effects.shouldBeEmpty()
        reducer.reduce(onLast.after(AnswerRevealed("card-$CARD_COUNT")), NextCardRequested).effects shouldBe
            listOf(FastSessionEffect.SessionComplete)
    }

    @Test
    fun `read-aloud next is available everywhere except the last card's answer`() {
        val lastIndex = CARD_COUNT - 1

        session.isReadAloudNextAvailable shouldBe true
        session.after(PlaybackChanged(playback(index = 0, phase = VoicePhase.Answer))).isReadAloudNextAvailable shouldBe true
        session.after(PlaybackChanged(playback(index = lastIndex, phase = VoicePhase.Question))).isReadAloudNextAvailable shouldBe true
        session.after(PlaybackChanged(playback(index = lastIndex, phase = VoicePhase.Answer))).isReadAloudNextAvailable shouldBe false
    }

    @Test
    fun `read-aloud ends when the player reports it played past the last card`() {
        val answered = session.after(PlaybackChanged(playback(index = CARD_COUNT - 1, phase = VoicePhase.Answer)))

        reducer.reduce(answered, PlaybackEndReached).effects shouldBe listOf(FastSessionEffect.SessionComplete)
    }

    @Test
    fun `a paused player back on the last card's question after its answer does not end the session`() {
        // A rewind, a speed change or an utterance error while paused all leave the player like this.
        val lastIndex = CARD_COUNT - 1
        val answered = session.after(PlaybackChanged(playback(index = lastIndex, phase = VoicePhase.Answer)))

        val transition = reducer.reduce(answered, PlaybackChanged(playback(index = lastIndex, phase = VoicePhase.Question, isPlaying = false)))

        transition.effects.shouldBeEmpty()
    }

    @Test
    fun `an unavailable engine pauses the session and stops the voice stack`() {
        val transition = reducer.reduce(session, PlaybackEngineUnavailable)

        transition.state.pauseReason shouldBe FastPauseReason.EngineUnavailable
        transition.effects shouldBe listOf(FastSessionEffect.StopVoiceStack, FastSessionEffect.Emit(FastSessionEvent.VoicePlaybackUnavailable))
        reducer.reduce(transition.state, PlaybackEngineUnavailable).effects.shouldBeEmpty()
    }

    @Test
    fun `play after an engine failure clears the pause and restarts the voice stack at the presented card`() {
        val paused = session.after(PlaybackChanged(playback(index = 1, phase = VoicePhase.Question)), PlaybackEngineUnavailable)

        val transition = reducer.reduce(paused, PlayRequested)

        transition.state.pauseReason shouldBe null
        transition.effects shouldBe listOf(FastSessionEffect.RestartVoiceStack(startIndex = 1))
    }

    // Pause and play

    @Test
    fun `a pause is a user pause and always reaches the player, and play clears it`() {
        val paused = reducer.reduce(playing, PauseRequested)

        paused.state.pauseReason shouldBe FastPauseReason.User
        paused.effects shouldBe listOf(Pause)
        reducer.reduce(paused.state.after(PlaybackChanged(playback(0, VoicePhase.Question, isPlaying = false))), PauseRequested).effects shouldBe listOf(Pause)

        val resumed = reducer.reduce(paused.state, PlayRequested)
        resumed.state.pauseReason shouldBe null
        resumed.effects shouldBe listOf(Play)
    }

    @Test
    fun `play on a playing session changes nothing`() {
        val transition = reducer.reduce(playing, PlayRequested)

        transition.state shouldBe playing
        transition.effects.shouldBeEmpty()
    }

    @Test
    fun `a temporary pause plays again when it ends, only while still paused by it`() {
        val paused = reducer.reduce(playing, TemporaryPauseRequested)
        paused.state.pauseReason shouldBe FastPauseReason.Temporary
        paused.effects shouldBe listOf(Pause)
        reducer.reduce(paused.state, TemporaryPauseEnded).effects shouldBe listOf(Play)

        val userPaused = paused.state.after(PauseRequested)
        userPaused.pauseReason shouldBe FastPauseReason.User
        reducer.reduce(userPaused, TemporaryPauseEnded).effects.shouldBeEmpty()
    }

    @Test
    fun `a temporary pause leaves a paused session alone`() {
        val userPaused = playing.after(PauseRequested)

        val transition = reducer.reduce(userPaused, TemporaryPauseRequested)

        transition.state shouldBe userPaused
        transition.effects.shouldBeEmpty()
    }

    @Test
    fun `the player starting to play ends a user pause`() {
        val paused = playing.after(PauseRequested, PlaybackChanged(playback(0, VoicePhase.Question, isPlaying = false)))

        paused.after(PlaybackChanged(playback(0, VoicePhase.Question))).pauseReason shouldBe null
    }

    // The advance hold

    @Test
    fun `a hold closes the advance gate once, and the gate stops the session held on the current card`() {
        val requested = reducer.reduce(answering, AdvanceHoldRequested)
        requested.effects shouldBe listOf(SetAdvanceGate(closed = true))
        reducer.reduce(requested.state, AdvanceHoldRequested).effects.shouldBeEmpty()

        val held = reducer.reduce(requested.state, AdvanceGateReached)

        held.state.isHeldAtAdvancePoint shouldBe true
        held.state.pauseReason shouldBe null
        held.effects.shouldBeEmpty()
    }

    @Test
    fun `releasing a held session opens the gate, moves on and plays`() {
        val transition = reducer.reduce(heldOnFirstCard, AdvanceHoldReleased)

        transition.state.isHeldAtAdvancePoint shouldBe false
        transition.state.isAdvanceHoldRequested shouldBe false
        transition.effects shouldBe listOf(SetAdvanceGate(closed = false), MoveToNextCard, Play)
    }

    @Test
    fun `releasing a hold that never stopped the session only opens the gate`() {
        val requested = answering.after(AdvanceHoldRequested)

        reducer.reduce(requested, AdvanceHoldReleased).effects shouldBe listOf(SetAdvanceGate(closed = false))
        reducer.reduce(session, AdvanceHoldReleased).effects.shouldBeEmpty()
    }

    @Test
    fun `releasing a hold on the last card ends the session`() {
        val heldOnLast = session.after(
            PlaybackChanged(playback(index = CARD_COUNT - 1, phase = VoicePhase.Answer)),
            AdvanceHoldRequested,
            AdvanceGateReached,
        )

        reducer.reduce(heldOnLast, AdvanceHoldReleased).effects shouldBe listOf(SetAdvanceGate(closed = false), SessionComplete)
    }

    @Test
    fun `a pause at a hold turns it into a user pause, and the release then only opens the gate`() {
        val paused = heldOnFirstCard.after(PauseRequested)

        paused.isHeldAtAdvancePoint shouldBe false
        paused.pauseReason shouldBe FastPauseReason.User
        reducer.reduce(paused, AdvanceHoldReleased).effects shouldBe listOf(SetAdvanceGate(closed = false))
        reducer.reduce(paused, PlayRequested).effects shouldBe listOf(MoveToNextCard, Play)
    }

    @Test
    fun `play or next at a hold moves on at once`() {
        reducer.reduce(heldOnFirstCard, PlayRequested).effects shouldBe listOf(MoveToNextCard, Play)
        reducer.reduce(heldOnFirstCard, NextRequested).effects shouldBe listOf(MoveToNextCard, Play)
    }

    @Test
    fun `previous at a hold restarts the card and plays`() {
        val transition = reducer.reduce(heldOnFirstCard, PreviousRequested(restartsCard = true))

        transition.state.isHeldAtAdvancePoint shouldBe false
        transition.effects shouldBe listOf(FastSessionEffect.RestartCurrentCard, Play)
    }

    @Test
    fun `previous card on the first card is ignored`() {
        val transition = reducer.reduce(playing, PreviousRequested(restartsCard = false))

        transition.state shouldBe playing
        transition.effects.shouldBeEmpty()
    }

    @Test
    fun `the gate reached after the hold was released moves on at once`() {
        val released = answering.after(AdvanceHoldRequested, AdvanceHoldReleased)

        reducer.reduce(released, AdvanceGateReached).effects shouldBe listOf(MoveToNextCard, Play)
    }

    @Test
    fun `the gate reached after a user pause stays paused at the advance point`() {
        val paused = answering.after(AdvanceHoldRequested, PauseRequested)

        val state = paused.after(AdvanceGateReached)

        state.isHeldAtAdvancePoint shouldBe false
        state.isPausedAtAdvancePoint shouldBe true
    }

    @Test
    fun `next at the last card's answer is ignored, even at a hold`() {
        val heldOnLast = session.after(
            PlaybackChanged(playback(index = CARD_COUNT - 1, phase = VoicePhase.Answer)),
            AdvanceHoldRequested,
            AdvanceGateReached,
        )

        val transition = reducer.reduce(heldOnLast, NextRequested)

        transition.state shouldBe heldOnLast
        transition.effects.shouldBeEmpty()
    }

    private val playing: FastSessionState
        get() = session.after(PlaybackChanged(playback(index = 0, phase = VoicePhase.Question)))

    private val answering: FastSessionState
        get() = session.after(PlaybackChanged(playback(index = 0, phase = VoicePhase.Answer)))

    private val heldOnFirstCard: FastSessionState
        get() = answering.after(AdvanceHoldRequested, AdvanceGateReached)

    private companion object {
        const val CARD_COUNT = 3
    }
}
