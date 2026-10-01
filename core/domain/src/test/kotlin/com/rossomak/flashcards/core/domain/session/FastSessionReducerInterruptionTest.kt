package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.AudioModeChanged
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.FocusChanged
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.MicSilenced
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.OutputDisconnected
import com.rossomak.flashcards.core.domain.model.AudioMode
import com.rossomak.flashcards.core.domain.model.FastPauseReason
import com.rossomak.flashcards.core.domain.model.FastSessionState
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FocusChange
import com.rossomak.flashcards.core.domain.model.InterruptionEpisode
import com.rossomak.flashcards.core.domain.model.InterruptionTier
import com.rossomak.flashcards.core.domain.model.ReadAloudStep
import com.rossomak.flashcards.core.domain.model.VoicePlaybackState
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.CancelReadAloudPause
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.Emit
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.PausePlayback
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.Play
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.PresentAnswer
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.PresentQuestion
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.SessionComplete
import com.rossomak.flashcards.core.domain.session.FastSessionEffect.StartBlipTimer
import com.rossomak.flashcards.core.domain.session.FastSessionInput.AdvancePauseElapsed
import com.rossomak.flashcards.core.domain.session.FastSessionInput.AnswerFinished
import com.rossomak.flashcards.core.domain.session.FastSessionInput.AudioEnvironmentChanged
import com.rossomak.flashcards.core.domain.session.FastSessionInput.BlipElapsed
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PauseRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PlayRequested
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PlaybackChanged
import com.rossomak.flashcards.core.domain.session.FastSessionInput.PlaybackEngineUnavailable
import com.rossomak.flashcards.core.domain.session.FastSessionInput.QuestionFinished
import com.rossomak.flashcards.core.domain.session.FastSessionInput.QuestionPauseElapsed
import com.rossomak.flashcards.core.domain.session.FastSessionInput.TemporaryPauseEnded
import com.rossomak.flashcards.core.domain.session.FastSessionInput.TemporaryPauseRequested
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import org.junit.Test

/** What audio interruptions do to Fast read-aloud, tier by tier and step by step. */
class FastSessionReducerInterruptionTest {
    private val reducer = FastSessionReducer(mockk(relaxed = true))
    private val timeSource = TestTimeSource()

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

    private fun cardId(number: Int): String = "card-$number"

    private val session: FastSessionState = reducer.seed((1..CARD_COUNT).map { flashcard(cardId(it)) }, isReadAloudSession = true)

    private fun playback(isPlaying: Boolean) = PlaybackChanged(VoicePlaybackState(isActive = true, isPlaying = isPlaying))

    private fun FastSessionState.after(vararg inputs: FastSessionInput): FastSessionState =
        inputs.fold(this) { state, input -> reducer.reduce(state, input).state }

    private fun FastSessionState.reduce(input: FastSessionInput) = reducer.reduce(this, input)

    private fun signal(signal: AudioEnvironmentSignal, after: Duration = Duration.ZERO): AudioEnvironmentChanged {
        timeSource += after
        return AudioEnvironmentChanged(signal, timeSource.markNow())
    }

    private fun focus(change: FocusChange, after: Duration = Duration.ZERO) = signal(FocusChanged(change), after)

    private fun readingQuestion(index: Int = 0): FastSessionState = session.copy(currentIndex = index).after(playback(true))

    private fun inQuestionPause(index: Int = 0): FastSessionState = readingQuestion(index).after(QuestionFinished(cardId(index + 1)))

    private fun readingAnswer(index: Int = 0): FastSessionState = inQuestionPause(index).after(QuestionPauseElapsed)

    private fun inAdvancePause(index: Int = 0): FastSessionState = readingAnswer(index).after(AnswerFinished(cardId(index + 1)))

    // Blips

    @Test
    fun `a blip keeps every step going and starts the blip timer`() {
        listOf(readingQuestion(), inQuestionPause(), readingAnswer(), inAdvancePause()).forEach { state ->
            val transition = state.reduce(focus(FocusChange.LossCanDuck))

            transition.effects shouldNotContain PausePlayback
            transition.effects shouldContain StartBlipTimer(InterruptionEpisode.BLIP_SPEECH_THRESHOLD)
            transition.state.pauseReason shouldBe null
        }
    }

    @Test
    fun `a blip that outlasts the threshold while a part is read becomes an interruption`() {
        listOf(readingQuestion(), readingAnswer()).forEach { state ->
            val blip = state.after(focus(FocusChange.LossCanDuck))

            val transition = blip.reduce(BlipElapsed)

            transition.effects shouldContain PausePlayback
            transition.state.pauseReason shouldBe FastPauseReason.User
            transition.state.episode.tier shouldBe InterruptionTier.Interruption
        }
    }

    @Test
    fun `a blip that outlasts the threshold during a pause waits, and the next part is presented paused`() {
        val blip = inQuestionPause().after(focus(FocusChange.LossCanDuck), BlipElapsed)
        blip.pauseReason shouldBe null

        val transition = blip.reduce(QuestionPauseElapsed)

        transition.effects shouldBe listOf(
            FastSessionEffect.CancelBlipTimer,
            PausePlayback,
            PresentAnswer(0),
        )
        transition.effects shouldNotContain Play
        transition.state.pauseReason shouldBe FastPauseReason.User
    }

    // Interruptions

    @Test
    fun `an interruption while a question is read stops it, and the auto-resume reads it again`() {
        val interruption = readingQuestion().reduce(focus(FocusChange.LossTransient))

        interruption.effects shouldContain PausePlayback
        interruption.state.pauseReason shouldBe FastPauseReason.User

        val resumed = interruption.state.reduce(focus(FocusChange.Gain, after = 10.seconds))

        resumed.effects shouldContain Play
        resumed.state.pauseReason shouldBe null
        resumed.state.readAloudStep shouldBe ReadAloudStep.Question
    }

    @Test
    fun `an interruption while an answer is read resumes by reading that answer again`() {
        val interruption = readingAnswer().after(focus(FocusChange.LossTransient))

        val resumed = interruption.reduce(focus(FocusChange.Gain, after = 10.seconds))

        resumed.effects shouldContain Play
        resumed.state.readAloudStep shouldBe ReadAloudStep.Answer
    }

    @Test
    fun `an interruption in the question pause cancels the pause, and the resume presents the answer`() {
        val interruption = inQuestionPause().reduce(focus(FocusChange.LossTransient))

        interruption.effects shouldContain CancelReadAloudPause
        interruption.effects shouldContain PausePlayback

        val resumed = interruption.state.reduce(focus(FocusChange.Gain, after = 5.seconds))

        resumed.effects.filter { it == PresentAnswer(0) || it == Play } shouldBe listOf(PresentAnswer(0), Play)
    }

    @Test
    fun `an interruption in the advance pause cancels the pause, and the resume goes on to the next card`() {
        val interruption = inAdvancePause().reduce(focus(FocusChange.LossTransient))

        interruption.effects shouldContain CancelReadAloudPause

        val resumed = interruption.state.reduce(focus(FocusChange.Gain, after = 5.seconds))

        resumed.effects.filter { it == PresentQuestion(1) || it == Play } shouldBe listOf(PresentQuestion(1), Play)
    }

    @Test
    fun `an interruption in the last card's advance pause resumes into the end of the session`() {
        val interruption = inAdvancePause(CARD_COUNT - 1).after(focus(FocusChange.LossTransient))

        interruption.reduce(focus(FocusChange.Gain, after = 5.seconds)).effects shouldContain SessionComplete
    }

    @Test
    fun `a gain at 59 seconds resumes and at 61 seconds does not`() {
        val interruption = readingQuestion().after(focus(FocusChange.LossTransient))

        interruption.reduce(focus(FocusChange.Gain, after = 59.seconds)).effects shouldContain Play
        interruption.reduce(focus(FocusChange.Gain, after = 61.seconds)).effects shouldNotContain Play
    }

    @Test
    fun `a gain with no episode does nothing`() {
        readingQuestion().reduce(focus(FocusChange.Gain)).effects.shouldBeEmpty()
    }

    @Test
    fun `a user pause during the episode cancels the auto-resume`() {
        val interruption = readingQuestion().after(focus(FocusChange.LossTransient), PauseRequested)

        interruption.reduce(focus(FocusChange.Gain, after = 5.seconds)).effects shouldNotContain Play
    }

    @Test
    fun `an output disconnect pauses like a user and never resumes, at the gain or later`() {
        val interruption = readingAnswer().after(focus(FocusChange.LossTransient))

        val disconnected = interruption.reduce(signal(OutputDisconnected))
        val gain = disconnected.state.reduce(focus(FocusChange.Gain, after = 5.seconds))

        disconnected.effects shouldContain PausePlayback
        gain.effects shouldNotContain Play
        gain.state.pauseReason shouldBe FastPauseReason.User
    }

    @Test
    fun `an output disconnect with no episode is a user pause`() {
        val transition = readingQuestion().reduce(signal(OutputDisconnected))

        transition.state.pauseReason shouldBe FastPauseReason.User
        transition.effects shouldContain PausePlayback
    }

    @Test
    fun `a microphone signal changes nothing in read-aloud`() {
        readingQuestion().reduce(signal(MicSilenced)).effects.shouldBeEmpty()
    }

    // Dialogs

    @Test
    fun `an interruption that starts during a dialog is not resumed by its gain, the dialog closing does`() {
        val dialog = readingQuestion().after(TemporaryPauseRequested, playback(false))

        val interruption = dialog.reduce(focus(FocusChange.LossTransient))

        interruption.effects shouldNotContain PausePlayback
        interruption.state.reduce(focus(FocusChange.Gain, after = 5.seconds)).effects shouldNotContain Play
        interruption.state.reduce(TemporaryPauseEnded).effects shouldContain Play
    }

    @Test
    fun `a dialog opened during an interruption takes over the resume`() {
        val interruption = readingQuestion().after(focus(FocusChange.LossTransient), playback(false))

        val dialog = interruption.reduce(TemporaryPauseRequested)

        dialog.state.pauseReason shouldBe FastPauseReason.Temporary
        dialog.state.reduce(focus(FocusChange.Gain, after = 5.seconds)).effects shouldNotContain Play
        dialog.state.reduce(TemporaryPauseEnded).effects shouldContain Play
    }

    @Test
    fun `a call that starts during a dialog turns the temporary pause into a user pause`() {
        val dialog = readingQuestion().after(TemporaryPauseRequested, playback(false))

        val call = dialog.after(signal(AudioModeChanged(AudioMode.InCall)), focus(FocusChange.LossTransient))

        call.pauseReason shouldBe FastPauseReason.User
        call.after(signal(AudioModeChanged(AudioMode.Normal))).reduce(TemporaryPauseEnded).effects shouldNotContain Play
    }

    // Calls and takeovers

    @Test
    fun `a picked-up call never auto-resumes, whatever its length`() {
        val call = readingQuestion().after(focus(FocusChange.LossTransient), signal(AudioModeChanged(AudioMode.InCall), after = 1.seconds))

        val transition = call.after(signal(AudioModeChanged(AudioMode.Normal), after = 2.seconds))
            .reduce(focus(FocusChange.Gain, after = 1.seconds))

        transition.effects shouldNotContain Play
        transition.state.pauseReason shouldBe FastPauseReason.User
    }

    @Test
    fun `a declined ring resumes within the window`() {
        val ringing = readingQuestion().after(signal(AudioModeChanged(AudioMode.Ringtone)), focus(FocusChange.LossTransient))

        val transition = ringing.after(signal(AudioModeChanged(AudioMode.Normal), after = 4.seconds))
            .reduce(focus(FocusChange.Gain))

        transition.effects shouldContain Play
    }

    @Test
    fun `a takeover pauses and stays paused until the user plays`() {
        val takeover = readingQuestion().reduce(focus(FocusChange.Loss))

        takeover.effects shouldContain PausePlayback
        takeover.state.reduce(focus(FocusChange.Gain, after = 5.seconds)).effects shouldNotContain Play

        val played = takeover.state.after(playback(false)).reduce(PlayRequested)
        played.effects shouldContain Play
        played.state.episode.isActive shouldBe false
    }

    @Test
    fun `a play during a plain transient loss wins over the hold`() {
        val interruption = readingQuestion().after(focus(FocusChange.LossTransient), playback(false))

        val played = interruption.reduce(PlayRequested)

        played.effects shouldContain Play
        played.state.reduce(focus(FocusChange.Gain, after = 5.seconds)).effects shouldNotContain Play
    }

    // The call block

    @Test
    fun `a call that starts reports it once, however the call then moves on`() {
        val ringing = readingQuestion().reduce(signal(AudioModeChanged(AudioMode.Ringtone)))
        val pickedUp = ringing.state.reduce(signal(AudioModeChanged(AudioMode.InCall)))
        val ended = pickedUp.state.reduce(signal(AudioModeChanged(AudioMode.Normal)))
        val ringingAgain = ended.state.reduce(signal(AudioModeChanged(AudioMode.Ringtone)))

        ringing.effects shouldContain Emit(FastSessionEvent.PlayIgnoredDuringCall)
        pickedUp.effects shouldNotContain Emit(FastSessionEvent.PlayIgnoredDuringCall)
        ended.effects shouldNotContain Emit(FastSessionEvent.PlayIgnoredDuringCall)
        ringingAgain.effects shouldContain Emit(FastSessionEvent.PlayIgnoredDuringCall)
    }

    @Test
    fun `a player that starts reading under a ringing call is paused, and stays paused when the call ends`() {
        val ringing = session.after(signal(AudioModeChanged(AudioMode.Ringtone)))

        val started = ringing.reduce(playback(true))
        val ended = started.state.after(playback(false)).reduce(signal(AudioModeChanged(AudioMode.Normal)))

        started.effects shouldContain PausePlayback
        ended.effects shouldNotContain Play
    }

    @Test
    fun `a player that starts reading with no call is left alone`() {
        session.reduce(playback(true)).effects shouldNotContain PausePlayback
    }

    @Test
    fun `a play after an engine failure starts no voice stack while a call rings`() {
        val failed = readingQuestion().after(PlaybackEngineUnavailable, signal(AudioModeChanged(AudioMode.Ringtone)))

        val transition = failed.reduce(PlayRequested)

        transition.effects shouldBe listOf(Emit(FastSessionEvent.PlayIgnoredDuringCall))
        transition.state shouldBe failed
    }

    @Test
    fun `a play at every call mode starts nothing and reports it`() {
        listOf(AudioMode.Ringtone, AudioMode.InCall, AudioMode.InCommunication).forEach { mode ->
            val paused = readingQuestion().after(signal(AudioModeChanged(mode)), focus(FocusChange.LossTransient), playback(false))

            val transition = paused.reduce(PlayRequested)

            transition.effects shouldBe listOf(Emit(FastSessionEvent.PlayIgnoredDuringCall))
            transition.state shouldBe paused
        }
    }

    @Test
    fun `a play on a session that already plays reports nothing during a call`() {
        readingQuestion().after(signal(AudioModeChanged(AudioMode.Ringtone))).reduce(PlayRequested).effects.shouldBeEmpty()
    }

    @Test
    fun `the end of a temporary pause during a call ends the pause and plays nothing`() {
        val dialog = readingQuestion().after(TemporaryPauseRequested, playback(false), signal(AudioModeChanged(AudioMode.InCall)))

        val transition = dialog.reduce(TemporaryPauseEnded)

        transition.state.pauseReason shouldBe null
        transition.effects shouldNotContain Play
    }

    @Test
    fun `the release of a hold during a call moves on without playing`() {
        val held = readingAnswer().after(
            FastSessionInput.AdvanceHoldRequested,
            AnswerFinished(cardId(1)),
            AdvancePauseElapsed,
            signal(AudioModeChanged(AudioMode.InCall)),
            FastSessionInput.AdvanceHoldReleased,
        )

        val transition = held.reduce(FastSessionInput.ReleaseLingerElapsed)

        transition.effects shouldContain PresentQuestion(1)
        transition.effects shouldNotContain Play
    }

    @Test
    fun `a play works again once the call ended`() {
        val ended = readingQuestion().after(
            signal(AudioModeChanged(AudioMode.InCall)),
            focus(FocusChange.LossTransient),
            playback(false),
            signal(AudioModeChanged(AudioMode.Normal)),
            focus(FocusChange.Gain, after = 5.seconds),
        )

        ended.reduce(PlayRequested).effects shouldContain Play
    }

    private companion object {
        const val CARD_COUNT = 3
    }
}
