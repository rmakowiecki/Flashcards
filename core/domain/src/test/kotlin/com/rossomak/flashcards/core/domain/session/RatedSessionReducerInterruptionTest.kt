package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.AudioModeChanged
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.FocusChanged
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.MicSilenced
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.MicUnsilenced
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.OutputDisconnected
import com.rossomak.flashcards.core.domain.model.AudioMode
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.model.FocusChange
import com.rossomak.flashcards.core.domain.model.InterruptionEpisode
import com.rossomak.flashcards.core.domain.model.InterruptionTier
import com.rossomak.flashcards.core.domain.model.RatedSessionState
import com.rossomak.flashcards.core.domain.model.SpokenNotice
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGrade
import com.rossomak.flashcards.core.domain.model.VoiceAnswerPauseReason
import com.rossomak.flashcards.core.domain.model.VoiceAnswerPhase
import com.rossomak.flashcards.core.domain.model.VoiceAnswerRound
import com.rossomak.flashcards.core.domain.model.VoicePlaybackState
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.CancelSilenceTimer
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.Emit
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.PausePlayback
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.Play
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.PlayListeningCue
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.PresentHeadQuestion
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.ResumeWithoutReading
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.SetCaptureGate
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.SpeakNotice
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.StartBlipTimer
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.StartGateTail
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.StartSilenceTimer
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.StopFeedback
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.StopListening
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.AudioEnvironmentChanged
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.BlipElapsed
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.GateTailElapsed
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.Graded
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.MicrophoneOpened
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.NoticeFinished
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.NoticeTailElapsed
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.PauseRequested
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.PlayRequested
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.PlaybackChanged
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.QuestionFinished
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.ReleaseLingerElapsed
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.ResumeRequested
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.SilenceTimedOut
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.SpeechEnded
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.SpeechStarted
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.TemporaryPauseEnded
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.TemporaryPauseRequested
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.TranscriptReady
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.UtteranceCaptured
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import org.junit.Test

/** What audio interruptions do to a Rated voice round, tier by tier and phase by phase. */
class RatedSessionReducerInterruptionTest {
    private val reducer = RatedSessionReducer(Random(FIXED_SEED), mockk(relaxed = true))
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

    private fun playing(isPlaying: Boolean) = PlaybackChanged(VoicePlaybackState(isActive = true, isPlaying = isPlaying))

    /** A voice session reading the first question. */
    private fun voiceSession(): RatedSessionState = reducer.seed(
        cards = (1..CARD_COUNT).map { flashcard("card-$it") },
        attemptsLimit = 3,
        isVoiceAnsweringSession = true,
    ).let { reducer.reduce(it, playing(true)).state }

    /** A voice session the player has not started reading yet. */
    private fun unstartedVoiceSession(): RatedSessionState = reducer.seed(
        cards = (1..CARD_COUNT).map { flashcard("card-$it") },
        attemptsLimit = 3,
        isVoiceAnsweringSession = true,
    )

    private fun RatedSessionState.after(vararg inputs: RatedSessionInput): RatedSessionState =
        inputs.fold(this) { state, input -> reducer.reduce(state, input).state }

    private fun RatedSessionState.reduce(input: RatedSessionInput) = reducer.reduce(this, input)

    private fun signal(signal: AudioEnvironmentSignal, after: Duration = Duration.ZERO): AudioEnvironmentChanged {
        timeSource += after
        return AudioEnvironmentChanged(signal, timeSource.markNow())
    }

    private fun focus(change: FocusChange, after: Duration = Duration.ZERO) = signal(FocusChanged(change), after)

    private fun RatedSessionState.head(): String = requireNotNull(currentCard).id

    private fun RatedSessionState.listening(): RatedSessionState = after(QuestionFinished(head()), MicrophoneOpened)

    private fun RatedSessionState.grading(): RatedSessionState = listening().after(SpeechStarted, SpeechEnded, UtteranceCaptured(WAV))

    private fun RatedSessionState.speakingFeedback(): RatedSessionState = grading().after(TranscriptReady(TRANSCRIPT), Graded(grade()))

    private fun RatedSessionState.silenceSkipNotice(): RatedSessionState = listening().after(SilenceTimedOut)

    private fun grade() = VoiceAnswerGrade(sanitizedTranscript = TRANSCRIPT, gradePercent = CORRECT_PERCENT, feedback = RATIONALE)

    // Blips

    @Test
    fun `a blip while the question is read keeps speaking and starts the blip timer`() {
        val transition = voiceSession().reduce(focus(FocusChange.LossCanDuck))

        transition.effects shouldNotContain PausePlayback
        transition.effects shouldContain StartBlipTimer(InterruptionEpisode.BLIP_SPEECH_THRESHOLD)
        transition.state.episode.tier shouldBe InterruptionTier.Blip
    }

    @Test
    fun `a blip that outlasts the threshold while the question is read becomes an interruption`() {
        val blip = voiceSession().after(focus(FocusChange.LossCanDuck))

        val transition = blip.reduce(BlipElapsed)

        transition.effects shouldContain PausePlayback
        transition.state.episode.tier shouldBe InterruptionTier.Interruption
        transition.state.isPlaying shouldBe false
    }

    @Test
    fun `an escalated blip follows the auto-resume window from its first loss`() {
        val escalated = voiceSession().after(focus(FocusChange.LossCanDuck), BlipElapsed)

        escalated.reduce(focus(FocusChange.Gain, after = 30.seconds)).effects shouldContain Play
        escalated.reduce(focus(FocusChange.Gain, after = 61.seconds)).effects shouldNotContain Play
    }

    @Test
    fun `a blip that ends before the threshold changes nothing and cancels its timer`() {
        val blip = voiceSession().after(focus(FocusChange.LossCanDuck))

        val transition = blip.reduce(focus(FocusChange.Gain, after = 700.milliseconds))

        transition.effects shouldContain RatedSessionEffect.CancelBlipTimer
        transition.effects shouldNotContain PausePlayback
        transition.effects shouldNotContain Play
        transition.state.episode.isActive shouldBe false
    }

    @Test
    fun `a blip timer that fires after the blip ended does nothing`() {
        val ended = voiceSession().after(focus(FocusChange.LossCanDuck), focus(FocusChange.Gain))

        val transition = ended.reduce(BlipElapsed)

        transition.effects.shouldBeEmpty()
    }

    // Interruptions in each phase

    @Test
    fun `an interruption while the question is read stops it, and the auto-resume reads it again`() {
        val interruption = voiceSession().reduce(focus(FocusChange.LossTransient))

        interruption.effects shouldContain PausePlayback
        interruption.state.episode.isHolding shouldBe true

        val resumed = interruption.state.reduce(focus(FocusChange.Gain, after = 10.seconds))

        resumed.effects shouldContain Play
        resumed.state.episode.isActive shouldBe false
    }

    @Test
    fun `an interruption that ends after the window resumes nothing`() {
        val interruption = voiceSession().after(focus(FocusChange.LossTransient))

        val transition = interruption.reduce(focus(FocusChange.Gain, after = 61.seconds))

        transition.effects shouldNotContain Play
    }

    @Test
    fun `an interruption that ends at 59 seconds resumes`() {
        val interruption = voiceSession().after(focus(FocusChange.LossTransient))

        interruption.reduce(focus(FocusChange.Gain, after = 59.seconds)).effects shouldContain Play
    }

    @Test
    fun `an interruption while listening cancels the round without a count, a notice or an Attempt`() {
        val listening = voiceSession().listening()

        val transition = listening.reduce(focus(FocusChange.LossTransient))

        transition.effects shouldContain StopListening
        transition.effects shouldContain PausePlayback
        transition.state.round.phase shouldBe VoiceAnswerPhase.WaitingForQuestion
        transition.state.consecutiveSilenceCount shouldBe 0
        transition.state.speakingNotices.shouldBeEmpty()
        transition.state.currentCardRatings.shouldBeEmpty()
    }

    @Test
    fun `an interruption while listening resumes by reading the same card's question again`() {
        val cancelled = voiceSession().listening().after(focus(FocusChange.LossTransient))

        val transition = cancelled.reduce(focus(FocusChange.Gain, after = 5.seconds))

        transition.effects shouldContain Play
        transition.state.head() shouldBe "card-1"
    }

    @Test
    fun `an interruption while an answer is spoken cancels the round the same way`() {
        val speaking = voiceSession().listening().after(SpeechStarted)

        val transition = speaking.reduce(focus(FocusChange.LossTransient))

        transition.effects shouldContain StopListening
        transition.state.round.phase shouldBe VoiceAnswerPhase.WaitingForQuestion
    }

    @Test
    fun `an interruption while grading pauses the grading, records the grade and holds its feedback`() {
        val grading = voiceSession().grading()

        val interruption = grading.reduce(focus(FocusChange.LossTransient))

        interruption.state.isPausedWhileGrading shouldBe true

        val graded = interruption.state.reduce(Graded(grade()))

        graded.state.isPausedAfterFeedback shouldBe true
        graded.effects.filterIsInstance<SpeakNotice>().shouldBeEmpty()
        graded.state.currentCardRatings.size shouldBe 1
    }

    @Test
    fun `the auto-resume after an interruption while grading goes back to waiting for the grade`() {
        val paused = voiceSession().grading().after(focus(FocusChange.LossTransient))

        val transition = paused.reduce(focus(FocusChange.Gain, after = 5.seconds))

        transition.effects shouldContain ResumeWithoutReading
        transition.state.isPausedWhileGrading shouldBe false
    }

    @Test
    fun `an interruption while the feedback is spoken cuts it, and the auto-resume reads it again from the start`() {
        val feedback = voiceSession().speakingFeedback()

        val interruption = feedback.reduce(focus(FocusChange.LossTransient))

        interruption.effects shouldContain StopFeedback
        interruption.effects shouldContain PausePlayback
        interruption.state.isPausedAfterFeedback shouldBe true

        val resumed = interruption.state.reduce(focus(FocusChange.Gain, after = 5.seconds))

        resumed.effects.filterIsInstance<SpeakNotice>().single().notice.shouldBeFeedback()
        resumed.state.isPausedAfterFeedback shouldBe false
    }

    @Test
    fun `a blip while the feedback is spoken keeps it speaking until it outlasts the threshold`() {
        val feedback = voiceSession().speakingFeedback()

        val blip = feedback.reduce(focus(FocusChange.LossCanDuck))
        blip.effects shouldNotContain StopFeedback

        val escalated = blip.state.reduce(BlipElapsed)
        escalated.effects shouldContain StopFeedback
        escalated.state.isPausedAfterFeedback shouldBe true
    }

    @Test
    fun `a short notice is never cut, and the session holds at the advance point afterwards`() {
        val notice = voiceSession().silenceSkipNotice()
        val noticeSpeaking = notice.speakingNotices.single()

        val interruption = notice.reduce(focus(FocusChange.LossTransient))

        interruption.effects shouldNotContain StopFeedback
        interruption.state.speakingNotices shouldBe listOf(noticeSpeaking)

        val tailStarted = interruption.state.after(NoticeFinished(noticeSpeaking), NoticeTailElapsed)
        tailStarted.isPausedAtAdvancePoint shouldBe true
    }

    @Test
    fun `the auto-resume at the advance point reads the next question`() {
        val held = voiceSession().silenceSkipNotice().after(focus(FocusChange.LossTransient))
        val atAdvancePoint = held.after(NoticeFinished(held.speakingNotices.single()), NoticeTailElapsed)

        val transition = atAdvancePoint.reduce(focus(FocusChange.Gain, after = 5.seconds))

        transition.effects shouldContain PresentHeadQuestion
        transition.effects shouldContain Play
    }

    @Test
    fun `an interruption that ends while a short notice still speaks lets the round go on by itself`() {
        val notice = voiceSession().silenceSkipNotice().after(focus(FocusChange.LossTransient))

        val transition = notice.reduce(focus(FocusChange.Gain, after = 1.seconds))

        transition.effects shouldContain ResumeWithoutReading
        transition.effects shouldNotContain Play
        transition.state.isPlaying shouldBe true
    }

    @Test
    fun `an interruption at an advance point held by a dialog only records that there is nothing to resume`() {
        val held = voiceSession().speakingFeedback().after(
            RatedSessionInput.AdvanceHoldRequested,
            NoticeFinished(SpokenNotice.Feedback(CorrectRating, RATIONALE)),
            NoticeTailElapsed,
        )
        held.isHeldAtAdvancePoint shouldBe true

        val interruption = held.reduce(focus(FocusChange.LossTransient))

        interruption.effects shouldNotContain PausePlayback
        interruption.state.episode.isResumeCancelled shouldBe true
        interruption.state.reduce(focus(FocusChange.Gain, after = 5.seconds)).effects shouldNotContain Play
    }

    // A silence pause or another pause during the episode

    @Test
    fun `a silence pause during the episode is not resumed by the gain`() {
        val silencePaused = voiceSession().paused(VoiceAnswerPauseReason.Silence)

        val transition = silencePaused.after(focus(FocusChange.LossTransient)).reduce(focus(FocusChange.Gain, after = 5.seconds))

        transition.effects shouldNotContain Play
    }

    private fun RatedSessionState.paused(reason: VoiceAnswerPauseReason): RatedSessionState =
        copy(voiceAnswerPauseReason = reason, round = VoiceAnswerRound(), isPlaying = false)

    @Test
    fun `a user pause during the episode cancels the auto-resume`() {
        val interruption = voiceSession().after(focus(FocusChange.LossTransient))

        val transition = interruption.after(PauseRequested).reduce(focus(FocusChange.Gain, after = 5.seconds))

        transition.effects shouldNotContain Play
    }

    @Test
    fun `an output disconnect pauses like a user and the later gain never resumes`() {
        val interruption = voiceSession().after(focus(FocusChange.LossTransient))

        val disconnected = interruption.reduce(signal(OutputDisconnected))
        val gain = disconnected.state.reduce(focus(FocusChange.Gain, after = 5.seconds))

        disconnected.effects shouldContain PausePlayback
        gain.effects shouldNotContain Play
    }

    @Test
    fun `an output disconnect while listening cancels the round`() {
        val transition = voiceSession().listening().reduce(signal(OutputDisconnected))

        transition.effects shouldContain StopListening
        transition.effects shouldContain PausePlayback
        transition.state.round.phase shouldBe VoiceAnswerPhase.WaitingForQuestion
        transition.state.consecutiveSilenceCount shouldBe 0
    }

    @Test
    fun `a gain with no episode does nothing`() {
        val transition = voiceSession().reduce(focus(FocusChange.Gain))

        transition.effects.shouldBeEmpty()
    }

    @Test
    fun `a session that was already paused by the user has nothing to resume`() {
        val paused = voiceSession().after(PauseRequested, playing(false))

        val interruption = paused.reduce(focus(FocusChange.LossTransient))

        interruption.effects shouldNotContain PausePlayback
        interruption.state.reduce(focus(FocusChange.Gain, after = 5.seconds)).effects shouldNotContain Play
    }

    @Test
    fun `a dialog opened during an interruption takes over the resume`() {
        val interruption = voiceSession().after(focus(FocusChange.LossTransient), playing(false))

        val dialogOpen = interruption.reduce(TemporaryPauseRequested)

        dialogOpen.state.isPausedTemporarily shouldBe true
        dialogOpen.state.reduce(focus(FocusChange.Gain, after = 5.seconds)).effects shouldNotContain Play
        dialogOpen.state.reduce(TemporaryPauseEnded).effects shouldContain Play
    }

    @Test
    fun `an interruption that starts during a dialog is not resumed by its gain, the dialog closing does`() {
        val dialog = voiceSession().after(TemporaryPauseRequested, playing(false))

        val interruption = dialog.reduce(focus(FocusChange.LossTransient))

        interruption.effects shouldNotContain PausePlayback
        interruption.state.reduce(focus(FocusChange.Gain, after = 5.seconds)).effects shouldNotContain Play
        interruption.state.reduce(TemporaryPauseEnded).effects shouldContain Play
    }

    // Calls and takeovers

    @Test
    fun `a picked-up call never auto-resumes, whatever its length`() {
        val call = voiceSession().after(focus(FocusChange.LossTransient), signal(AudioModeChanged(AudioMode.InCall), after = 1.seconds))

        val shortCall = call.after(signal(AudioModeChanged(AudioMode.Normal), after = 3.seconds))
            .reduce(focus(FocusChange.Gain, after = 1.seconds))

        shortCall.effects shouldNotContain Play
    }

    @Test
    fun `a call escalation while listening cancels the round like any interruption`() {
        val blip = voiceSession().listening().after(focus(FocusChange.LossCanDuck))

        val transition = blip.reduce(signal(AudioModeChanged(AudioMode.InCall)))

        transition.effects shouldContain StopListening
        transition.effects shouldContain PausePlayback
    }

    @Test
    fun `a takeover pauses and stays paused until the user plays`() {
        val takeover = voiceSession().reduce(focus(FocusChange.Loss))

        takeover.effects shouldContain PausePlayback
        takeover.state.reduce(focus(FocusChange.Gain, after = 5.seconds)).effects shouldNotContain Play
        val played = takeover.state.after(playing(false)).reduce(PlayRequested)
        played.effects shouldContain Play
        played.state.episode.isActive shouldBe false
    }

    @Test
    fun `a play during a plain transient loss wins over the hold`() {
        val interruption = voiceSession().after(focus(FocusChange.LossTransient), playing(false))

        val played = interruption.reduce(PlayRequested)

        played.effects shouldContain Play
        played.state.reduce(focus(FocusChange.Gain, after = 5.seconds)).effects shouldNotContain Play
    }

    // The call block

    @Test
    fun `a call that starts reports it once, however the call then moves on`() {
        val voice = voiceSession()

        val ringing = voice.reduce(signal(AudioModeChanged(AudioMode.Ringtone)))
        val pickedUp = ringing.state.reduce(signal(AudioModeChanged(AudioMode.InCall)))
        val ended = pickedUp.state.reduce(signal(AudioModeChanged(AudioMode.Normal)))
        val ringingAgain = ended.state.reduce(signal(AudioModeChanged(AudioMode.Ringtone)))

        ringing.effects shouldContain Emit(RatedSessionEvent.PlayIgnoredDuringCall)
        pickedUp.effects shouldNotContain Emit(RatedSessionEvent.PlayIgnoredDuringCall)
        ended.effects shouldNotContain Emit(RatedSessionEvent.PlayIgnoredDuringCall)
        ringingAgain.effects shouldContain Emit(RatedSessionEvent.PlayIgnoredDuringCall)
    }

    @Test
    fun `a player that starts reading under a ringing call is paused, and stays paused when the call ends`() {
        val ringing = unstartedVoiceSession().after(signal(AudioModeChanged(AudioMode.Ringtone)))

        val started = ringing.reduce(playing(true))
        val ended = started.state.after(playing(false)).reduce(signal(AudioModeChanged(AudioMode.Normal)))

        started.effects shouldContain PausePlayback
        ended.effects shouldNotContain Play
        ended.effects shouldNotContain ResumeWithoutReading
    }

    @Test
    fun `a player that starts reading with no call is left alone`() {
        unstartedVoiceSession().reduce(playing(true)).effects shouldNotContain PausePlayback
    }

    @Test
    fun `a play while a call rings starts nothing and reports it`() {
        val ringing = voiceSession().after(signal(AudioModeChanged(AudioMode.Ringtone)), focus(FocusChange.LossTransient), playing(false))

        val transition = ringing.reduce(PlayRequested)

        transition.effects shouldBe listOf(Emit(RatedSessionEvent.PlayIgnoredDuringCall))
        transition.state shouldBe ringing
    }

    @Test
    fun `a play during a call at every mode starts nothing`() {
        listOf(AudioMode.Ringtone, AudioMode.InCall, AudioMode.InCommunication).forEach { mode ->
            val session = voiceSession().after(signal(AudioModeChanged(mode)), focus(FocusChange.LossTransient), playing(false))

            session.reduce(PlayRequested).effects shouldBe listOf(Emit(RatedSessionEvent.PlayIgnoredDuringCall))
        }
    }

    @Test
    fun `a play while nothing needs playing does not report a call`() {
        val ringing = voiceSession().after(signal(AudioModeChanged(AudioMode.Ringtone)))

        ringing.reduce(PlayRequested).effects.shouldBeEmpty()
    }

    @Test
    fun `the end of a temporary pause during a call ends the pause and plays nothing`() {
        val dialog = voiceSession().after(TemporaryPauseRequested, playing(false), signal(AudioModeChanged(AudioMode.InCall)))

        val transition = dialog.reduce(TemporaryPauseEnded)

        transition.state.isPausedTemporarily shouldBe false
        transition.effects shouldNotContain Play
        transition.effects.filterIsInstance<SpeakNotice>().shouldBeEmpty()
    }

    @Test
    fun `the end of a temporary pause during a call leaves a paused feedback waiting for the user`() {
        val dialog = voiceSession().speakingFeedback().after(TemporaryPauseRequested, playing(false), signal(AudioModeChanged(AudioMode.InCall)))

        val transition = dialog.reduce(TemporaryPauseEnded)

        transition.state.isPausedAfterFeedback shouldBe true
        transition.effects.filterIsInstance<SpeakNotice>().shouldBeEmpty()
    }

    @Test
    fun `the release of a hold during a call moves on without playing`() {
        val held = voiceSession().speakingFeedback().after(
            RatedSessionInput.AdvanceHoldRequested,
            NoticeFinished(SpokenNotice.Feedback(CorrectRating, RATIONALE)),
            NoticeTailElapsed,
            signal(AudioModeChanged(AudioMode.InCall)),
            RatedSessionInput.AdvanceHoldReleased,
        )

        val transition = held.reduce(ReleaseLingerElapsed)

        transition.effects shouldNotContain Play
        transition.effects shouldContain PresentHeadQuestion
        transition.state.isHeldAtAdvancePoint shouldBe false
    }

    @Test
    fun `a resume after a voice-answer pause is ignored during a call`() {
        val paused = voiceSession().paused(VoiceAnswerPauseReason.Silence).after(signal(AudioModeChanged(AudioMode.InCall)))

        val transition = paused.reduce(ResumeRequested(isMicrophoneGranted = true))

        transition.effects shouldBe listOf(Emit(RatedSessionEvent.PlayIgnoredDuringCall))
    }

    @Test
    fun `a play works again once the call ended`() {
        val ended = voiceSession().after(
            signal(AudioModeChanged(AudioMode.InCall)),
            focus(FocusChange.LossTransient),
            playing(false),
            signal(AudioModeChanged(AudioMode.Normal)),
            focus(FocusChange.Gain, after = 5.seconds),
        )

        ended.reduce(PlayRequested).effects shouldContain Play
    }

    // The capture gate

    @Test
    fun `a blip while listening closes the gate and cancels the silence timer`() {
        val transition = voiceSession().listening().reduce(focus(FocusChange.LossCanDuck))

        transition.effects shouldContain SetCaptureGate(closed = true)
        transition.effects shouldContain CancelSilenceTimer
        transition.state.isCaptureGated shouldBe true
        transition.state.round.phase shouldBe VoiceAnswerPhase.Listening
    }

    @Test
    fun `the gate opens a tail after the blip and the silence timer restarts in full`() {
        val gated = voiceSession().listening().after(focus(FocusChange.LossCanDuck))

        val gain = gated.reduce(focus(FocusChange.Gain, after = 700.milliseconds))
        gain.effects shouldContain StartGateTail
        gain.state.isCaptureGated shouldBe true

        val reopened = gain.state.reduce(GateTailElapsed)
        reopened.effects shouldContain SetCaptureGate(closed = false)
        reopened.effects shouldContain StartSilenceTimer
        reopened.state.isCaptureGated shouldBe false
    }

    @Test
    fun `an output disconnect during a blip while listening keeps the gate closed until the blip ends`() {
        val gated = voiceSession().listening().after(focus(FocusChange.LossCanDuck))

        val disconnected = gated.reduce(signal(OutputDisconnected))
        disconnected.effects shouldContain StopListening
        disconnected.effects shouldNotContain SetCaptureGate(closed = false)
        disconnected.state.isCaptureGated shouldBe true

        val gain = disconnected.state.reduce(focus(FocusChange.Gain, after = 700.milliseconds))
        gain.effects shouldContain StartGateTail

        val reopened = gain.state.reduce(GateTailElapsed)
        reopened.effects shouldContain SetCaptureGate(closed = false)
        reopened.effects shouldNotContain StartSilenceTimer
        reopened.state.isCaptureGated shouldBe false
    }

    @Test
    fun `a silence timeout during the gate is ignored`() {
        val gated = voiceSession().listening().after(focus(FocusChange.LossCanDuck))

        val transition = gated.reduce(SilenceTimedOut)

        transition.state shouldBe gated
        transition.effects.shouldBeEmpty()
    }

    @Test
    fun `a microphone that opens during the gate defers the timer and the cue to the gate opening`() {
        val gated = voiceSession().after(QuestionFinished("card-1"), focus(FocusChange.LossCanDuck))

        val opened = gated.reduce(MicrophoneOpened)
        opened.effects shouldNotContain StartSilenceTimer
        opened.effects shouldNotContain PlayListeningCue

        val reopened = opened.state.after(focus(FocusChange.Gain)).reduce(GateTailElapsed)
        reopened.effects shouldContain StartSilenceTimer
        reopened.effects shouldContain PlayListeningCue
    }

    @Test
    fun `a new blip during the tail keeps the gate closed`() {
        val tail = voiceSession().listening().after(focus(FocusChange.LossCanDuck), focus(FocusChange.Gain))

        val blip = tail.reduce(focus(FocusChange.LossCanDuck))
        blip.effects shouldContain RatedSessionEffect.CancelGateTail
        blip.effects shouldNotContain SetCaptureGate(closed = true)

        blip.state.reduce(GateTailElapsed).state.isCaptureGated shouldBe true
    }

    @Test
    fun `a blip that gates a listening window for a minute cancels the round without an auto-resume`() {
        val gated = voiceSession().listening().after(focus(FocusChange.LossCanDuck))

        val stillGated = gated.reduce(BlipElapsed)
        stillGated.effects shouldContain StartBlipTimer(InterruptionEpisode.LISTENING_BLIP_ESCALATION - InterruptionEpisode.BLIP_SPEECH_THRESHOLD)
        stillGated.state.round.phase shouldBe VoiceAnswerPhase.Listening

        val cancelled = stillGated.state.reduce(BlipElapsed)
        cancelled.effects shouldContain StopListening
        cancelled.effects shouldContain PausePlayback
        cancelled.state.reduce(focus(FocusChange.Gain, after = 5.seconds)).effects shouldNotContain Play
    }

    @Test
    fun `a silenced microphone while listening cancels the round, and its return restarts the card`() {
        val listening = voiceSession().listening()

        val silenced = listening.reduce(signal(MicSilenced))
        silenced.effects shouldContain StopListening
        silenced.state.consecutiveSilenceCount shouldBe 0

        silenced.state.reduce(signal(MicUnsilenced, after = 5.seconds)).effects shouldContain Play
    }

    @Test
    fun `a silenced microphone outside a listening window is ignored`() {
        val transition = voiceSession().reduce(signal(MicSilenced))

        transition.effects.shouldBeEmpty()
        transition.state.episode.isActive shouldBe false
    }

    @Test
    fun `a gain while the microphone is still silenced does not resume`() {
        val silenced = voiceSession().listening().after(focus(FocusChange.LossTransient), signal(MicSilenced))

        val gain = silenced.reduce(focus(FocusChange.Gain, after = 5.seconds))
        gain.effects shouldNotContain Play

        gain.state.reduce(signal(MicUnsilenced, after = 5.seconds)).effects shouldContain Play
    }

    // Blips that outlast the threshold while nothing speaks

    @Test
    fun `a long blip that meets a grade holds the feedback instead of speaking it`() {
        val grading = voiceSession().grading().after(focus(FocusChange.LossCanDuck))
        val marked = grading.reduce(BlipElapsed).state
        marked.episode.isLongBlip shouldBe true

        val graded = marked.after(TranscriptReady(TRANSCRIPT)).reduce(Graded(grade()))

        graded.effects.filterIsInstance<SpeakNotice>().shouldBeEmpty()
        graded.state.isPausedAfterFeedback shouldBe true
        graded.effects shouldContain PausePlayback
    }

    @Test
    fun `a long blip that meets the end of a notice holds the next question`() {
        val notice = voiceSession().silenceSkipNotice().after(focus(FocusChange.LossCanDuck), BlipElapsed)
        val finished = notice.after(NoticeFinished(notice.speakingNotices.single()))

        val transition = finished.reduce(NoticeTailElapsed)

        transition.effects shouldNotContain PresentHeadQuestion
        transition.state.isPausedAtAdvancePoint shouldBe true
    }

    private fun SpokenNotice.shouldBeFeedback() {
        (this is SpokenNotice.Feedback) shouldBe true
    }

    private companion object {
        const val FIXED_SEED = 7
        const val CARD_COUNT = 3
        const val CORRECT_PERCENT = 95
        const val TRANSCRIPT = "the answer"
        const val RATIONALE = "well done"
        val WAV = byteArrayOf(1)
        val CorrectRating = FlashcardAttemptRating.Correct
    }
}
