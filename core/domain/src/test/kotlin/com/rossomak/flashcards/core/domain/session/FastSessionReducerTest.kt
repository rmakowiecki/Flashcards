package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.FastSessionState
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.SessionPauseReason
import com.rossomak.flashcards.core.domain.model.VoicePhase
import com.rossomak.flashcards.core.domain.model.VoicePlaybackState
import com.rossomak.flashcards.core.domain.session.FastSessionInput.AnswerRevealed
import com.rossomak.flashcards.core.domain.session.FastSessionInput.NextCardRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PlaybackChanged
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PlaybackEndReached
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PlaybackEngineUnavailable
import com.rossomak.flashcards.core.domain.session.FastSessionInput.VoiceStackRestarted
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
        val revealed = session.after(AnswerRevealed)

        revealed.isAnswerRevealed shouldBe true
        revealed.seenCardIds shouldBe listOf("card-1")
    }

    @Test
    fun `the answer phase for the current index marks that card Seen`() {
        val state = session.after(PlaybackChanged(playback(index = 1, phase = VoicePhase.Answer)))

        state.currentIndex shouldBe 1
        state.isAnswerRevealed shouldBe true
        state.seenCardIds shouldBe listOf("card-2")
    }

    @Test
    fun `a revisited card is not recorded twice, and first-seen order holds`() {
        val state = session.after(
            PlaybackChanged(playback(index = 1, phase = VoicePhase.Answer)),
            PlaybackChanged(playback(index = 0, phase = VoicePhase.Answer)),
            PlaybackChanged(playback(index = 1, phase = VoicePhase.Answer)),
        )

        state.seenCardIds shouldBe listOf("card-2", "card-1")
    }

    @Test
    fun `manual next moves to the next card with the answer hidden`() {
        val transition = reducer.reduce(session.after(AnswerRevealed), NextCardRequested)

        transition.state.currentIndex shouldBe 1
        transition.state.isAnswerRevealed shouldBe false
        transition.effects.shouldBeEmpty()
    }

    @Test
    fun `manual next on the last card ends the session`() {
        val onLast = session.copy(currentIndex = CARD_COUNT - 1)

        reducer.reduce(onLast, NextCardRequested).effects shouldBe listOf(FastSessionEffect.SessionComplete)
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

        transition.state.pauseReason shouldBe SessionPauseReason.VoiceEngineUnavailable
        transition.effects shouldBe listOf(FastSessionEffect.StopVoiceStack, FastSessionEffect.Emit(FastSessionEvent.VoicePlaybackUnavailable))
        reducer.reduce(transition.state, PlaybackEngineUnavailable).effects.shouldBeEmpty()
    }

    @Test
    fun `a restarted voice stack clears the pause`() {
        session.after(PlaybackEngineUnavailable, VoiceStackRestarted).pauseReason shouldBe null
    }

    private companion object {
        const val CARD_COUNT = 3
    }
}
