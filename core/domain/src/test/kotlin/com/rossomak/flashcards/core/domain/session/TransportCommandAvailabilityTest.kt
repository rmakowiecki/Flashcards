package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.FastSessionState
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.GradingFailureReason
import com.rossomak.flashcards.core.domain.model.RatedSessionState
import com.rossomak.flashcards.core.domain.model.TransportCommandType
import com.rossomak.flashcards.core.domain.model.TransportCommandType.JumpTo
import com.rossomak.flashcards.core.domain.model.TransportCommandType.Next
import com.rossomak.flashcards.core.domain.model.TransportCommandType.Pause
import com.rossomak.flashcards.core.domain.model.TransportCommandType.Play
import com.rossomak.flashcards.core.domain.model.TransportCommandType.Previous
import com.rossomak.flashcards.core.domain.model.TransportCommandType.PreviousCard
import com.rossomak.flashcards.core.domain.model.TransportCommandType.Stop
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGrade
import com.rossomak.flashcards.core.domain.model.VoiceCaptureFailureReason
import com.rossomak.flashcards.core.domain.model.VoicePlaybackState
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.AdvanceHoldRequested
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.CaptureFailed
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.Graded
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.GradingFailed
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.NoticeFinished
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.NoticeTailElapsed
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.PauseRequested
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.PlaybackChanged
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.PlaybackEngineUnavailable
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.QuestionFinished
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.SilenceTimedOut
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.SpeechEnded
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.SpeechStarted
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.UtteranceCaptured
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import org.junit.Test

class TransportCommandAvailabilityTest {

    private val reducer = RatedSessionReducer(Random(FIXED_SEED))

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

    private val cards = (1..CARD_COUNT).map { flashcard("card-$it") }

    private fun RatedSessionState.after(vararg inputs: RatedSessionInput): RatedSessionState =
        inputs.fold(this) { state, input -> reducer.reduce(state, input).state }

    private fun playing(isPlaying: Boolean) = PlaybackChanged(VoicePlaybackState(isActive = true, isPlaying = isPlaying))

    private val questionBeingRead: RatedSessionState =
        reducer.seed(cards = cards, attemptsLimit = 3, isVoiceAnsweringSession = true).after(playing(true))

    private val listening = questionBeingRead.after(QuestionFinished("card-1"))
    private val grading = listening.after(SpeechStarted, SpeechEnded, UtteranceCaptured(byteArrayOf(1)))
    private val feedback = grading.after(Graded(VoiceAnswerGrade(sanitizedTranscript = "answer", gradePercent = 90, feedback = "Right.")))

    private fun RatedSessionState.paused(): RatedSessionState = after(PauseRequested, playing(false))

    private fun RatedSessionState.noticesOver(): RatedSessionState {
        var state = this
        while (state.speakingNotices.isNotEmpty()) state = state.after(NoticeFinished(state.speakingNotices.first()))
        return state.after(NoticeTailElapsed)
    }

    // Rated

    @Test
    fun `the question being read offers everything but jumping and going back a card`() {
        questionBeingRead.availableTransportCommands shouldBe setOf(Pause, Stop, Next, Previous)
    }

    @Test
    fun `paused at the question, play replaces pause`() {
        questionBeingRead.paused().availableTransportCommands shouldBe setOf(Play, Next, Previous)
        listening.paused().availableTransportCommands shouldBe setOf(Play, Next, Previous)
    }

    @Test
    fun `listening, speech detected and grading offer pause only`() {
        listening.availableTransportCommands shouldBe setOf(Pause, Stop)
        listening.after(SpeechStarted).availableTransportCommands shouldBe setOf(Pause, Stop)
        grading.availableTransportCommands shouldBe setOf(Pause, Stop)
    }

    @Test
    fun `the grading feedback and its tail offer pause and next, never previous`() {
        feedback.availableTransportCommands shouldBe setOf(Pause, Stop, Next)
        feedback.after(NoticeFinished(feedback.speakingNotices.single())).availableTransportCommands shouldBe setOf(Pause, Stop, Next)
    }

    @Test
    fun `short notices offer pause only, even once paused`() {
        val silence = listening.after(SilenceTimedOut)
        silence.availableTransportCommands shouldBe setOf(Pause, Stop)
        silence.paused().availableTransportCommands shouldBe setOf(Pause, Stop)
        grading.after(GradingFailed(GradingFailureReason.NoConnection)).availableTransportCommands shouldBe setOf(Pause, Stop)
    }

    @Test
    fun `paused while grading offers play only`() {
        grading.paused().availableTransportCommands shouldBe setOf(Play)
    }

    @Test
    fun `paused after the feedback offers play and next`() {
        feedback.paused().availableTransportCommands shouldBe setOf(Play, Next)
        grading.paused().after(Graded(VoiceAnswerGrade(sanitizedTranscript = "answer", gradePercent = 90, feedback = "Right.")))
            .availableTransportCommands shouldBe setOf(Play, Next)
    }

    @Test
    fun `paused at the auto-advance point offers play and next`() {
        listening.after(SilenceTimedOut).paused().noticesOver().availableTransportCommands shouldBe setOf(Play, Next)
    }

    @Test
    fun `held at the auto-advance point offers play, pause and next`() {
        feedback.after(AdvanceHoldRequested).noticesOver().availableTransportCommands shouldBe setOf(Play, Pause, Stop, Next)
    }

    @Test
    fun `voice answering paused offers play only`() {
        questionBeingRead.after(CaptureFailed(VoiceCaptureFailureReason.BluetoothMicUnavailable), playing(false)).noticesOver()
            .availableTransportCommands shouldBe setOf(Play)
    }

    @Test
    fun `an unavailable engine offers play, which restarts it`() {
        questionBeingRead.after(PlaybackEngineUnavailable).availableTransportCommands shouldBe setOf(Play)
    }

    // Fast

    @Test
    fun `fast read-aloud offers every command until the last card's answer`() {
        val fast = FastSessionState(cards = cards, isReadAloudSession = true)
        fast.availableTransportCommands shouldBe TransportCommandType.entries.toSet()
        fast.copy(currentIndex = CARD_COUNT - 1, isAnswerRevealed = true).availableTransportCommands shouldBe
            setOf(Play, Pause, Previous, PreviousCard, JumpTo, Stop)
        fast.copy(currentIndex = CARD_COUNT - 1).availableTransportCommands shouldBe TransportCommandType.entries.toSet()
    }

    private companion object {
        const val FIXED_SEED = 42
        const val CARD_COUNT = 3
    }
}
