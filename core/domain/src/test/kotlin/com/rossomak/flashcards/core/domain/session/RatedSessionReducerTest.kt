package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.model.GradingFailureReason
import com.rossomak.flashcards.core.domain.model.RatedSessionState
import com.rossomak.flashcards.core.domain.model.SessionPauseReason
import com.rossomak.flashcards.core.domain.model.SpokenNotice
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGrade
import com.rossomak.flashcards.core.domain.model.VoiceAnswerPauseReason
import com.rossomak.flashcards.core.domain.model.VoiceAnswerPhase
import com.rossomak.flashcards.core.domain.model.VoiceCaptureFailureReason
import com.rossomak.flashcards.core.domain.model.VoicePlaybackState
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.CancelGrading
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.CancelReleaseLinger
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.CancelSilenceTimer
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.Emit
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.EndForRevokedMicPermission
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.Grade
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.OpenListeningWindow
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.PausePlayback
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.Play
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.PlayListeningCue
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.PresentHeadQuestion
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.RestartVoiceStack
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.ResumeWithoutReading
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.SessionComplete
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.SpeakNotice
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.StartNoticeTail
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.StartReleaseLinger
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.StartSilenceTimer
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.StartVoiceAnswering
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.StopListening
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.StopVoiceAnswering
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.StopVoiceStack
import com.rossomak.flashcards.core.domain.session.RatedSessionEffect.SyncQueue
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.AdvanceHoldReleased
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.AdvanceHoldRequested
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.AttemptRated
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.CaptureFailed
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.CardSkipped
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.FeedbackSkipRequested
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.Graded
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.GradingFailed
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.MicrophoneOpened
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.NoticeFinished
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.NoticeTailElapsed
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.PauseRequested
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.PlayRequested
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.PlaybackChanged
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.PlaybackEngineUnavailable
import com.rossomak.flashcards.core.domain.session.RatedSessionInput.PreviousRequested
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
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import kotlin.random.Random
import org.junit.Test

class RatedSessionReducerTest {

    private val reducer = RatedSessionReducer(Random(FIXED_SEED), mockk(relaxed = true))

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

    private fun voiceSession(cardCount: Int = CARD_COUNT, attemptsLimit: Int = 3): RatedSessionState =
        reducer.seed(
            cards = (1..cardCount).map { flashcard("card-$it") },
            attemptsLimit = attemptsLimit,
            isVoiceAnsweringSession = true,
        ).let { reducer.reduce(it, PlaybackChanged(VoicePlaybackState(isActive = true, isPlaying = true))).state }

    private fun manualSession(cardCount: Int = CARD_COUNT): RatedSessionState =
        reducer.seed(cards = (1..cardCount).map { flashcard("card-$it") }, attemptsLimit = 3)

    private fun RatedSessionState.after(vararg inputs: RatedSessionInput): RatedSessionState =
        inputs.fold(this) { state, input -> reducer.reduce(state, input).state }

    private fun RatedSessionState.head(): String = requireNotNull(currentCard).id

    private fun RatedSessionState.listening(): RatedSessionState = after(QuestionFinished(head()))

    private fun RatedSessionState.grading(): RatedSessionState = listening().after(SpeechStarted, SpeechEnded, UtteranceCaptured(WAV))

    private fun RatedSessionState.speakingFeedback(gradePercent: Int = CORRECT_PERCENT): RatedSessionState =
        grading().after(TranscriptReady(TRANSCRIPT), Graded(grade(gradePercent)))

    private fun RatedSessionState.silence(): RatedSessionState = listening().after(SilenceTimedOut)

    private fun RatedSessionState.gradingFailure(reason: GradingFailureReason = GradingFailureReason.NoConnection): RatedSessionState =
        grading().after(GradingFailed(reason))

    /** A user pause, with the player reporting it paused. */
    private fun RatedSessionState.paused(): RatedSessionState =
        after(PauseRequested, PlaybackChanged(VoicePlaybackState(isActive = true, isPlaying = false)))

    /** A temporary pause, with the player reporting it paused. */
    private fun RatedSessionState.temporarilyPaused(): RatedSessionState =
        after(TemporaryPauseRequested, PlaybackChanged(VoicePlaybackState(isActive = true, isPlaying = false)))

    /** Finishes every spoken notice and, for an advancing one, its tail. */
    private fun RatedSessionState.noticesOver(): RatedSessionState {
        var state = this
        while (state.speakingNotices.isNotEmpty()) state = state.after(NoticeFinished(state.speakingNotices.first()))
        return state.after(NoticeTailElapsed)
    }

    private fun grade(gradePercent: Int) = VoiceAnswerGrade(sanitizedTranscript = TRANSCRIPT, gradePercent = gradePercent, feedback = RATIONALE)

    // Manual ratings

    @Test
    fun `a manual rating syncs the queue at once and completes the session when the queue empties`() {
        val transition = reducer.reduce(manualSession(cardCount = 1), AttemptRated(FlashcardAttemptRating.Correct))

        transition.effects shouldBe listOf(SyncQueue(emptyList()), SessionComplete)
        transition.state.remainingCards.shouldBeEmpty()
    }

    @Test
    fun `a manual rating resets the answer reveal with the sync`() {
        val revealed = manualSession().after(RatedSessionInput.AnswerRevealed)
        revealed.isAnswerRevealed shouldBe true

        revealed.after(AttemptRated(FlashcardAttemptRating.Correct)).isAnswerRevealed shouldBe false
    }

    // The round

    @Test
    fun `a finished question opens the listening window for the head`() {
        val session = voiceSession()

        val transition = reducer.reduce(session, QuestionFinished(session.head()))

        transition.state.round.phase shouldBe VoiceAnswerPhase.Listening
        transition.effects shouldBe listOf(OpenListeningWindow(session.head()))
    }

    @Test
    fun `a finished question opens the window with the microphone still being prepared`() {
        val listening = voiceSession().listening()

        listening.round.phase shouldBe VoiceAnswerPhase.Listening
        listening.round.isMicrophoneOpen shouldBe false
    }

    @Test
    fun `the microphone opening starts the silence timer and plays the listening cue`() {
        val transition = reducer.reduce(voiceSession().listening(), MicrophoneOpened)

        transition.state.round.isMicrophoneOpen shouldBe true
        transition.effects shouldBe listOf(StartSilenceTimer, PlayListeningCue)
    }

    @Test
    fun `the microphone opening again in the same window changes nothing`() {
        val open = voiceSession().listening().after(MicrophoneOpened)

        val transition = reducer.reduce(open, MicrophoneOpened)

        transition.state shouldBe open
        transition.effects.shouldBeEmpty()
    }

    @Test
    fun `the microphone opening outside a listening window changes nothing`() {
        val grading = voiceSession().grading()

        val transition = reducer.reduce(grading, MicrophoneOpened)

        transition.state shouldBe grading
        transition.effects.shouldBeEmpty()
    }

    @Test
    fun `a finished question while listening closes the window and opens a fresh one`() {
        val listening = voiceSession().listening().after(MicrophoneOpened)

        val transition = reducer.reduce(listening, QuestionFinished(listening.head()))

        transition.effects shouldBe listOf(StopListening, OpenListeningWindow(listening.head()))
        transition.state.round.isMicrophoneOpen shouldBe false
    }

    @Test
    fun `the player stopping on its own while listening closes the window, and the question read again opens a fresh one`() {
        val listening = voiceSession().listening().after(MicrophoneOpened)

        val stopped = reducer.reduce(listening, PlaybackChanged(VoicePlaybackState(isActive = true, isPlaying = false)))

        stopped.effects shouldBe listOf(StopListening)
        stopped.state.round.phase shouldBe VoiceAnswerPhase.WaitingForQuestion
        val replaying = stopped.state.after(PlaybackChanged(VoicePlaybackState(isActive = true, isPlaying = true)))
        reducer.reduce(replaying, QuestionFinished(listening.head())).effects shouldBe listOf(OpenListeningWindow(listening.head()))
    }

    @Test
    fun `the player starting on its own while waiting for the question changes nothing but whether it plays`() {
        val stopped = voiceSession().listening().after(
            MicrophoneOpened,
            PlaybackChanged(VoicePlaybackState(isActive = true, isPlaying = false)),
        )

        val transition = reducer.reduce(stopped, PlaybackChanged(VoicePlaybackState(isActive = true, isPlaying = true)))

        transition.effects.shouldBeEmpty()
        transition.state shouldBe stopped.copy(isPlaying = true)
    }

    @Test
    fun `a start the session asked for does not present the question a second time`() {
        val paused = voiceSession().paused()
        val played = reducer.reduce(paused, PlayRequested)
        played.effects shouldBe listOf(Play)

        val transition = reducer.reduce(played.state, PlaybackChanged(VoicePlaybackState(isActive = true, isPlaying = true)))

        transition.effects.shouldBeEmpty()
    }

    @Test
    fun `the player starting on its own during a notice does not present the question`() {
        val speaking = voiceSession().speakingFeedback().after(PlaybackChanged(VoicePlaybackState(isActive = true, isPlaying = false)))

        val transition = reducer.reduce(speaking, PlaybackChanged(VoicePlaybackState(isActive = true, isPlaying = true)))

        transition.effects.shouldBeEmpty()
    }

    @Test
    fun `a finished question while grading is ignored`() {
        val grading = voiceSession().grading()

        val transition = reducer.reduce(grading, QuestionFinished(grading.head()))

        transition.state shouldBe grading
        transition.effects.shouldBeEmpty()
    }

    @Test
    fun `a finished question during a notice is ignored`() {
        val speaking = voiceSession().silence()

        reducer.reduce(speaking, QuestionFinished(speaking.head())).effects.shouldBeEmpty()
    }

    @Test
    fun `a finished question for a card other than the head is ignored`() {
        reducer.reduce(voiceSession(), QuestionFinished("card-2")).effects.shouldBeEmpty()
    }

    @Test
    fun `a finished question while voice answering is paused is ignored`() {
        val paused = voiceSession().silence().noticesOver().silence().noticesOver().silence().noticesOver()
        paused.voiceAnswerPauseReason shouldBe VoiceAnswerPauseReason.Silence

        reducer.reduce(paused, QuestionFinished(paused.head())).effects.shouldBeEmpty()
    }

    @Test
    fun `speech cancels the silence timer and its end starts grading`() {
        val started = reducer.reduce(voiceSession().listening(), SpeechStarted)
        started.state.round.phase shouldBe VoiceAnswerPhase.SpeechDetected
        started.effects shouldBe listOf(CancelSilenceTimer)

        started.state.after(SpeechEnded).round.phase shouldBe VoiceAnswerPhase.Grading
    }

    @Test
    fun `a captured answer stops listening and grades the round's card`() {
        val listening = voiceSession().listening().after(SpeechStarted, SpeechEnded)

        val transition = reducer.reduce(listening, UtteranceCaptured(WAV))

        transition.effects[0] shouldBe CancelSilenceTimer
        transition.effects[1] shouldBe StopListening
        val grade = transition.effects[2].shouldBeInstanceOf<Grade>()
        grade.card.id shouldBe listening.head()
        transition.state.round.phase shouldBe VoiceAnswerPhase.Grading
        transition.state.isAnswerRevealed shouldBe true
    }

    @Test
    fun `a transcript then a grade speak the feedback and rate the head, keeping it current until the notice finishes`() {
        val grading = voiceSession().grading()
        val gradedCardId = grading.head()

        val transition = reducer.reduce(grading.after(TranscriptReady(TRANSCRIPT)), Graded(grade(PARTIAL_PERCENT)))

        transition.effects shouldBe listOf(SpeakNotice(SpokenNotice.Feedback(FlashcardAttemptRating.PartiallyCorrect, RATIONALE)))
        with(transition.state) {
            round.transcript shouldBe TRANSCRIPT
            round.phase shouldBe VoiceAnswerPhase.SpeakingNotice
            isSyncPending shouldBe true
            currentCard?.id shouldBe gradedCardId
            currentCardRatings shouldBe listOf(FlashcardAttemptRating.PartiallyCorrect)
            isAnswerRevealed shouldBe true
        }
    }

    @Test
    fun `a grade updates the counts at once, while the rated card stays current until the sync`() {
        val speaking = voiceSession().speakingFeedback(CORRECT_PERCENT)

        with(speaking) {
            masteredCount shouldBe 1
            completedCount shouldBe 1
            currentCard?.id shouldBe "card-1"
            remainingCards.size shouldBe CARD_COUNT
        }
        speaking.noticesOver().remainingCards.size shouldBe CARD_COUNT - 1
    }

    @Test
    fun `a manual rating is ignored while a voice grade waits for its sync`() {
        val speaking = voiceSession().speakingFeedback(PARTIAL_PERCENT)

        val transition = reducer.reduce(speaking, AttemptRated(FlashcardAttemptRating.Correct))

        transition.effects.shouldBeEmpty()
        transition.state shouldBe speaking
    }

    @Test
    fun `a stale grade for a card that is no longer the head rates nothing but still speaks its feedback`() {
        val grading = voiceSession().grading()
        val stale = grading.copy(queue = grading.queue.drop(1) + grading.queue.first())

        val transition = reducer.reduce(stale, Graded(grade(CORRECT_PERCENT)))

        transition.state.terminalStates shouldBe stale.terminalStates
        transition.state.isSyncPending shouldBe false
        transition.effects shouldBe listOf(SpeakNotice(SpokenNotice.Feedback(FlashcardAttemptRating.Correct, RATIONALE)))
    }

    @Test
    fun `a silence under the threshold requeues the head and speaks the skip notice`() {
        val listening = voiceSession().listening()

        val transition = reducer.reduce(listening, SilenceTimedOut)

        transition.effects shouldBe listOf(StopListening, SpeakNotice(SpokenNotice.SilenceSkip), Emit(RatedSessionEvent.VoiceAnswerSilenceSkip))
        with(transition.state) {
            consecutiveSilenceCount shouldBe 1
            isSyncPending shouldBe true
            currentCard?.id shouldBe "card-1"
            currentCardRatings.shouldBeEmpty()
        }
    }

    @Test
    fun `the third silence in a row speaks the pause notice and pauses voice answering`() {
        val twoSilences = voiceSession().silence().noticesOver().silence().noticesOver()

        val transition = reducer.reduce(twoSilences.listening(), SilenceTimedOut)

        transition.effects shouldBe listOf(
            StopListening,
            SpeakNotice(SpokenNotice.SilencePause),
            PausePlayback,
            StopVoiceAnswering,
            CancelGrading,
            Emit(RatedSessionEvent.VoiceAnswerSilencePause),
        )
        transition.state.voiceAnswerPauseReason shouldBe VoiceAnswerPauseReason.Silence
        transition.state.round.phase shouldBe VoiceAnswerPhase.Idle
    }

    @Test
    fun `a grading failure under the threshold requeues the head and speaks its cause`() {
        val transition = reducer.reduce(voiceSession().grading(), GradingFailed(GradingFailureReason.ServiceError))

        transition.effects shouldBe listOf(
            SpeakNotice(SpokenNotice.GradingFailed(GradingFailureReason.ServiceError)),
            Emit(RatedSessionEvent.VoiceAnswerGradingFailed(GradingFailureReason.ServiceError)),
        )
        transition.state.consecutiveGradingFailureCount shouldBe 1
        transition.state.round.gradingFailure shouldBe GradingFailureReason.ServiceError
    }

    @Test
    fun `failure, silence, failure, failure pauses on the third failure`() {
        val beforeLast = voiceSession()
            .gradingFailure().noticesOver()
            .silence().noticesOver()
            .gradingFailure().noticesOver()

        val transition = reducer.reduce(beforeLast.grading(), GradingFailed(GradingFailureReason.NoConnection))

        transition.effects shouldContain SpeakNotice(SpokenNotice.GradingPause)
        transition.state.voiceAnswerPauseReason shouldBe VoiceAnswerPauseReason.GradingFailures
        transition.state.consecutiveSilenceCount shouldBe 1
    }

    @Test
    fun `silence, failure, silence, silence pauses on the third silence`() {
        val paused = voiceSession()
            .silence().noticesOver()
            .gradingFailure().noticesOver()
            .silence().noticesOver()
            .silence()

        paused.voiceAnswerPauseReason shouldBe VoiceAnswerPauseReason.Silence
        paused.consecutiveGradingFailureCount shouldBe 1
    }

    @Test
    fun `a graded answer resets both counters`() {
        val state = voiceSession().silence().noticesOver().gradingFailure().noticesOver().speakingFeedback()

        state.consecutiveSilenceCount shouldBe 0
        state.consecutiveGradingFailureCount shouldBe 0
    }

    @Test
    fun `a resume resets both counters and starts voice answering again`() {
        val paused = voiceSession()
            .silence().noticesOver().gradingFailure().noticesOver()
            .silence().noticesOver().silence().noticesOver()
            .after(PlaybackChanged(VoicePlaybackState(isActive = true, isPlaying = false)))
        paused.voiceAnswerPauseReason shouldBe VoiceAnswerPauseReason.Silence

        val transition = reducer.reduce(paused, ResumeRequested(isMicrophoneGranted = true))

        transition.effects shouldBe listOf(StartVoiceAnswering, Play)
        with(transition.state) {
            voiceAnswerPauseReason shouldBe null
            consecutiveSilenceCount shouldBe 0
            consecutiveGradingFailureCount shouldBe 0
            round.phase shouldBe VoiceAnswerPhase.WaitingForQuestion
        }
    }

    @Test
    fun `a capture failure speaks its notice, pauses and restarts the card, without requeue or advance`() {
        val listening = voiceSession().listening()

        val transition = reducer.reduce(listening, CaptureFailed(VoiceCaptureFailureReason.BluetoothMicUnavailable))

        transition.effects shouldBe listOf(
            StopListening,
            CancelSilenceTimer,
            CancelGrading,
            SpeakNotice(SpokenNotice.CaptureFailed),
            PausePlayback,
            StopVoiceAnswering,
            CancelGrading,
            PresentHeadQuestion,
            Emit(RatedSessionEvent.VoiceAnswerCaptureUnavailable),
        )
        transition.state.queue shouldBe listening.queue
        transition.state.voiceAnswerPauseReason shouldBe VoiceAnswerPauseReason.CaptureFailed
        transition.state.after(NoticeFinished(SpokenNotice.CaptureFailed), NoticeTailElapsed).let { after ->
            after.isPausedAtAdvancePoint shouldBe false
        }
        reducer.reduce(transition.state, NoticeFinished(SpokenNotice.CaptureFailed)).effects.shouldBeEmpty()
    }

    @Test
    fun `a capture failure while paused is ignored`() {
        val paused = voiceSession().after(CaptureFailed(VoiceCaptureFailureReason.AudioRecordInitFailed))

        reducer.reduce(paused, CaptureFailed(VoiceCaptureFailureReason.AudioRecordInitFailed)).effects.shouldBeEmpty()
    }

    // The notice lifecycle

    @Test
    fun `an advancing notice waits its tail, then syncs the queue before advancing`() {
        val speaking = voiceSession().speakingFeedback()

        val finished = reducer.reduce(speaking, NoticeFinished(speaking.speakingNotices.first()))
        finished.effects shouldBe listOf(StartNoticeTail)
        finished.state.isSyncPending shouldBe true

        val tail = reducer.reduce(finished.state, NoticeTailElapsed)
        tail.effects shouldBe listOf(SyncQueue(tail.state.remainingCards), PresentHeadQuestion)
        tail.state.currentCard?.id shouldBe "card-2"
        tail.state.isAnswerRevealed shouldBe false
        tail.state.round.phase shouldBe VoiceAnswerPhase.WaitingForQuestion
    }

    @Test
    fun `a pause notice syncs the queue when it finishes, without a tail or an advance`() {
        val pausing = voiceSession().silence().noticesOver().silence().noticesOver().silence()

        val transition = reducer.reduce(pausing, NoticeFinished(SpokenNotice.SilencePause))

        transition.effects shouldBe listOf(SyncQueue(transition.state.remainingCards))
        transition.state.currentCard?.id shouldNotBe pausing.currentCard?.id
    }

    @Test
    fun `the last card completes the session only after its notice and tail`() {
        val speaking = voiceSession(cardCount = 1).speakingFeedback(gradePercent = CORRECT_PERCENT)
        speaking.isComplete shouldBe false
        speaking.isSyncPending shouldBe true

        val finished = reducer.reduce(speaking, NoticeFinished(speaking.speakingNotices.first()))
        finished.effects shouldNotContain SessionComplete

        reducer.reduce(finished.state, NoticeTailElapsed).effects shouldBe listOf(SyncQueue(emptyList()), SessionComplete)
    }

    @Test
    fun `a notice finishing after playback paused holds the session at the advance point, and play advances`() {
        val speaking = voiceSession().speakingFeedback().after(PlaybackChanged(VoicePlaybackState(isActive = true, isPlaying = false)))

        val held = speaking.noticesOver()
        held.isPausedAtAdvancePoint shouldBe true

        val play = reducer.reduce(held, PlayRequested)
        play.effects shouldBe listOf(PresentHeadQuestion, Play)
        play.state.isPausedAtAdvancePoint shouldBe false
    }

    @Test
    fun `releasing a held session stays on the answered card for the linger, then advances`() {
        val held = voiceSession().after(AdvanceHoldRequested).speakingFeedback().noticesOver()
        held.isHeldAtAdvancePoint shouldBe true

        val released = reducer.reduce(held, AdvanceHoldReleased)
        released.effects shouldBe listOf(StartReleaseLinger)
        released.state.isHeldAtAdvancePoint shouldBe true

        val elapsed = reducer.reduce(released.state, ReleaseLingerElapsed)
        elapsed.effects.last() shouldBe PresentHeadQuestion
        elapsed.state.isHeldAtAdvancePoint shouldBe false
    }

    @Test
    fun `a hold requested again during the linger keeps the session held`() {
        val lingering = voiceSession().after(AdvanceHoldRequested).speakingFeedback().noticesOver().after(AdvanceHoldReleased)

        val transition = reducer.reduce(lingering, AdvanceHoldRequested)

        transition.effects shouldBe listOf(CancelReleaseLinger)
        transition.state.isHeldAtAdvancePoint shouldBe true
        reducer.reduce(transition.state, ReleaseLingerElapsed).effects.shouldBeEmpty()
    }

    @Test
    fun `a pause during the linger drops it, and its end then does nothing`() {
        val lingering = voiceSession().after(AdvanceHoldRequested).speakingFeedback().noticesOver().after(AdvanceHoldReleased)

        val transition = reducer.reduce(lingering, PauseRequested)

        transition.effects shouldBe listOf(PausePlayback, CancelReleaseLinger)
        transition.state.isReleaseLingering shouldBe false
        reducer.reduce(transition.state, ReleaseLingerElapsed).effects.shouldBeEmpty()
    }

    @Test
    fun `playback starting by itself does not clear the advance point`() {
        val held = voiceSession().speakingFeedback()
            .after(PlaybackChanged(VoicePlaybackState(isActive = true, isPlaying = false)))
            .noticesOver()

        held.after(PlaybackChanged(VoicePlaybackState(isActive = true, isPlaying = true))).isPausedAtAdvancePoint shouldBe true
    }

    // Transport commands

    @Test
    fun `next at the question requeues the head without an Attempt and advances while playing`() {
        val session = voiceSession()

        val transition = reducer.reduce(session, CardSkipped)

        transition.effects shouldBe listOf(SyncQueue(transition.state.remainingCards), PresentHeadQuestion)
        transition.state.currentCard?.id shouldBe "card-2"
        transition.state.queue.first { it.card.id == "card-1" }.ratings.shouldBeEmpty()
        transition.state.consecutiveSilenceCount shouldBe 0
    }

    @Test
    fun `next while paused shows the new head without advancing`() {
        val paused = voiceSession().after(PlaybackChanged(VoicePlaybackState(isActive = true, isPlaying = false)))

        val transition = reducer.reduce(paused, CardSkipped)

        transition.effects shouldBe listOf(SyncQueue(transition.state.remainingCards))
    }

    @Test
    fun `next and previous are ignored while listening, grading or speaking a short notice`() {
        listOf(voiceSession().listening(), voiceSession().grading(), voiceSession().silence()).forEach { state ->
            reducer.reduce(state, CardSkipped).effects.shouldBeEmpty()
            reducer.reduce(state, PreviousRequested).effects.shouldBeEmpty()
        }
    }

    @Test
    fun `previous is ignored during the grading feedback`() {
        reducer.reduce(voiceSession().speakingFeedback(), PreviousRequested).effects.shouldBeEmpty()
    }

    @Test
    fun `next and previous are ignored while voice answering is paused`() {
        val paused = voiceSession().after(CaptureFailed(VoiceCaptureFailureReason.BluetoothMicUnavailable)).noticesOver()

        reducer.reduce(paused, CardSkipped).effects.shouldBeEmpty()
        reducer.reduce(paused, PreviousRequested).effects.shouldBeEmpty()
    }

    @Test
    fun `previous at the question restarts the presented card`() {
        reducer.reduce(voiceSession(), PreviousRequested).effects shouldBe listOf(PresentHeadQuestion)
    }

    @Test
    fun `presenting the head question also plays when the player is paused`() {
        val paused = voiceSession().silence().paused().noticesOver()
        paused.isPlaying shouldBe false

        reducer.reduce(paused, CardSkipped).effects.takeLast(2) shouldBe listOf(PresentHeadQuestion, Play)
    }

    @Test
    fun `presenting the head question does not play again when the player already plays`() {
        val playing = voiceSession()
        playing.isPlaying shouldBe true

        reducer.reduce(playing, CardSkipped).effects.shouldNotContain(Play)
    }

    @Test
    fun `next at the auto-advance point moves on and plays`() {
        val atAdvancePoint = voiceSession().silence().paused().noticesOver()
        atAdvancePoint.isPausedAtAdvancePoint shouldBe true

        val transition = reducer.reduce(atAdvancePoint, CardSkipped)

        transition.effects shouldBe listOf(PresentHeadQuestion, Play)
        transition.state.isPausedAtAdvancePoint shouldBe false
    }

    // Pause, by phase

    @Test
    fun `a pause while listening cancels the round without a count, an Attempt or a notice`() {
        val listening = voiceSession().listening()

        val transition = reducer.reduce(listening, PauseRequested)

        transition.effects shouldBe listOf(StopListening, PausePlayback)
        with(transition.state) {
            round.phase shouldBe VoiceAnswerPhase.WaitingForQuestion
            queue shouldBe listening.queue
            consecutiveSilenceCount shouldBe 0
            speakingNotices.shouldBeEmpty()
        }
    }

    @Test
    fun `play after a pause while listening reads the question again, then listens`() {
        val paused = voiceSession().listening().paused()

        reducer.reduce(paused, PlayRequested).effects shouldBe listOf(Play)
        val playing = paused.after(PlayRequested, PlaybackChanged(VoicePlaybackState(isActive = true, isPlaying = true)))
        reducer.reduce(playing, QuestionFinished("card-1")).effects shouldBe listOf(OpenListeningWindow("card-1"))
    }

    @Test
    fun `a pause while grading lets grading go on`() {
        val transition = reducer.reduce(voiceSession().grading(), PauseRequested)

        transition.effects shouldBe listOf(PausePlayback)
        transition.state.isPausedWhileGrading shouldBe true
        transition.state.round.phase shouldBe VoiceAnswerPhase.Grading
    }

    @Test
    fun `a grade arriving while paused is recorded without speaking its feedback`() {
        val transition = reducer.reduce(voiceSession().grading().paused(), Graded(grade(CORRECT_PERCENT)))

        transition.effects.filterIsInstance<SpeakNotice>().shouldBeEmpty()
        with(transition.state) {
            currentCardRatings shouldBe listOf(FlashcardAttemptRating.Correct)
            isPausedAfterFeedback shouldBe true
            isPausedWhileGrading shouldBe false
            currentCard?.id shouldBe "card-1"
        }
    }

    @Test
    fun `play before the grade arrives goes back to waiting for it, without reading, and the feedback plays on arrival`() {
        val paused = voiceSession().grading().paused()

        val play = reducer.reduce(paused, PlayRequested)

        play.effects shouldBe listOf(ResumeWithoutReading)
        play.state.isPausedWhileGrading shouldBe false
        reducer.reduce(play.state, Graded(grade(CORRECT_PERCENT))).effects shouldContain
            SpeakNotice(SpokenNotice.Feedback(FlashcardAttemptRating.Correct, RATIONALE))
    }

    @Test
    fun `a grading failure while paused reports itself and requeues, without its notice, paused at the auto-advance point`() {
        val transition = reducer.reduce(voiceSession().grading().paused(), GradingFailed(GradingFailureReason.NoConnection))

        transition.effects shouldContain Emit(RatedSessionEvent.VoiceAnswerGradingFailed(GradingFailureReason.NoConnection))
        transition.effects shouldContain SyncQueue(transition.state.remainingCards)
        transition.effects.filterIsInstance<SpeakNotice>().shouldBeEmpty()
        with(transition.state) {
            isPausedAtAdvancePoint shouldBe true
            currentCard?.id shouldBe "card-2"
            consecutiveGradingFailureCount shouldBe 1
        }
        reducer.reduce(transition.state, PlayRequested).effects shouldBe listOf(PresentHeadQuestion, Play)
    }

    @Test
    fun `the third grading failure while paused pauses voice answering without its notice`() {
        val twoFailures = voiceSession().gradingFailure().noticesOver().gradingFailure().noticesOver()

        val transition = reducer.reduce(twoFailures.grading().paused(), GradingFailed(GradingFailureReason.ServiceError))

        transition.effects shouldContain Emit(RatedSessionEvent.VoiceAnswerGradingPause)
        transition.effects.filterIsInstance<SpeakNotice>().shouldBeEmpty()
        transition.state.voiceAnswerPauseReason shouldBe VoiceAnswerPauseReason.GradingFailures
        transition.state.isSyncPending shouldBe false
    }

    @Test
    fun `a pause during the feedback stops it before the auto-advance point, so the queue does not sync`() {
        val transition = reducer.reduce(voiceSession().speakingFeedback(), PauseRequested)

        transition.effects shouldBe listOf(RatedSessionEffect.StopFeedback, RatedSessionEffect.CancelNoticeTail, PausePlayback)
        with(transition.state) {
            isPausedAfterFeedback shouldBe true
            isSyncPending shouldBe true
            currentCard?.id shouldBe "card-1"
            speakingNotices.shouldBeEmpty()
        }
    }

    @Test
    fun `a pause in the tail after the feedback cancels the tail and waits after the feedback`() {
        val feedback = voiceSession().speakingFeedback()
        val inTail = feedback.after(NoticeFinished(feedback.speakingNotices.single()))

        val transition = reducer.reduce(inTail, PauseRequested)

        transition.effects shouldBe listOf(RatedSessionEffect.CancelNoticeTail, PausePlayback)
        transition.state.isPausedAfterFeedback shouldBe true
    }

    @Test
    fun `play after a paused feedback reads it again and never rates again`() {
        val paused = voiceSession().speakingFeedback().paused()

        val play = reducer.reduce(paused, PlayRequested)

        play.effects shouldBe listOf(ResumeWithoutReading, SpeakNotice(SpokenNotice.Feedback(FlashcardAttemptRating.Correct, RATIONALE)))
        play.state.currentCardRatings shouldBe paused.currentCardRatings
        play.state.isPausedAfterFeedback shouldBe false
        play.state.after(PlaybackChanged(VoicePlaybackState(isActive = true, isPlaying = true))).noticesOver().currentCard?.id shouldBe "card-2"
    }

    @Test
    fun `next while paused after the feedback shows the next card, still paused`() {
        val transition = reducer.reduce(voiceSession().speakingFeedback().paused(), CardSkipped)

        transition.effects shouldBe listOf(SyncQueue(transition.state.remainingCards))
        transition.state.currentCard?.id shouldBe "card-2"
        transition.state.isPausedAfterFeedback shouldBe false
        transition.state.round.phase shouldBe VoiceAnswerPhase.WaitingForQuestion
    }

    @Test
    fun `a pause during a short notice lets it finish, then waits at the auto-advance point`() {
        val silence = voiceSession().silence()

        val transition = reducer.reduce(silence, PauseRequested)

        transition.effects shouldBe listOf(PausePlayback)
        transition.state.speakingNotices shouldBe listOf(SpokenNotice.SilenceSkip)
        transition.state.after(PlaybackChanged(VoicePlaybackState(isActive = true, isPlaying = false))).noticesOver()
            .isPausedAtAdvancePoint shouldBe true
    }

    @Test
    fun `a voice-answer pause pauses the player even when it already is, dropping a pending auto-resume`() {
        val twoFailures = voiceSession().gradingFailure().noticesOver().gradingFailure().noticesOver()
        val gradingWhilePaused = twoFailures.grading().after(PlaybackChanged(VoicePlaybackState(isActive = true, isPlaying = false)))

        reducer.reduce(gradingWhilePaused, GradingFailed(GradingFailureReason.NoConnection)).effects shouldContain PausePlayback
    }

    // Temporary pause

    @Test
    fun `a temporary pause while listening cancels the round, and its end reads the question again`() {
        val listening = voiceSession().listening()

        val transition = reducer.reduce(listening, TemporaryPauseRequested)

        transition.effects shouldBe listOf(StopListening, PausePlayback)
        transition.state.round.phase shouldBe VoiceAnswerPhase.WaitingForQuestion
        transition.state.queue shouldBe listening.queue
        reducer.reduce(listening.temporarilyPaused(), TemporaryPauseEnded).effects shouldBe listOf(Play)
    }

    // Feedback skip

    @Test
    fun `next during the feedback skips it, syncing the queue once before the next question`() {
        val transition = reducer.reduce(voiceSession().speakingFeedback(), CardSkipped)

        transition.effects shouldBe listOf(
            RatedSessionEffect.StopFeedback,
            RatedSessionEffect.CancelNoticeTail,
            SyncQueue(transition.state.remainingCards),
            PresentHeadQuestion,
        )
        transition.state.currentCard?.id shouldBe "card-2"
        transition.state.speakingNotices.shouldBeEmpty()
    }

    @Test
    fun `a feedback skip acts only while the feedback plays`() {
        reducer.reduce(voiceSession().speakingFeedback(), FeedbackSkipRequested).effects shouldContain PresentHeadQuestion
        listOf(voiceSession(), voiceSession().grading(), voiceSession().silence(), voiceSession().speakingFeedback().paused()).forEach { state ->
            reducer.reduce(state, FeedbackSkipRequested).effects.shouldBeEmpty()
        }
    }

    // Engine unavailable

    @Test
    fun `an unavailable engine cancels the open round without a requeue and pauses the session`() {
        val grading = voiceSession().grading()

        val transition = reducer.reduce(grading, PlaybackEngineUnavailable)

        transition.effects shouldContainInOrder listOf(StopVoiceStack, Emit(RatedSessionEvent.VoicePlaybackUnavailable))
        with(transition.state) {
            pauseReason shouldBe SessionPauseReason.VoiceEngineUnavailable
            queue shouldBe grading.queue
            consecutiveSilenceCount shouldBe 0
            consecutiveGradingFailureCount shouldBe 0
            round.phase shouldBe VoiceAnswerPhase.Idle
            isVoiceAnsweringSession shouldBe true
        }
    }

    @Test
    fun `an unavailable engine runs a sync still waiting on a notice`() {
        val speaking = voiceSession().speakingFeedback()

        val transition = reducer.reduce(speaking, PlaybackEngineUnavailable)

        transition.effects shouldContain SyncQueue(transition.state.remainingCards)
        transition.state.currentCard?.id shouldBe "card-2"
        transition.state.speakingNotices.shouldBeEmpty()
    }

    @Test
    fun `a restarted voice stack clears the pause and starts voice answering without playing`() {
        val paused = voiceSession().after(PlaybackEngineUnavailable)

        val transition = reducer.reduce(paused, ResumeRequested(isMicrophoneGranted = true))

        transition.effects shouldBe listOf(RestartVoiceStack, StartVoiceAnswering)
        transition.state.pauseReason shouldBe null
        transition.state.round.phase shouldBe VoiceAnswerPhase.WaitingForQuestion
    }

    @Test
    fun `a resume without the microphone ends the session and keeps the pause`() {
        val paused = voiceSession().after(PlaybackEngineUnavailable)

        val transition = reducer.reduce(paused, ResumeRequested(isMicrophoneGranted = false))

        transition.effects shouldBe listOf(EndForRevokedMicPermission)
        transition.state shouldBe paused
    }

    @Test
    fun `a resume with nothing paused does nothing`() {
        val playing = voiceSession()

        val transition = reducer.reduce(playing, ResumeRequested(isMicrophoneGranted = false))

        transition.effects.shouldBeEmpty()
        transition.state shouldBe playing
    }

    private companion object {
        const val FIXED_SEED = 42
        const val CARD_COUNT = 5
        const val CORRECT_PERCENT = 90
        const val PARTIAL_PERCENT = 60
        const val TRANSCRIPT = "state hoisting"
        const val RATIONALE = "Close."
        val WAV = byteArrayOf(1, 2, 3)
    }
}
