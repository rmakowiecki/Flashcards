package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.logging.FakeDomainLogger
import com.rossomak.flashcards.core.domain.model.AppPermission
import com.rossomak.flashcards.core.domain.model.CaptureEvent
import com.rossomak.flashcards.core.domain.model.CardProgressEntry
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.GradingFailureReason
import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.core.domain.model.PlaybackEvent
import com.rossomak.flashcards.core.domain.model.RatedSessionStateSnapshot
import com.rossomak.flashcards.core.domain.model.SessionPauseReason
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SpokenNotice
import com.rossomak.flashcards.core.domain.model.SubcategoryProgress
import com.rossomak.flashcards.core.domain.model.TransportCommand
import com.rossomak.flashcards.core.domain.model.TransportCommandType
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGrade
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGradingEvent
import com.rossomak.flashcards.core.domain.model.VoiceAnswerPauseReason
import com.rossomak.flashcards.core.domain.model.VoiceAnswerPhase
import com.rossomak.flashcards.core.domain.model.VoiceCaptureFailureReason
import com.rossomak.flashcards.core.domain.model.VoiceSettings
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.repository.FakeCardProgressRepository
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import com.rossomak.flashcards.core.domain.repository.FakePermissionGateway
import com.rossomak.flashcards.core.domain.repository.FakeStudyVoicePlaybackGateway
import com.rossomak.flashcards.core.domain.repository.FakeStudyVoicePlaybackGateway.Call
import com.rossomak.flashcards.core.domain.repository.FakeVoiceAnswerGradingRepository
import com.rossomak.flashcards.core.domain.repository.FakeVoiceCaptureGateway
import com.rossomak.flashcards.core.domain.repository.FakeXpConfigRepository
import com.rossomak.flashcards.core.domain.usecase.GetFlashcardsUseCase
import com.rossomak.flashcards.core.domain.usecase.GetSessionStartDataUseCase
import com.rossomak.flashcards.core.domain.usecase.GetSubcategoryProgressUseCase
import com.rossomak.flashcards.core.domain.usecase.GetXpConfigUseCase
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RatedStudySessionCoordinatorTest {

    private val flashcardRepository = FakeFlashcardRepository()
    private val cardProgressRepository = FakeCardProgressRepository()
    private val xpConfigRepository = FakeXpConfigRepository()
    private val getSessionStartData = GetSessionStartDataUseCase(
        GetFlashcardsUseCase(flashcardRepository),
        GetSubcategoryProgressUseCase(cardProgressRepository),
        GetXpConfigUseCase(xpConfigRepository),
    )
    private val playbackGateway = FakeStudyVoicePlaybackGateway()
    private val captureGateway = FakeVoiceCaptureGateway()
    private val gradingRepository = FakeVoiceAnswerGradingRepository()
    private val permissionGateway = FakePermissionGateway().apply {
        statuses.value = mapOf(AppPermission.RecordAudio to PermissionStatus.Granted)
    }
    private val logger = FakeDomainLogger()

    private val setup = RatedSessionSetup(
        categoryId = "android",
        categoryName = "Android",
        subcategoryIds = listOf(SUBCATEGORY_ID),
        subcategoryNames = listOf("Compose"),
        cardIds = listOf("card-1", "card-2", "card-3"),
        sessionTitle = "Compose",
        voiceSettings = VoiceSettings(speechRate = SPEECH_RATE, voiceId = VOICE_ID),
        voiceAnsweringEnabled = true,
        attemptsLimit = 3,
        partialRatingCardRequeueingEnabled = true,
    )

    private val events = mutableListOf<RatedSessionEvent>()

    private fun flashcard(id: String): Flashcard = Flashcard(
        id = id,
        subcategoryId = SUBCATEGORY_ID,
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

    private val RatedStudySessionCoordinator.runningSnapshot: RatedSessionStateSnapshot.Running
        get() = sessionState.value.shouldBeInstanceOf<RatedSessionStateSnapshot.Running>()

    private fun TestScope.startCoordinator(
        sessionSetup: RatedSessionSetup = setup,
        clock: Clock = Clock.fixed(START_INSTANT, ZoneOffset.UTC),
        runsLoad: Boolean = true,
    ): RatedStudySessionCoordinator {
        flashcardRepository.flashcardsBySubcategory.putIfAbsent(SUBCATEGORY_ID, Result.success(sessionSetup.cardIds.map(::flashcard)))
        val coordinator = RatedStudySessionCoordinator(
            getSessionStartData = getSessionStartData,
            playbackGateway = playbackGateway,
            captureGateway = captureGateway,
            gradingRepository = gradingRepository,
            permissionGateway = permissionGateway,
            reducer = RatedSessionReducer(Random(FIXED_SEED)),
            clock = clock,
            timeSource = testScheduler.timeSource,
            logger = logger,
        )
        backgroundScope.launch { coordinator.events.collect { events += it } }
        coordinator.start(backgroundScope, sessionSetup)
        if (runsLoad) runCurrent()
        return coordinator
    }

    private fun TestScope.openListening() {
        playbackGateway.finishQuestion()
        runCurrent()
    }

    private fun TestScope.captureAnswer() {
        openListening()
        captureGateway.emit(CaptureEvent.SpeechStarted)
        captureGateway.emit(CaptureEvent.SpeechEnded)
        captureGateway.emit(CaptureEvent.UtteranceCaptured(byteArrayOf(1)))
        runCurrent()
    }

    private fun TestScope.finishNotice() {
        playbackGateway.finishNotice()
        runCurrent()
    }

    private fun gradedFlow(gradePercent: Int = CORRECT_PERCENT) = flow {
        emit(VoiceAnswerGradingEvent.TranscriptReady(TRANSCRIPT))
        emit(VoiceAnswerGradingEvent.Graded(VoiceAnswerGrade(TRANSCRIPT, gradePercent, RATIONALE)))
    }

    // Start

    @Test
    fun `a voice-answering session starts the voice stack in question-only mode with the session's voice settings`() = runTest {
        startCoordinator()

        playbackGateway.calls shouldContainInOrder listOf(
            Call.Start(listOf("card-1", "card-2", "card-3"), 0, "Compose", true),
            Call.SetSpeechRate(SPEECH_RATE),
            Call.SetVoice(VOICE_ID),
            Call.SetQuestionOnlyMode(true),
        )
        captureGateway.isVoiceAnsweringStarted shouldBe true
    }

    @Test
    fun `a manual session never starts the voice stack`() = runTest {
        startCoordinator(setup.copy(voiceAnsweringEnabled = false))

        playbackGateway.startCalls.size shouldBe 0
        captureGateway.startVoiceAnsweringCount shouldBe 0
    }

    @Test
    fun `a revoked microphone at start stops voice and reports the revocation, without starting playback`() = runTest {
        permissionGateway.statuses.value = mapOf(AppPermission.RecordAudio to PermissionStatus.PermanentlyDenied)

        startCoordinator()

        playbackGateway.startCalls.size shouldBe 0
        captureGateway.isVoiceAnsweringStarted shouldBe false
        events shouldBe listOf(RatedSessionEvent.MicPermissionRevoked)
    }

    // Timers

    @Test
    fun `the silence timeout fires 8 seconds after the microphone opens`() = runTest {
        val coordinator = startCoordinator()
        openListening()

        advanceTimeBy(SILENCE_TIMEOUT - 1.milliseconds)
        coordinator.runningSnapshot.round.phase shouldBe VoiceAnswerPhase.Listening

        advanceTimeBy(2.milliseconds)
        coordinator.runningSnapshot.round.phase shouldBe VoiceAnswerPhase.SpeakingNotice
        playbackGateway.spokenNotices shouldBe listOf(SpokenNotice.SilenceSkip)
        events shouldBe listOf(RatedSessionEvent.VoiceAnswerSilenceSkip)
    }

    @Test
    fun `the silence timer starts only once the route is ready`() = runTest {
        val routeReady = CompletableDeferred<Unit>()
        captureGateway.routeReadyGate = routeReady
        val coordinator = startCoordinator()
        openListening()

        advanceTimeBy(ROUTE_READY_TIMEOUT - 1.milliseconds)
        captureGateway.isListening shouldBe false
        routeReady.complete(Unit)
        runCurrent()
        captureGateway.isListening shouldBe true
        advanceTimeBy(SILENCE_TIMEOUT - 1.milliseconds)

        coordinator.runningSnapshot.round.phase shouldBe VoiceAnswerPhase.Listening
    }

    @Test
    fun `a route that is not ready within 5 seconds pauses voice answering as a capture failure`() = runTest {
        captureGateway.routeReadyGate = CompletableDeferred()
        val coordinator = startCoordinator()
        openListening()

        advanceTimeBy(ROUTE_READY_TIMEOUT - 1.milliseconds)
        coordinator.runningSnapshot.voiceAnswerPauseReason shouldBe null

        advanceTimeBy(2.milliseconds)
        captureGateway.isListening shouldBe false
        coordinator.runningSnapshot.voiceAnswerPauseReason shouldBe VoiceAnswerPauseReason.CaptureFailed
        playbackGateway.spokenNotices shouldBe listOf(SpokenNotice.CaptureFailed)
        events shouldBe listOf(RatedSessionEvent.VoiceAnswerCaptureUnavailable)
    }

    @Test
    fun `speech cancels the silence timeout`() = runTest {
        val coordinator = startCoordinator()
        openListening()
        captureGateway.emit(CaptureEvent.SpeechStarted)
        runCurrent()

        advanceTimeBy(SILENCE_TIMEOUT * 2)

        coordinator.runningSnapshot.round.phase shouldBe VoiceAnswerPhase.SpeechDetected
        events shouldBe emptyList()
    }

    @Test
    fun `the grade is held until the transcript has shown for a second`() = runTest {
        gradingRepository.gradingFlow = flow {
            emit(VoiceAnswerGradingEvent.TranscriptReady(TRANSCRIPT))
            delay(200.milliseconds)
            emit(VoiceAnswerGradingEvent.Graded(VoiceAnswerGrade(TRANSCRIPT, CORRECT_PERCENT, RATIONALE)))
        }
        val coordinator = startCoordinator()
        captureAnswer()

        coordinator.runningSnapshot.round.transcript shouldBe TRANSCRIPT
        advanceTimeBy(MIN_TRANSCRIPT_DISPLAY - 1.milliseconds)
        coordinator.runningSnapshot.round.grade shouldBe null

        advanceTimeBy(2.milliseconds)
        coordinator.runningSnapshot.round.grade?.gradePercent shouldBe CORRECT_PERCENT
        playbackGateway.spokenNotices shouldBe listOf(SpokenNotice.Feedback(FlashcardAttemptRating.Correct, RATIONALE))
    }

    @Test
    fun `a grading failure is held like the grade, then logged and spoken`() = runTest {
        gradingRepository.gradingFlow = flow {
            emit(VoiceAnswerGradingEvent.TranscriptReady(TRANSCRIPT))
            emit(VoiceAnswerGradingEvent.Failed(GradingFailureReason.NoConnection))
        }
        val coordinator = startCoordinator()
        captureAnswer()

        advanceTimeBy(MIN_TRANSCRIPT_DISPLAY - 1.milliseconds)
        coordinator.runningSnapshot.round.gradingFailure shouldBe null

        advanceTimeBy(2.milliseconds)
        coordinator.runningSnapshot.round.gradingFailure shouldBe GradingFailureReason.NoConnection
        playbackGateway.spokenNotices shouldBe listOf(SpokenNotice.GradingFailed(GradingFailureReason.NoConnection))
        logger.entries.single().level shouldBe FakeDomainLogger.Level.Error
    }

    @Test
    fun `the grading call gets the round's card and never the recording's bytes in state`() = runTest {
        gradingRepository.gradingFlow = gradedFlow()
        startCoordinator()

        captureAnswer()

        gradingRepository.requests shouldBe listOf(FakeVoiceAnswerGradingRepository.Request("card-1", "q-card-1", "a-card-1"))
    }

    @Test
    fun `after the notice, the tail waits a second, then the queue syncs before the next question is read`() = runTest {
        gradingRepository.gradingFlow = gradedFlow()
        val coordinator = startCoordinator()
        captureAnswer()
        advanceTimeBy(MIN_TRANSCRIPT_DISPLAY)
        runCurrent()
        finishNotice()

        advanceTimeBy(NOTICE_TAIL - 1.milliseconds)
        playbackGateway.advanceAfterVoiceAnswerCount shouldBe 0
        coordinator.runningSnapshot.cards.firstOrNull()?.id shouldBe "card-1"

        advanceTimeBy(2.milliseconds)
        val syncIndex = playbackGateway.calls.indexOfLast { it is Call.UpdateQueue }
        val advanceIndex = playbackGateway.calls.indexOf(Call.AdvanceAfterVoiceAnswer)
        (syncIndex in 0 until advanceIndex) shouldBe true
        coordinator.runningSnapshot.cards.firstOrNull()?.id shouldBe "card-2"
    }

    @Test
    fun `a pause before the feedback tail ends waits after the feedback, and the next play reads it again`() = runTest {
        gradingRepository.gradingFlow = gradedFlow()
        val coordinator = startCoordinator()
        captureAnswer()
        advanceTimeBy(MIN_TRANSCRIPT_DISPLAY)
        runCurrent()
        finishNotice()
        coordinator.pause()
        runCurrent()

        advanceTimeBy(NOTICE_TAIL * 2)
        playbackGateway.advanceAfterVoiceAnswerCount shouldBe 0
        coordinator.runningSnapshot.isPausedAfterFeedback shouldBe true
        coordinator.runningSnapshot.cards.firstOrNull()?.id shouldBe "card-1"

        coordinator.play()

        playbackGateway.spokenNotices shouldBe List(2) { SpokenNotice.Feedback(FlashcardAttemptRating.Correct, RATIONALE) }
        playbackGateway.advanceAfterVoiceAnswerCount shouldBe 0
        playbackGateway.playCount shouldBe 0
    }

    @Test
    fun `a pause before a short notice's tail ends holds the session, and the next play advances`() = runTest {
        val coordinator = startCoordinator()
        openListening()
        advanceTimeBy(SILENCE_TIMEOUT)
        runCurrent()
        finishNotice()
        coordinator.pause()
        runCurrent()

        advanceTimeBy(NOTICE_TAIL * 2)
        playbackGateway.advanceAfterVoiceAnswerCount shouldBe 0
        coordinator.runningSnapshot.isPausedAtAdvancePoint shouldBe true

        coordinator.play()

        playbackGateway.advanceAfterVoiceAnswerCount shouldBe 1
        playbackGateway.playCount shouldBe 0
    }

    // Pause in every phase

    @Test
    fun `a pause while listening closes the microphone and stops the silence timer, counting nothing`() = runTest {
        val coordinator = startCoordinator()
        openListening()

        coordinator.pause()
        advanceTimeBy(SILENCE_TIMEOUT * 2)
        runCurrent()

        captureGateway.isListening shouldBe false
        playbackGateway.spokenNotices shouldBe emptyList()
        events shouldBe emptyList()
        with(coordinator.runningSnapshot) {
            round.phase shouldBe VoiceAnswerPhase.WaitingForQuestion
            cards.firstOrNull()?.id shouldBe "card-1"
        }
    }

    @Test
    fun `a grade that arrives while paused is recorded silently, and play reads its feedback`() = runTest {
        val gradeGate = CompletableDeferred<Unit>()
        gradingRepository.gradingFlow = flow {
            emit(VoiceAnswerGradingEvent.TranscriptReady(TRANSCRIPT))
            gradeGate.await()
            emit(VoiceAnswerGradingEvent.Graded(VoiceAnswerGrade(TRANSCRIPT, CORRECT_PERCENT, RATIONALE)))
        }
        val coordinator = startCoordinator()
        captureAnswer()
        playbackGateway.emitExternal(TransportCommand.Pause)
        runCurrent()

        gradeGate.complete(Unit)
        advanceTimeBy(MIN_TRANSCRIPT_DISPLAY)
        runCurrent()

        playbackGateway.spokenNotices shouldBe emptyList()
        coordinator.runningSnapshot.currentCardRatings shouldBe listOf(FlashcardAttemptRating.Correct)
        coordinator.runningSnapshot.isPausedAfterFeedback shouldBe true

        playbackGateway.emitExternal(TransportCommand.Play)
        runCurrent()

        playbackGateway.spokenNotices shouldBe listOf(SpokenNotice.Feedback(FlashcardAttemptRating.Correct, RATIONALE))
        playbackGateway.calls shouldContain Call.ResumeWithoutReading
        coordinator.runningSnapshot.currentCardRatings shouldBe listOf(FlashcardAttemptRating.Correct)
    }

    @Test
    fun `a pause during the feedback then an immediate play reads it again, and only its own end advances`() = runTest {
        gradingRepository.gradingFlow = gradedFlow()
        val coordinator = startCoordinator()
        captureAnswer()
        advanceTimeBy(MIN_TRANSCRIPT_DISPLAY)
        runCurrent()

        coordinator.pause()
        coordinator.play()
        runCurrent()
        advanceTimeBy(NOTICE_TAIL * 2)

        playbackGateway.calls shouldContain Call.StopFeedback
        playbackGateway.speakingNotices shouldBe listOf(SpokenNotice.Feedback(FlashcardAttemptRating.Correct, RATIONALE))
        playbackGateway.advanceAfterVoiceAnswerCount shouldBe 0
        coordinator.runningSnapshot.cards.firstOrNull()?.id shouldBe "card-1"

        finishNotice()
        advanceTimeBy(NOTICE_TAIL + 1.milliseconds)

        playbackGateway.advanceAfterVoiceAnswerCount shouldBe 1
        playbackGateway.calls.filterIsInstance<Call.UpdateQueue>().size shouldBe 1
    }

    @Test
    fun `skipping the feedback syncs the queue once and reads the next question`() = runTest {
        gradingRepository.gradingFlow = gradedFlow()
        val coordinator = startCoordinator()
        captureAnswer()
        advanceTimeBy(MIN_TRANSCRIPT_DISPLAY)
        runCurrent()

        coordinator.skipFeedback()
        advanceTimeBy(NOTICE_TAIL * 2)

        playbackGateway.calls.filterIsInstance<Call.UpdateQueue>().size shouldBe 1
        playbackGateway.advanceAfterVoiceAnswerCount shouldBe 1
        coordinator.runningSnapshot.cards.firstOrNull()?.id shouldBe "card-2"
        coordinator.runningSnapshot.currentCardRatings shouldBe emptyList()
    }

    @Test
    fun `external next during the feedback skips it and is reported`() = runTest {
        gradingRepository.gradingFlow = gradedFlow()
        val coordinator = startCoordinator()
        captureAnswer()
        advanceTimeBy(MIN_TRANSCRIPT_DISPLAY)
        runCurrent()

        playbackGateway.emitExternal(TransportCommand.Next)
        runCurrent()

        playbackGateway.calls shouldContain Call.StopFeedback
        coordinator.runningSnapshot.cards.firstOrNull()?.id shouldBe "card-2"
        events shouldContain RatedSessionEvent.ExternalTransportCommand(TransportCommand.Next)
    }

    // Available commands

    @Test
    fun `the system controls get the live commands and the session's own counter`() = runTest {
        startCoordinator()
        playbackGateway.availableCommandsUpdates.last() shouldBe setOf(
            TransportCommandType.Pause,
            TransportCommandType.Stop,
            TransportCommandType.Next,
            TransportCommandType.Previous,
            TransportCommandType.PreviousCard,
        )
        playbackGateway.sessionProgressUpdates.last() shouldBe FakeStudyVoicePlaybackGateway.SessionProgress(completedCount = 0, totalCount = 3)

        openListening()

        playbackGateway.availableCommandsUpdates.last() shouldBe setOf(TransportCommandType.Pause, TransportCommandType.Stop)
    }

    @Test
    fun `an external play while only pause is offered is ignored`() = runTest {
        startCoordinator()
        openListening()
        val callsBefore = playbackGateway.calls.size

        playbackGateway.emitExternal(TransportCommand.Play)
        runCurrent()

        playbackGateway.calls.size shouldBe callsBefore
        events shouldBe emptyList()
    }

    // The advance hold

    @Test
    fun `with a hold requested the session keeps playing and stops held at the auto-advance point`() = runTest {
        val coordinator = startCoordinator()

        coordinator.holdAdvance()
        playbackGateway.pauseCount shouldBe 0
        gradeAndReachAdvancePoint()

        with(coordinator.runningSnapshot) {
            isHeldAtAdvancePoint shouldBe true
            cards.firstOrNull()?.id shouldBe "card-1"
            completedCount shouldBe 1
        }
        playbackGateway.calls.filterIsInstance<Call.UpdateQueue>() shouldBe emptyList()
        playbackGateway.advanceAfterVoiceAnswerCount shouldBe 0
        playbackGateway.calls.last() shouldBe Call.Pause
    }

    @Test
    fun `releasing a held session syncs the queue, reads the next question, and a second release changes nothing`() = runTest {
        val coordinator = startCoordinator()
        coordinator.holdAdvance()
        gradeAndReachAdvancePoint()

        coordinator.releaseAdvance()
        runCurrent()

        coordinator.runningSnapshot.isHeldAtAdvancePoint shouldBe false
        coordinator.runningSnapshot.cards.firstOrNull()?.id shouldBe "card-2"
        playbackGateway.advanceAfterVoiceAnswerCount shouldBe 1

        coordinator.releaseAdvance()
        runCurrent()

        playbackGateway.advanceAfterVoiceAnswerCount shouldBe 1
    }

    @Test
    fun `releasing a hold that never stopped the session changes nothing`() = runTest {
        val coordinator = startCoordinator()
        coordinator.holdAdvance()
        val callCount = playbackGateway.calls.size

        coordinator.releaseAdvance()
        gradeAndReachAdvancePoint()

        playbackGateway.calls.drop(callCount).filterIsInstance<Call.Pause>() shouldBe emptyList()
        playbackGateway.advanceAfterVoiceAnswerCount shouldBe 1
    }

    @Test
    fun `an external pause at a hold turns it into a user pause, which the release does not resume`() = runTest {
        val coordinator = startCoordinator()
        coordinator.holdAdvance()
        gradeAndReachAdvancePoint()

        playbackGateway.emitExternal(TransportCommand.Pause)
        runCurrent()
        coordinator.releaseAdvance()
        runCurrent()

        coordinator.runningSnapshot.isHeldAtAdvancePoint shouldBe false
        coordinator.runningSnapshot.isPausedAtAdvancePoint shouldBe true
        coordinator.runningSnapshot.cards.firstOrNull()?.id shouldBe "card-1"
        playbackGateway.advanceAfterVoiceAnswerCount shouldBe 0

        coordinator.play()
        runCurrent()

        coordinator.runningSnapshot.cards.firstOrNull()?.id shouldBe "card-2"
        playbackGateway.advanceAfterVoiceAnswerCount shouldBe 1
    }

    @Test
    fun `external play at a hold moves on at once and is reported`() = runTest {
        externalCommandAtHoldMovesOn(TransportCommand.Play)
    }

    @Test
    fun `external next at a hold moves on at once and is reported`() = runTest {
        externalCommandAtHoldMovesOn(TransportCommand.Next)
    }

    private fun TestScope.externalCommandAtHoldMovesOn(command: TransportCommand) {
        val coordinator = startCoordinator()
        coordinator.holdAdvance()
        gradeAndReachAdvancePoint()

        playbackGateway.emitExternal(command)
        runCurrent()

        coordinator.runningSnapshot.isHeldAtAdvancePoint shouldBe false
        coordinator.runningSnapshot.cards.firstOrNull()?.id shouldBe "card-2"
        playbackGateway.advanceAfterVoiceAnswerCount shouldBe 1
        events shouldContain RatedSessionEvent.ExternalTransportCommand(command)
    }

    @Test
    fun `releasing a hold on the last card ends the session`() = runTest {
        val coordinator = startCoordinator(setup.copy(cardIds = listOf("card-1")))
        coordinator.holdAdvance()
        gradeAndReachAdvancePoint()
        events.filterIsInstance<RatedSessionEvent.SessionEnded>() shouldBe emptyList()

        coordinator.releaseAdvance()
        runCurrent()

        events.filterIsInstance<RatedSessionEvent.SessionEnded>().single().result.abandoned shouldBe false
    }

    @Test
    fun `a silence under a hold is counted, then holds, and the third one pauses voice answering instead`() = runTest {
        val coordinator = startCoordinator()

        // A dialog open over each of the three rounds.
        repeat(2) {
            coordinator.holdAdvance()
            openListening()
            advanceTimeBy(SILENCE_TIMEOUT)
            runCurrent()
            finishNotice()
            advanceTimeBy(NOTICE_TAIL)
            runCurrent()
            coordinator.runningSnapshot.isHeldAtAdvancePoint shouldBe true
            coordinator.releaseAdvance()
            runCurrent()
        }
        coordinator.holdAdvance()
        openListening()
        advanceTimeBy(SILENCE_TIMEOUT)
        runCurrent()
        finishNotice()
        advanceTimeBy(NOTICE_TAIL)
        runCurrent()

        coordinator.runningSnapshot.isHeldAtAdvancePoint shouldBe false
        coordinator.runningSnapshot.voiceAnswerPauseReason shouldBe VoiceAnswerPauseReason.Silence
        val advanceCount = playbackGateway.advanceAfterVoiceAnswerCount

        coordinator.releaseAdvance()
        coordinator.endTemporaryPause()
        runCurrent()

        coordinator.runningSnapshot.voiceAnswerPauseReason shouldBe VoiceAnswerPauseReason.Silence
        playbackGateway.advanceAfterVoiceAnswerCount shouldBe advanceCount
        captureGateway.isVoiceAnsweringStarted shouldBe false
    }

    @Test
    fun `a temporary pause plays again when it ends, unless the user paused meanwhile`() = runTest {
        val coordinator = startCoordinator()

        coordinator.pauseTemporarily()
        runCurrent()
        playbackGateway.calls.last() shouldBe Call.Pause
        coordinator.endTemporaryPause()
        runCurrent()
        playbackGateway.calls.last() shouldBe Call.Play

        coordinator.pauseTemporarily()
        runCurrent()
        coordinator.pause()
        runCurrent()
        coordinator.endTemporaryPause()
        runCurrent()

        playbackGateway.calls.last() shouldBe Call.Pause
    }

    @Test
    fun `the notice tail during a temporary pause waits at the advance point, and its end moves on`() = runTest {
        val coordinator = startCoordinator()
        gradingRepository.gradingFlow = gradedFlow()
        captureAnswer()
        advanceTimeBy(MIN_TRANSCRIPT_DISPLAY)
        runCurrent()
        coordinator.pauseTemporarily()
        finishNotice()
        advanceTimeBy(NOTICE_TAIL)
        runCurrent()
        coordinator.runningSnapshot.isPausedAtAdvancePoint shouldBe true

        coordinator.endTemporaryPause()
        runCurrent()

        playbackGateway.advanceAfterVoiceAnswerCount shouldBe 1
    }

    private fun TestScope.gradeAndReachAdvancePoint() {
        gradingRepository.gradingFlow = gradedFlow()
        captureAnswer()
        advanceTimeBy(MIN_TRANSCRIPT_DISPLAY)
        runCurrent()
        finishNotice()
        advanceTimeBy(NOTICE_TAIL)
        runCurrent()
    }

    // Resume

    @Test
    fun `a resume after three silences checks the microphone permission first`() = runTest {
        val coordinator = startCoordinator()
        repeat(3) {
            openListening()
            advanceTimeBy(SILENCE_TIMEOUT)
            runCurrent()
            finishNotice()
            advanceTimeBy(NOTICE_TAIL)
            runCurrent()
        }
        coordinator.runningSnapshot.voiceAnswerPauseReason shouldBe VoiceAnswerPauseReason.Silence
        permissionGateway.statuses.value = mapOf(AppPermission.RecordAudio to PermissionStatus.Denied)

        coordinator.resume()
        runCurrent()

        events.last() shouldBe RatedSessionEvent.MicPermissionRevoked
        captureGateway.isVoiceAnsweringStarted shouldBe false
    }

    @Test
    fun `a resume with the permission granted starts voice answering and plays`() = runTest {
        val coordinator = startCoordinator()
        captureGateway.emit(CaptureEvent.CaptureFailed(VoiceCaptureFailureReason.BluetoothMicUnavailable))
        runCurrent()
        coordinator.runningSnapshot.voiceAnswerPauseReason shouldBe VoiceAnswerPauseReason.CaptureFailed

        coordinator.resume()
        runCurrent()

        coordinator.runningSnapshot.voiceAnswerPauseReason shouldBe null
        captureGateway.isVoiceAnsweringStarted shouldBe true
        playbackGateway.calls.last() shouldBe Call.Play
    }

    @Test
    fun `an unavailable engine pauses the session, and a resume restarts the voice stack at the presented card`() = runTest {
        val coordinator = startCoordinator()
        coordinator.rate(FlashcardAttemptRating.Correct)
        playbackGateway.emit(PlaybackEvent.EngineUnavailable)
        runCurrent()

        coordinator.runningSnapshot.pauseReason shouldBe SessionPauseReason.VoiceEngineUnavailable
        events shouldContain RatedSessionEvent.VoicePlaybackUnavailable
        captureGateway.isVoiceAnsweringStarted shouldBe false
        playbackGateway.state.value.isActive shouldBe false
        logger.entries.single().level shouldBe FakeDomainLogger.Level.Warn

        coordinator.resume()
        runCurrent()

        coordinator.runningSnapshot.pauseReason shouldBe null
        playbackGateway.startCalls.last().cardIds.first() shouldBe "card-2"
        playbackGateway.startCalls.last().startIndex shouldBe 0
        captureGateway.isVoiceAnsweringStarted shouldBe true
        coordinator.runningSnapshot.round.phase shouldBe VoiceAnswerPhase.WaitingForQuestion
    }

    @Test
    fun `play during an engine pause resumes`() = runTest {
        val coordinator = startCoordinator()
        playbackGateway.emit(PlaybackEvent.EngineUnavailable)
        runCurrent()

        coordinator.play()
        runCurrent()

        playbackGateway.startCalls.size shouldBe 2
    }

    @Test
    fun `a double resume during an engine pause restarts the voice stack once`() = runTest {
        val coordinator = startCoordinator()
        playbackGateway.emit(PlaybackEvent.EngineUnavailable)
        runCurrent()
        val stopsBeforeResume = playbackGateway.stopCount

        coordinator.resume()
        coordinator.resume()
        runCurrent()

        playbackGateway.startCalls.size shouldBe 2
        playbackGateway.stopCount shouldBe stopsBeforeResume + 1
        coordinator.runningSnapshot.pauseReason shouldBe null
    }

    // External commands

    @Test
    fun `external play and pause reach the player through the session`() = runTest {
        startCoordinator()
        playbackGateway.emitExternal(TransportCommand.Pause)
        runCurrent()
        playbackGateway.state.value.isPlaying shouldBe false

        playbackGateway.emitExternal(TransportCommand.Play)
        runCurrent()

        playbackGateway.calls shouldContainInOrder listOf(Call.Pause, Call.Play)
    }

    @Test
    fun `an external stop only pauses`() = runTest {
        startCoordinator()

        playbackGateway.emitExternal(TransportCommand.Stop)
        runCurrent()

        playbackGateway.calls.last() shouldBe Call.Pause
        playbackGateway.stopCount shouldBe 0
    }

    @Test
    fun `external next skips the presented card`() = runTest {
        val coordinator = startCoordinator()

        playbackGateway.emitExternal(TransportCommand.Next)
        runCurrent()

        coordinator.runningSnapshot.cards.firstOrNull()?.id shouldBe "card-2"
        playbackGateway.calls.last() shouldBe Call.AdvanceAfterVoiceAnswer
    }

    @Test
    fun `external previous and previous card restart the question`() = runTest {
        startCoordinator()

        playbackGateway.emitExternal(TransportCommand.Previous)
        playbackGateway.emitExternal(TransportCommand.PreviousCard)
        runCurrent()

        playbackGateway.restartCurrentCardCount shouldBe 2
    }

    @Test
    fun `an external jump is ignored`() = runTest {
        startCoordinator()
        val callsBefore = playbackGateway.calls.size

        playbackGateway.emitExternal(TransportCommand.JumpTo(2))
        runCurrent()

        playbackGateway.calls.size shouldBe callsBefore
    }

    @Test
    fun `external next while listening is ignored`() = runTest {
        val coordinator = startCoordinator()
        openListening()

        playbackGateway.emitExternal(TransportCommand.Next)
        runCurrent()

        coordinator.runningSnapshot.cards.firstOrNull()?.id shouldBe "card-1"
        playbackGateway.calls shouldNotContain Call.AdvanceAfterVoiceAnswer
        events.filterIsInstance<RatedSessionEvent.ExternalTransportCommand>() shouldBe emptyList()
    }

    // Result

    @Test
    fun `leaving before the cards load seals an empty abandoned result with zero duration`() = runTest {
        val coordinator = startCoordinator(setup.copy(voiceAnsweringEnabled = false), runsLoad = false)

        coordinator.end(abandoned = true)
        coordinator.end(abandoned = false)
        runCurrent()

        val ended = events.single().shouldBeInstanceOf<RatedSessionEvent.SessionEnded>()
        val result = ended.result.shouldBeInstanceOf<SessionResult.Rated>()
        result.abandoned shouldBe true
        result.cardResults shouldBe emptyList()
        result.durationSeconds shouldBe 0
        result.xpConfig shouldBe XpConfig()
    }

    @Test
    fun `the duration runs from the first card shown to the end`() = runTest {
        val clock = MutableClock(START_INSTANT)
        val coordinator = startCoordinator(setup.copy(voiceAnsweringEnabled = false), clock)
        clock.instant = START_INSTANT.plusSeconds(ELAPSED_SECONDS)

        coordinator.end(abandoned = true)
        runCurrent()

        val result = events.filterIsInstance<RatedSessionEvent.SessionEnded>().single().result
        result.startedAt shouldBe START_INSTANT
        result.durationSeconds shouldBe ELAPSED_SECONDS.toInt()
    }

    @Test
    fun `the prior progress read is recorded, and a card with no entry is new`() = runTest {
        cardProgressRepository.seed(
            SubcategoryProgress(
                subcategoryId = SUBCATEGORY_ID,
                categoryId = "android",
                cards = mapOf("card-2" to CardProgressEntry(state = FlashcardStudyProgressState.Mastered, firstStudiedAt = START_INSTANT, masteredAt = START_INSTANT)),
            ),
        )

        val coordinator = startCoordinator(setup.copy(voiceAnsweringEnabled = false))

        coordinator.priorProgressByCardId.keys shouldBe setOf("card-2")
        coordinator.priorProgressByCardId.keys shouldNotContain "card-1"
    }

    @Test
    fun `a failed progress read leaves the prior progress empty and still runs the session`() = runTest {
        cardProgressRepository.resultToReturn = Result.failure(IllegalStateException("offline"))

        val coordinator = startCoordinator(setup.copy(voiceAnsweringEnabled = false))

        coordinator.priorProgressByCardId shouldBe emptyMap()
        coordinator.runningSnapshot.cards.size shouldBe 3
    }

    @Test
    fun `stop stops the whole voice stack synchronously`() = runTest {
        val coordinator = startCoordinator()

        coordinator.stop()

        playbackGateway.stopCount shouldBe 1
        captureGateway.isVoiceAnsweringStarted shouldBe false
    }

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId?): Clock = this

        override fun instant(): Instant = instant
    }

    private companion object {
        const val FIXED_SEED = 42
        const val SUBCATEGORY_ID = "android-compose"
        const val SPEECH_RATE = 1.25f
        const val VOICE_ID = "en-us-x-voice"
        const val CORRECT_PERCENT = 90
        const val TRANSCRIPT = "remember keeps state"
        const val RATIONALE = "Right."
        const val ELAPSED_SECONDS = 42L
        val START_INSTANT: Instant = Instant.parse("2026-09-06T10:00:00Z")
    }
}
