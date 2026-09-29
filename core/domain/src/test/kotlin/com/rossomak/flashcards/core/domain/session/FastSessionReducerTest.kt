package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.FastPauseReason
import com.rossomak.flashcards.core.domain.model.FastSessionState
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.ReadAloudStep
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

    private fun playback(isPlaying: Boolean = true) = VoicePlaybackState(isActive = true, isPlaying = isPlaying)

    private fun FastSessionState.after(vararg inputs: FastSessionInput): FastSessionState =
        inputs.fold(this) { state, input -> reducer.reduce(state, input).state }

    /** Read-aloud playing card [index]'s question. */
    private fun readingQuestion(index: Int): FastSessionState = session.copy(currentIndex = index).after(PlaybackChanged(playback()))

    private fun inQuestionPause(index: Int): FastSessionState = readingQuestion(index).after(QuestionFinished("card-${index + 1}"))

    private fun readingAnswer(index: Int): FastSessionState = inQuestionPause(index).after(QuestionPauseElapsed)

    private fun inAdvancePause(index: Int): FastSessionState = readingAnswer(index).after(AnswerFinished("card-${index + 1}"))

    @Test
    fun `a manual reveal shows the answer and marks the card Seen`() {
        val revealed = session.after(AnswerRevealed("card-1"))

        revealed.isAnswerRevealed shouldBe true
        revealed.seenCardIds shouldBe listOf("card-1")
    }

    @Test
    fun `a revealed answer the session already moved past still marks that card Seen`() {
        val movedOn = session.copy(currentIndex = 1)

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
        readingAnswer(0).isReadAloudNextAvailable shouldBe true
        readingQuestion(lastIndex).isReadAloudNextAvailable shouldBe true
        readingAnswer(lastIndex).isReadAloudNextAvailable shouldBe false
        inAdvancePause(lastIndex).isReadAloudNextAvailable shouldBe false
    }

    // The read-aloud loop

    @Test
    fun `a question read in full starts the question pause`() {
        val transition = reducer.reduce(readingQuestion(0), QuestionFinished("card-1"))

        transition.state.readAloudStep shouldBe ReadAloudStep.QuestionPause
        transition.effects shouldBe listOf(StartQuestionPause)
    }

    @Test
    fun `the end of the question pause presents the answer and reveals it`() {
        val transition = reducer.reduce(inQuestionPause(0), QuestionPauseElapsed)

        transition.state.readAloudStep shouldBe ReadAloudStep.Answer
        transition.state.isAnswerRevealed shouldBe true
        transition.effects shouldBe listOf(PresentAnswer(0))
    }

    @Test
    fun `an answer read in full starts the advance pause`() {
        val transition = reducer.reduce(readingAnswer(0), AnswerFinished("card-1"))

        transition.state.readAloudStep shouldBe ReadAloudStep.AdvancePause
        transition.effects shouldBe listOf(StartAdvancePause)
    }

    @Test
    fun `the end of the advance pause presents the next card's question`() {
        val transition = reducer.reduce(inAdvancePause(0), AdvancePauseElapsed)

        with(transition.state) {
            currentIndex shouldBe 1
            readAloudStep shouldBe ReadAloudStep.Question
            isAnswerRevealed shouldBe false
        }
        transition.effects shouldBe listOf(PresentQuestion(1))
    }

    @Test
    fun `the end of the advance pause after the last card ends the session`() {
        reducer.reduce(inAdvancePause(CARD_COUNT - 1), AdvancePauseElapsed).effects shouldBe listOf(SessionComplete)
    }

    @Test
    fun `a stale part report does nothing`() {
        val otherCard = reducer.reduce(readingQuestion(0), QuestionFinished("card-2"))
        val otherPart = reducer.reduce(readingQuestion(0), AnswerFinished("card-1"))
        val paused = reducer.reduce(readingQuestion(0).after(PauseRequested), QuestionFinished("card-1"))
        val staleElapsed = reducer.reduce(readingAnswer(0), QuestionPauseElapsed)

        listOf(otherCard, otherPart, paused, staleElapsed).forEach { transition -> transition.effects.shouldBeEmpty() }
        reducer.reduce(readingQuestion(0), AdvancePauseElapsed).state shouldBe readingQuestion(0)
    }

    @Test
    fun `an unavailable engine pauses the session and stops the voice stack`() {
        val transition = reducer.reduce(session, PlaybackEngineUnavailable)

        transition.state.pauseReason shouldBe FastPauseReason.EngineUnavailable
        transition.effects shouldBe listOf(FastSessionEffect.StopVoiceStack, FastSessionEffect.Emit(FastSessionEvent.VoicePlaybackUnavailable))
        reducer.reduce(transition.state, PlaybackEngineUnavailable).effects.shouldBeEmpty()
    }

    @Test
    fun `an unavailable engine in a pause stops the pause`() {
        reducer.reduce(inQuestionPause(0), PlaybackEngineUnavailable).effects.first() shouldBe CancelReadAloudPause
    }

    @Test
    fun `play after an engine failure clears the pause and restarts the voice stack at the presented card's question`() {
        val paused = readingAnswer(1).after(PlaybackEngineUnavailable)

        val transition = reducer.reduce(paused, PlayRequested)

        transition.state.pauseReason shouldBe null
        transition.state.readAloudStep shouldBe ReadAloudStep.Question
        transition.state.isAnswerRevealed shouldBe false
        transition.effects shouldBe listOf(FastSessionEffect.RestartVoiceStack(startIndex = 1))
    }

    // Pause and play

    @Test
    fun `a pause is a user pause and always reaches the player, and play clears it`() {
        val paused = reducer.reduce(playing, PauseRequested)

        paused.state.pauseReason shouldBe FastPauseReason.User
        paused.effects shouldBe listOf(Pause)
        reducer.reduce(paused.state.after(PlaybackChanged(playback(isPlaying = false))), PauseRequested).effects shouldBe listOf(Pause)

        val resumed = reducer.reduce(paused.state, PlayRequested)
        resumed.state.pauseReason shouldBe null
        resumed.effects shouldBe listOf(Play)
    }

    @Test
    fun `pause mid-part and play reads that part again`() {
        val paused = readingAnswer(0).after(PauseRequested, PlaybackChanged(playback(isPlaying = false)))

        val transition = reducer.reduce(paused, PlayRequested)

        transition.state.readAloudStep shouldBe ReadAloudStep.Answer
        transition.effects shouldBe listOf(Play)
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
        val paused = playing.after(PauseRequested, PlaybackChanged(playback(isPlaying = false)))

        paused.after(PlaybackChanged(playback())).pauseReason shouldBe null
    }

    // Resuming inside a read-aloud pause goes on to the next step

    @Test
    fun `a pause in a read-aloud pause stops it, and the step stays`() {
        val transition = reducer.reduce(inQuestionPause(0), PauseRequested)

        transition.state.readAloudStep shouldBe ReadAloudStep.QuestionPause
        transition.effects shouldBe listOf(CancelReadAloudPause, Pause)
        reducer.reduce(inAdvancePause(0), TemporaryPauseRequested).effects shouldBe listOf(CancelReadAloudPause, Pause)
    }

    @Test
    fun `play after a pause in the question pause presents the answer`() {
        val paused = inQuestionPause(0).after(PauseRequested, PlaybackChanged(playback(isPlaying = false)))

        val transition = reducer.reduce(paused, PlayRequested)

        transition.state.readAloudStep shouldBe ReadAloudStep.Answer
        transition.state.pauseReason shouldBe null
        transition.effects shouldBe listOf(PresentAnswer(0), Play)
    }

    @Test
    fun `play after a pause in the advance pause presents the next card`() {
        val paused = inAdvancePause(0).after(PauseRequested, PlaybackChanged(playback(isPlaying = false)))

        val transition = reducer.reduce(paused, PlayRequested)

        transition.state.currentIndex shouldBe 1
        transition.effects shouldBe listOf(PresentQuestion(1), Play)
    }

    @Test
    fun `play after a pause in the last card's advance pause ends the session`() {
        val paused = inAdvancePause(CARD_COUNT - 1).after(PauseRequested)

        reducer.reduce(paused, PlayRequested).effects shouldBe listOf(SessionComplete)
    }

    @Test
    fun `a temporary pause ending in a read-aloud pause goes on to the next step`() {
        reducer.reduce(inQuestionPause(0).after(TemporaryPauseRequested), TemporaryPauseEnded).effects shouldBe listOf(PresentAnswer(0), Play)
        reducer.reduce(inAdvancePause(0).after(TemporaryPauseRequested), TemporaryPauseEnded).effects shouldBe listOf(PresentQuestion(1), Play)
    }

    @Test
    fun `the player stopping by itself in a read-aloud pause stops the pause`() {
        val transition = reducer.reduce(inQuestionPause(0), PlaybackChanged(playback(isPlaying = false)))

        transition.state.readAloudStep shouldBe ReadAloudStep.QuestionPause
        transition.state.pauseReason shouldBe null
        transition.effects shouldBe listOf(CancelReadAloudPause)
    }

    @Test
    fun `the player playing again by itself in a read-aloud pause goes on to the next step, without a play`() {
        val lostFocusInQuestionPause = inQuestionPause(0).after(PlaybackChanged(playback(isPlaying = false)))
        val lostFocusInAdvancePause = inAdvancePause(0).after(PlaybackChanged(playback(isPlaying = false)))

        reducer.reduce(lostFocusInQuestionPause, PlaybackChanged(playback())).effects shouldBe listOf(PresentAnswer(0))
        reducer.reduce(lostFocusInAdvancePause, PlaybackChanged(playback())).effects shouldBe listOf(PresentQuestion(1))
    }

    @Test
    fun `the player playing again by itself mid-part does nothing more, it reads that part again by itself`() {
        val lostFocus = readingQuestion(0).after(PlaybackChanged(playback(isPlaying = false)))

        reducer.reduce(lostFocus, PlaybackChanged(playback())).effects.shouldBeEmpty()
    }

    @Test
    fun `the player's own playing report after a resume does not take a second step`() {
        val resumed = inQuestionPause(0).after(PauseRequested, PlaybackChanged(playback(isPlaying = false)), PlayRequested)

        val transition = reducer.reduce(resumed, PlaybackChanged(playback()))

        transition.state.readAloudStep shouldBe ReadAloudStep.Answer
        transition.effects.shouldBeEmpty()
    }

    // Next, previous and jump

    @Test
    fun `next at a question or in the question pause presents the answer`() {
        reducer.reduce(readingQuestion(0), NextRequested).effects shouldBe listOf(PresentAnswer(0))

        val fromPause = reducer.reduce(inQuestionPause(0), NextRequested)
        fromPause.state.readAloudStep shouldBe ReadAloudStep.Answer
        fromPause.effects shouldBe listOf(CancelReadAloudPause, PresentAnswer(0))
    }

    @Test
    fun `next at an answer or in the advance pause presents the next question`() {
        reducer.reduce(readingAnswer(0), NextRequested).effects shouldBe listOf(PresentQuestion(1))

        val fromPause = reducer.reduce(inAdvancePause(0), NextRequested)
        fromPause.state.currentIndex shouldBe 1
        fromPause.state.readAloudStep shouldBe ReadAloudStep.Question
        fromPause.effects shouldBe listOf(CancelReadAloudPause, PresentQuestion(1))
    }

    @Test
    fun `previous restarts or goes back from every step, stopping a running pause`() {
        reducer.reduce(inQuestionPause(1), PreviousRequested(restartsCard = true)).effects shouldBe listOf(CancelReadAloudPause, PresentQuestion(1))
        reducer.reduce(readingAnswer(1), PreviousRequested(restartsCard = true)).effects shouldBe listOf(PresentQuestion(1))

        val back = reducer.reduce(inAdvancePause(1), PreviousRequested(restartsCard = false))
        back.state.currentIndex shouldBe 0
        back.state.isAnswerRevealed shouldBe false
        back.effects shouldBe listOf(CancelReadAloudPause, PresentQuestion(0))
    }

    @Test
    fun `a jump presents the target card's question`() {
        val transition = reducer.reduce(inAdvancePause(0), JumpRequested(2))

        transition.state.currentIndex shouldBe 2
        transition.effects shouldBe listOf(CancelReadAloudPause, PresentQuestion(2))
        reducer.reduce(readingQuestion(1), JumpRequested(1)).effects.shouldBeEmpty()
    }

    @Test
    fun `previous card on the first card is ignored`() {
        val transition = reducer.reduce(playing, PreviousRequested(restartsCard = false))

        transition.state shouldBe playing
        transition.effects.shouldBeEmpty()
    }

    // The advance hold

    @Test
    fun `a hold stops the session at the advance point, held on the current card`() {
        val requested = reducer.reduce(answering, AdvanceHoldRequested)
        requested.effects.shouldBeEmpty()
        reducer.reduce(requested.state, AdvanceHoldRequested).state shouldBe requested.state

        val held = reducer.reduce(requested.state.after(AnswerFinished("card-1")), AdvancePauseElapsed)

        held.state.isHeldAtAdvancePoint shouldBe true
        held.state.pauseReason shouldBe null
        held.state.currentIndex shouldBe 0
        held.effects shouldBe listOf(Pause)
    }

    @Test
    fun `releasing a held session moves on and plays`() {
        val transition = reducer.reduce(heldOnFirstCard, AdvanceHoldReleased)

        transition.state.isHeldAtAdvancePoint shouldBe false
        transition.state.isAdvanceHoldRequested shouldBe false
        transition.effects shouldBe listOf(PresentQuestion(1), Play)
    }

    @Test
    fun `releasing a hold that never stopped the session only drops the request`() {
        val requested = answering.after(AdvanceHoldRequested)

        val transition = reducer.reduce(requested, AdvanceHoldReleased)
        transition.state.isAdvanceHoldRequested shouldBe false
        transition.effects.shouldBeEmpty()
        reducer.reduce(session, AdvanceHoldReleased).effects.shouldBeEmpty()
    }

    @Test
    fun `releasing a hold on the last card ends the session`() {
        val heldOnLast = readingAnswer(CARD_COUNT - 1).after(AdvanceHoldRequested, AnswerFinished("card-$CARD_COUNT"), AdvancePauseElapsed)

        reducer.reduce(heldOnLast, AdvanceHoldReleased).effects shouldBe listOf(SessionComplete)
    }

    @Test
    fun `a pause at a hold turns it into a user pause, and the release then does nothing`() {
        val paused = heldOnFirstCard.after(PauseRequested)

        paused.isHeldAtAdvancePoint shouldBe false
        paused.pauseReason shouldBe FastPauseReason.User
        reducer.reduce(paused, AdvanceHoldReleased).effects.shouldBeEmpty()
        reducer.reduce(paused, PlayRequested).effects shouldBe listOf(PresentQuestion(1), Play)
    }

    @Test
    fun `play or next at a hold moves on at once`() {
        reducer.reduce(heldOnFirstCard, PlayRequested).effects shouldBe listOf(PresentQuestion(1), Play)
        reducer.reduce(heldOnFirstCard, NextRequested).effects shouldBe listOf(PresentQuestion(1), Play)
    }

    @Test
    fun `previous at a hold restarts the card and plays`() {
        val transition = reducer.reduce(heldOnFirstCard, PreviousRequested(restartsCard = true))

        transition.state.isHeldAtAdvancePoint shouldBe false
        transition.effects shouldBe listOf(CancelReadAloudPause, PresentQuestion(0), Play)
    }

    @Test
    fun `the advance point reached after the hold was released moves on at once`() {
        val released = answering.after(AdvanceHoldRequested, AdvanceHoldReleased, AnswerFinished("card-1"))

        reducer.reduce(released, AdvancePauseElapsed).effects shouldBe listOf(PresentQuestion(1))
    }

    @Test
    fun `next at the last card's answer is ignored, even at a hold`() {
        val heldOnLast = readingAnswer(CARD_COUNT - 1).after(AdvanceHoldRequested, AnswerFinished("card-$CARD_COUNT"), AdvancePauseElapsed)

        val transition = reducer.reduce(heldOnLast, NextRequested)

        transition.state shouldBe heldOnLast
        transition.effects.shouldBeEmpty()
    }

    private val playing: FastSessionState
        get() = readingQuestion(0)

    private val answering: FastSessionState
        get() = readingAnswer(0)

    private val heldOnFirstCard: FastSessionState
        get() = answering.after(AdvanceHoldRequested, AnswerFinished("card-1"), AdvancePauseElapsed)

    private companion object {
        const val CARD_COUNT = 3
    }
}
