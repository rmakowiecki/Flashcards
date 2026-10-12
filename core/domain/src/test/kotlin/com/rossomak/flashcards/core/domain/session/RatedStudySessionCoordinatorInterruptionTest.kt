package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.AppPermission
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.AudioModeChanged
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.FocusChanged
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.MicSilenced
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.MicUnsilenced
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.OutputDisconnected
import com.rossomak.flashcards.core.domain.model.AudioMode
import com.rossomak.flashcards.core.domain.model.CaptureEvent
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.model.FocusChange
import com.rossomak.flashcards.core.domain.model.InterruptionEpisode
import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.core.domain.model.RatedSessionStateSnapshot
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import com.rossomak.flashcards.core.domain.model.SpokenNotice
import com.rossomak.flashcards.core.domain.model.TransportCommand
import com.rossomak.flashcards.core.domain.model.TransportCommandType
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGrade
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGradingEvent
import com.rossomak.flashcards.core.domain.model.VoiceAnswerPhase
import com.rossomak.flashcards.core.domain.model.VoiceSettings
import com.rossomak.flashcards.core.domain.repository.FakeAudioInterruptionGateway
import com.rossomak.flashcards.core.domain.repository.FakeCardProgressRepository
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import com.rossomak.flashcards.core.domain.repository.FakePermissionGateway
import com.rossomak.flashcards.core.domain.repository.FakeStudyVoicePlaybackGateway
import com.rossomak.flashcards.core.domain.repository.FakeStudyVoicePlaybackGateway.Call
import com.rossomak.flashcards.core.domain.repository.FakeVoiceAnswerGradingRepository
import com.rossomak.flashcards.core.domain.repository.FakeVoiceCaptureGateway
import com.rossomak.flashcards.core.domain.usecase.GetFlashcardsUseCase
import com.rossomak.flashcards.core.domain.usecase.GetSessionStartDataUseCase
import com.rossomak.flashcards.core.domain.usecase.GetSubcategoryProgressDetailsUseCase
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The Rated coordinator under audio interruptions, in virtual time on the test fixtures. */
@OptIn(ExperimentalCoroutinesApi::class)
class RatedStudySessionCoordinatorInterruptionTest {

    private val flashcardRepository = FakeFlashcardRepository()
    private val getSessionStartData = GetSessionStartDataUseCase(
        GetFlashcardsUseCase(flashcardRepository),
        GetSubcategoryProgressDetailsUseCase(FakeCardProgressRepository()),
    )
    private val playbackGateway = FakeStudyVoicePlaybackGateway()
    private val captureGateway = FakeVoiceCaptureGateway()
    private val interruptionGateway = FakeAudioInterruptionGateway()
    private val gradingRepository = FakeVoiceAnswerGradingRepository()
    private val permissionGateway = FakePermissionGateway().apply {
        statuses.value = mapOf(AppPermission.RecordAudio to PermissionStatus.Granted)
    }
    private val events = mutableListOf<RatedSessionEvent>()

    private val setup = RatedSessionSetup(
        categoryId = "android",
        categoryName = "Android",
        subcategoryIds = listOf(SUBCATEGORY_ID),
        subcategoryNames = listOf("Compose"),
        cardIds = listOf("card-1", "card-2", "card-3"),
        sessionTitle = "Compose",
        voiceSettings = VoiceSettings(speechRate = 1f, voiceId = "voice"),
        voiceAnsweringEnabled = true,
        attemptsLimit = 3,
        partialRatingCardRequeueingEnabled = true,
        sourceType = SingleSubcategory,
    )

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

    private fun TestScope.startCoordinator(): RatedStudySessionCoordinator {
        flashcardRepository.flashcardsBySubcategory.putIfAbsent(SUBCATEGORY_ID, Result.success(setup.cardIds.map(::flashcard)))
        val coordinator = RatedStudySessionCoordinator(
            getSessionStartData = getSessionStartData,
            playbackGateway = playbackGateway,
            captureGateway = captureGateway,
            interruptionGateway = interruptionGateway,
            gradingRepository = gradingRepository,
            permissionGateway = permissionGateway,
            reducer = RatedSessionReducer(Random(FIXED_SEED), mockk(relaxed = true)),
            clock = Clock.fixed(START_INSTANT, ZoneOffset.UTC),
            timeSource = testScheduler.timeSource,
            logger = mockk(relaxed = true),
        )
        backgroundScope.launch { coordinator.events.collect { events += it } }
        coordinator.start(backgroundScope, setup)
        runCurrent()
        return coordinator
    }

    private fun TestScope.openListening() {
        playbackGateway.finishQuestion()
        runCurrent()
    }

    private fun TestScope.emit(signal: AudioEnvironmentSignal) {
        interruptionGateway.emit(signal)
        runCurrent()
    }

    private fun TestScope.emit(change: FocusChange) = emit(FocusChanged(change))

    private fun TestScope.captureAnswer() {
        openListening()
        captureGateway.emit(CaptureEvent.SpeechStarted)
        captureGateway.emit(CaptureEvent.SpeechEnded)
        captureGateway.emit(CaptureEvent.UtteranceCaptured(byteArrayOf(1)))
        runCurrent()
    }

    // Interruptions while listening

    @Test
    fun `an interruption while listening closes the microphone and counts no silence`() = runTest {
        val coordinator = startCoordinator()
        openListening()

        emit(FocusChange.LossTransient)
        advanceTimeBy(SILENCE_TIMEOUT * 2)
        runCurrent()

        captureGateway.isListening shouldBe false
        playbackGateway.pauseCount shouldBe 1
        playbackGateway.spokenNotices shouldBe emptyList()
        events shouldBe emptyList()
        coordinator.runningSnapshot.round.phase shouldBe VoiceAnswerPhase.WaitingForQuestion
    }

    @Test
    fun `the auto-resume reads the same card's question again and listens for it`() = runTest {
        val coordinator = startCoordinator()
        openListening()
        emit(FocusChange.LossTransient)

        advanceTimeBy(10.seconds)
        emit(FocusChange.Gain)
        val playsAfterResume = playbackGateway.playCount
        playbackGateway.finishQuestion()
        runCurrent()

        playsAfterResume shouldBe 1
        captureGateway.startListeningCount shouldBe 2
        coordinator.runningSnapshot.round.phase shouldBe VoiceAnswerPhase.Listening
        coordinator.runningSnapshot.cards.first().id shouldBe "card-1"
    }

    @Test
    fun `a gain at 59 seconds resumes`() = runTest {
        startCoordinator()
        openListening()
        emit(FocusChange.LossTransient)
        advanceTimeBy(59.seconds)
        emit(FocusChange.Gain)
        playbackGateway.playCount shouldBe 1
    }

    @Test
    fun `a gain at 61 seconds does not resume`() = runTest {
        startCoordinator()
        openListening()
        emit(FocusChange.LossTransient)
        advanceTimeBy(61.seconds)

        emit(FocusChange.Gain)

        playbackGateway.playCount shouldBe 0
    }

    @Test
    fun `an alarm ringing past the window never resumes the session, and it stays paused`() = runTest {
        val coordinator = startCoordinator()
        emit(FocusChange.LossTransient)

        advanceTimeBy(FIVE_MINUTES)
        emit(FocusChange.Gain)

        playbackGateway.playCount shouldBe 0
        coordinator.runningSnapshot.availableTransportCommands shouldContain TransportCommandType.Play
    }

    @Test
    fun `a play during the transient loss resumes at once and the later gain does nothing`() = runTest {
        val coordinator = startCoordinator()
        openListening()
        emit(FocusChange.LossTransient)

        coordinator.play()
        runCurrent()
        emit(FocusChange.Gain)

        playbackGateway.playCount shouldBe 1
    }

    // Blips and the capture gate

    @Test
    fun `a blip while listening gates the capture, stops the silence timer and reopens after the tail with a full timer`() = runTest {
        val coordinator = startCoordinator()
        openListening()

        emit(FocusChange.LossCanDuck)
        captureGateway.captureGateHistory shouldBe listOf(true)
        advanceTimeBy(SILENCE_TIMEOUT * 2)
        coordinator.runningSnapshot.round.phase shouldBe VoiceAnswerPhase.Listening

        emit(FocusChange.Gain)
        advanceTimeBy(GATE_TAIL - 1.milliseconds)
        captureGateway.captureGateHistory shouldBe listOf(true)

        advanceTimeBy(2.milliseconds)
        captureGateway.captureGateHistory shouldBe listOf(true, false)
        advanceTimeBy(SILENCE_TIMEOUT - 1.milliseconds)
        coordinator.runningSnapshot.round.phase shouldBe VoiceAnswerPhase.Listening
        advanceTimeBy(2.milliseconds)
        coordinator.runningSnapshot.round.phase shouldBe VoiceAnswerPhase.SpeakingNotice
    }

    @Test
    fun `a blip while listening keeps the round and the microphone`() = runTest {
        startCoordinator()
        openListening()

        emit(FocusChange.LossCanDuck)
        advanceTimeBy(700.milliseconds)
        emit(FocusChange.Gain)

        captureGateway.isListening shouldBe true
        playbackGateway.pauseCount shouldBe 0
    }

    @Test
    fun `a blip that gates a listening window for a minute cancels the round without an auto-resume`() = runTest {
        val coordinator = startCoordinator()
        openListening()
        emit(FocusChange.LossCanDuck)

        advanceTimeBy(59.seconds)
        coordinator.runningSnapshot.round.phase shouldBe VoiceAnswerPhase.Listening
        advanceTimeBy(2.seconds)

        captureGateway.isListening shouldBe false
        playbackGateway.pauseCount shouldBe 1
        emit(FocusChange.Gain)
        playbackGateway.playCount shouldBe 0
    }

    @Test
    fun `a blip that outlasts the threshold while the question is read pauses it, and the gain resumes it`() = runTest {
        startCoordinator()

        emit(FocusChange.LossCanDuck)
        advanceTimeBy(BLIP_THRESHOLD - 1.milliseconds)
        playbackGateway.pauseCount shouldBe 0
        advanceTimeBy(2.milliseconds)
        playbackGateway.pauseCount shouldBe 1

        emit(FocusChange.Gain)
        playbackGateway.playCount shouldBe 1
    }

    @Test
    fun `a blip while the question is read that ends in time changes nothing`() = runTest {
        startCoordinator()

        emit(FocusChange.LossCanDuck)
        advanceTimeBy(700.milliseconds)
        emit(FocusChange.Gain)
        advanceTimeBy(10.seconds)

        playbackGateway.pauseCount shouldBe 0
        playbackGateway.playCount shouldBe 0
    }

    // Grading and the feedback

    @Test
    fun `a grade that arrives during an interruption is recorded, and its feedback waits for the resume`() = runTest {
        val gradeGate = CompletableDeferred<Unit>()
        gradingRepository.gradingFlow = flow {
            emit(VoiceAnswerGradingEvent.TranscriptReady(TRANSCRIPT))
            gradeGate.await()
            emit(VoiceAnswerGradingEvent.Graded(VoiceAnswerGrade(TRANSCRIPT, CORRECT_PERCENT, RATIONALE)))
        }
        val coordinator = startCoordinator()
        captureAnswer()
        emit(FocusChange.LossTransient)

        gradeGate.complete(Unit)
        advanceTimeBy(MIN_TRANSCRIPT_DISPLAY)
        runCurrent()

        playbackGateway.spokenNotices shouldBe emptyList()
        coordinator.runningSnapshot.currentCardRatings shouldBe listOf(FlashcardAttemptRating.Correct)
        coordinator.runningSnapshot.isPausedAfterFeedback shouldBe true

        emit(FocusChange.Gain)

        playbackGateway.spokenNotices shouldBe listOf(SpokenNotice.Feedback(FlashcardAttemptRating.Correct, RATIONALE))
    }

    @Test
    fun `an interruption during the feedback cuts it without a finish, and the resume reads it again`() = runTest {
        gradingRepository.gradingFlow = flow {
            emit(VoiceAnswerGradingEvent.TranscriptReady(TRANSCRIPT))
            emit(VoiceAnswerGradingEvent.Graded(VoiceAnswerGrade(TRANSCRIPT, CORRECT_PERCENT, RATIONALE)))
        }
        val coordinator = startCoordinator()
        captureAnswer()
        advanceTimeBy(MIN_TRANSCRIPT_DISPLAY)
        runCurrent()
        playbackGateway.spokenNotices.size shouldBe 1

        emit(FocusChange.LossTransient)

        playbackGateway.calls shouldContain Call.StopFeedback
        coordinator.runningSnapshot.isPausedAfterFeedback shouldBe true

        emit(FocusChange.Gain)

        playbackGateway.spokenNotices.size shouldBe 2
        coordinator.runningSnapshot.isPausedAfterFeedback shouldBe false
    }

    @Test
    fun `a short notice speaking when an interruption starts plays to the end, then holds until the resume`() = runTest {
        val coordinator = startCoordinator()
        openListening()
        advanceTimeBy(SILENCE_TIMEOUT + 1.milliseconds)
        playbackGateway.spokenNotices shouldBe listOf(SpokenNotice.SilenceSkip)

        emit(FocusChange.LossTransient)
        playbackGateway.calls shouldNotContain Call.StopFeedback
        playbackGateway.finishNotice()
        advanceTimeBy(NOTICE_TAIL + 1.milliseconds)
        runCurrent()

        coordinator.runningSnapshot.round.phase shouldBe VoiceAnswerPhase.WaitingForQuestion
        val questionsBeforeResume = playbackGateway.presentedQuestions.size

        emit(FocusChange.Gain)

        playbackGateway.presentedQuestions.size shouldBe questionsBeforeResume + 1
        playbackGateway.playCount shouldBe 1
        coordinator.runningSnapshot.cards.first().id shouldBe "card-2"
    }

    // Calls, takeovers and headsets

    @Test
    fun `a picked-up call never resumes on its own, and play after it resumes voice answering`() = runTest {
        val coordinator = startCoordinator()
        openListening()

        emit(AudioModeChanged(AudioMode.Ringtone))
        emit(FocusChange.LossTransient)
        emit(AudioModeChanged(AudioMode.InCall))
        advanceTimeBy(5.seconds)
        emit(AudioModeChanged(AudioMode.Normal))
        emit(FocusChange.Gain)

        playbackGateway.playCount shouldBe 0
        coordinator.play()
        runCurrent()
        playbackGateway.playCount shouldBe 1
        coordinator.runningSnapshot.voiceAnswerPauseReason shouldBe null
    }

    @Test
    fun `a declined ring resumes on its own`() = runTest {
        startCoordinator()
        emit(AudioModeChanged(AudioMode.Ringtone))
        emit(FocusChange.LossTransient)
        advanceTimeBy(4.seconds)
        emit(AudioModeChanged(AudioMode.Normal))

        emit(FocusChange.Gain)

        playbackGateway.playCount shouldBe 1
    }

    @Test
    fun `a call that starts tells the user once`() = runTest {
        startCoordinator()
        emit(AudioModeChanged(AudioMode.InCall))
        emit(FocusChange.LossTransient)

        events.count { it == RatedSessionEvent.PlayIgnoredDuringCall } shouldBe 1
    }

    @Test
    fun `a play from the app during a call starts nothing and tells the user again`() = runTest {
        val coordinator = startCoordinator()
        emit(AudioModeChanged(AudioMode.InCall))
        emit(FocusChange.LossTransient)

        coordinator.play()
        runCurrent()

        playbackGateway.playCount shouldBe 0
        events.count { it == RatedSessionEvent.PlayIgnoredDuringCall } shouldBe 2
    }

    @Test
    fun `a play from a headset during a call is dropped without a message`() = runTest {
        startCoordinator()
        emit(AudioModeChanged(AudioMode.InCall))
        emit(FocusChange.LossTransient)

        playbackGateway.emitExternal(TransportCommand.Play)
        runCurrent()

        playbackGateway.playCount shouldBe 0
        events.count { it == RatedSessionEvent.PlayIgnoredDuringCall } shouldBe 1
    }

    @Test
    fun `no transport command is offered during a call`() = runTest {
        val coordinator = startCoordinator()
        emit(AudioModeChanged(AudioMode.InCall))
        emit(FocusChange.LossTransient)

        coordinator.runningSnapshot.availableTransportCommands shouldBe emptySet()
        playbackGateway.availableCommandsUpdates.last() shouldBe emptySet()
    }

    @Test
    fun `a session opened while a call rings is paused, told once, and offers nothing`() = runTest {
        interruptionGateway.emit(AudioModeChanged(AudioMode.Ringtone))
        val coordinator = startCoordinator()

        playbackGateway.pauseCount shouldBeGreaterThan 0
        playbackGateway.playCount shouldBe 0
        events.count { it == RatedSessionEvent.PlayIgnoredDuringCall } shouldBe 1
        coordinator.runningSnapshot.availableTransportCommands shouldBe emptySet()

        emit(AudioModeChanged(AudioMode.Normal))
        playbackGateway.playCount shouldBe 0
        coordinator.runningSnapshot.availableTransportCommands.shouldNotBeEmpty()
    }

    @Test
    fun `a takeover leaves the session paused until the user plays`() = runTest {
        val coordinator = startCoordinator()
        openListening()

        emit(FocusChange.Loss)
        advanceTimeBy(FIVE_MINUTES)
        emit(FocusChange.Gain)

        playbackGateway.playCount shouldBe 0
        captureGateway.isVoiceAnsweringStarted shouldBe true
        coordinator.play()
        runCurrent()
        playbackGateway.playCount shouldBe 1
    }

    @Test
    fun `a headset disconnect while listening cancels the round and nothing ever resumes on the speaker`() = runTest {
        val coordinator = startCoordinator()
        openListening()

        emit(OutputDisconnected)
        emit(FocusChange.LossTransient)
        advanceTimeBy(5.seconds)
        emit(FocusChange.Gain)

        captureGateway.isListening shouldBe false
        playbackGateway.pauseCount shouldBe 1
        playbackGateway.playCount shouldBe 0
        coordinator.runningSnapshot.round.phase shouldBe VoiceAnswerPhase.WaitingForQuestion
    }

    @Test
    fun `a silenced microphone overlapping a transient loss resumes only once it is back`() = runTest {
        startCoordinator()
        openListening()
        emit(FocusChange.LossTransient)
        emit(MicSilenced)

        advanceTimeBy(5.seconds)
        emit(FocusChange.Gain)
        playbackGateway.playCount shouldBe 0

        emit(MicUnsilenced)
        playbackGateway.playCount shouldBe 1
    }

    private companion object {
        const val FIXED_SEED = 42
        const val SUBCATEGORY_ID = "android-compose"
        const val CORRECT_PERCENT = 90
        const val TRANSCRIPT = "remember keeps state"
        const val RATIONALE = "Right."
        val FIVE_MINUTES = 300.seconds
        val START_INSTANT: Instant = Instant.parse("2026-09-06T10:00:00Z")
        val GATE_TAIL = InterruptionEpisode.CAPTURE_GATE_TAIL
        val BLIP_THRESHOLD = InterruptionEpisode.BLIP_SPEECH_THRESHOLD
    }
}
