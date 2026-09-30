package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.AudioModeChanged
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.FocusChanged
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.OutputDisconnected
import com.rossomak.flashcards.core.domain.model.AudioMode
import com.rossomak.flashcards.core.domain.model.FastPauseReason
import com.rossomak.flashcards.core.domain.model.FastSessionStateSnapshot
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FocusChange
import com.rossomak.flashcards.core.domain.model.InterruptionEpisode
import com.rossomak.flashcards.core.domain.model.TransportCommand
import com.rossomak.flashcards.core.domain.model.VoiceSettings
import com.rossomak.flashcards.core.domain.repository.FakeAudioInterruptionGateway
import com.rossomak.flashcards.core.domain.repository.FakeCardProgressRepository
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import com.rossomak.flashcards.core.domain.repository.FakeStudyVoicePlaybackGateway
import com.rossomak.flashcards.core.domain.usecase.GetFlashcardsUseCase
import com.rossomak.flashcards.core.domain.usecase.GetSessionStartDataUseCase
import com.rossomak.flashcards.core.domain.usecase.GetSubcategoryProgressUseCase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The Fast coordinator under audio interruptions, in virtual time on the test fixtures. */
@OptIn(ExperimentalCoroutinesApi::class)
class FastStudySessionCoordinatorInterruptionTest {

    private val flashcardRepository = FakeFlashcardRepository()
    private val getSessionStartData = GetSessionStartDataUseCase(
        GetFlashcardsUseCase(flashcardRepository),
        GetSubcategoryProgressUseCase(FakeCardProgressRepository()),
    )
    private val playbackGateway = FakeStudyVoicePlaybackGateway()
    private val interruptionGateway = FakeAudioInterruptionGateway()
    private val events = mutableListOf<FastSessionEvent>()

    private val setup = FastSessionSetup(
        categoryId = "android",
        categoryName = "Android",
        subcategoryIds = listOf(SUBCATEGORY_ID),
        subcategoryNames = listOf("Compose"),
        cardIds = listOf("card-1", "card-2", "card-3"),
        sessionTitle = "Compose",
        voiceSettings = VoiceSettings(speechRate = 1f),
        readAloudEnabled = true,
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

    private val FastStudySessionCoordinator.runningSnapshot: FastSessionStateSnapshot.Running
        get() = sessionState.value.shouldBeInstanceOf<FastSessionStateSnapshot.Running>()

    private fun TestScope.startCoordinator(): FastStudySessionCoordinator {
        flashcardRepository.flashcardsBySubcategory.putIfAbsent(SUBCATEGORY_ID, Result.success(setup.cardIds.map(::flashcard)))
        val coordinator = FastStudySessionCoordinator(
            getSessionStartData = getSessionStartData,
            playbackGateway = playbackGateway,
            interruptionGateway = interruptionGateway,
            reducer = FastSessionReducer(mockk(relaxed = true)),
            clock = Clock.fixed(START_INSTANT, ZoneOffset.UTC),
            timeSource = testScheduler.timeSource,
            logger = mockk(relaxed = true),
        )
        backgroundScope.launch { coordinator.events.collect { events += it } }
        coordinator.start(backgroundScope, setup)
        runCurrent()
        return coordinator
    }

    private fun TestScope.emit(signal: AudioEnvironmentSignal) {
        interruptionGateway.emit(signal)
        runCurrent()
    }

    private fun TestScope.emit(change: FocusChange) = emit(FocusChanged(change))

    private fun TestScope.finishQuestion() {
        playbackGateway.finishQuestion()
        runCurrent()
    }

    private fun TestScope.reachAnswer() {
        finishQuestion()
        advanceTimeBy(QUESTION_TO_ANSWER_PAUSE)
        runCurrent()
    }

    private fun TestScope.finishAnswer() {
        playbackGateway.finishAnswer()
        runCurrent()
    }

    // Each step

    @Test
    fun `an interruption while a question is read pauses it, and the auto-resume reads it again`() = runTest {
        val coordinator = startCoordinator()

        emit(FocusChange.LossTransient)
        coordinator.runningSnapshot.pauseReason shouldBe FastPauseReason.User
        playbackGateway.pauseCount shouldBe 1
        advanceTimeBy(10.seconds)
        emit(FocusChange.Gain)

        playbackGateway.playCount shouldBe 1
        coordinator.runningSnapshot.pauseReason shouldBe null
    }

    @Test
    fun `an interruption while an answer is read resumes by reading it again`() = runTest {
        val coordinator = startCoordinator()
        reachAnswer()

        emit(FocusChange.LossTransient)
        emit(FocusChange.Gain)

        playbackGateway.playCount shouldBe 1
        coordinator.runningSnapshot.isAnswerRevealed shouldBe true
    }

    @Test
    fun `an interruption in the question pause cancels the pause, and the resume goes to the answer`() = runTest {
        startCoordinator()
        finishQuestion()

        emit(FocusChange.LossTransient)
        advanceTimeBy(QUESTION_TO_ANSWER_PAUSE * 2)
        runCurrent()
        playbackGateway.presentedAnswers shouldBe emptyList()

        emit(FocusChange.Gain)

        playbackGateway.presentedAnswers shouldBe listOf(0)
        playbackGateway.playCount shouldBe 1
    }

    @Test
    fun `an interruption in the advance pause cancels the pause, and the resume goes to the next card`() = runTest {
        val coordinator = startCoordinator()
        reachAnswer()
        finishAnswer()

        emit(FocusChange.LossTransient)
        advanceTimeBy(ANSWER_TO_NEXT_PAUSE * 2)
        runCurrent()
        coordinator.runningSnapshot.currentIndex shouldBe 0

        emit(FocusChange.Gain)

        coordinator.runningSnapshot.currentIndex shouldBe 1
        playbackGateway.playCount shouldBe 1
    }

    @Test
    fun `a gain at 61 seconds does not resume`() = runTest {
        startCoordinator()
        emit(FocusChange.LossTransient)
        advanceTimeBy(61.seconds)

        emit(FocusChange.Gain)

        playbackGateway.playCount shouldBe 0
    }

    @Test
    fun `a gain at 59 seconds resumes`() = runTest {
        startCoordinator()
        emit(FocusChange.LossTransient)
        advanceTimeBy(59.seconds)

        emit(FocusChange.Gain)

        playbackGateway.playCount shouldBe 1
    }

    @Test
    fun `a blip keeps the question going, and past the threshold it pauses`() = runTest {
        startCoordinator()

        emit(FocusChange.LossCanDuck)
        advanceTimeBy(InterruptionEpisode.BLIP_SPEECH_THRESHOLD - 1.milliseconds)
        playbackGateway.pauseCount shouldBe 0
        advanceTimeBy(2.milliseconds)
        playbackGateway.pauseCount shouldBe 1

        emit(FocusChange.Gain)
        playbackGateway.playCount shouldBe 1
    }

    @Test
    fun `a short blip changes nothing`() = runTest {
        startCoordinator()

        emit(FocusChange.LossCanDuck)
        advanceTimeBy(700.milliseconds)
        emit(FocusChange.Gain)
        advanceTimeBy(5.seconds)

        playbackGateway.pauseCount shouldBe 0
        playbackGateway.playCount shouldBe 0
    }

    // Headsets, calls, takeovers and dialogs

    @Test
    fun `a headset disconnect pauses, and neither the gain nor a reconnect resumes on the speaker`() = runTest {
        val coordinator = startCoordinator()

        emit(OutputDisconnected)
        emit(FocusChange.LossTransient)
        advanceTimeBy(5.seconds)
        emit(FocusChange.Gain)

        playbackGateway.pauseCount shouldBe 1
        playbackGateway.playCount shouldBe 0
        coordinator.runningSnapshot.pauseReason shouldBe FastPauseReason.User
    }

    @Test
    fun `a picked-up call never resumes on its own`() = runTest {
        startCoordinator()

        emit(FocusChange.LossTransient)
        emit(AudioModeChanged(AudioMode.InCall))
        advanceTimeBy(20.seconds)
        emit(AudioModeChanged(AudioMode.Normal))
        emit(FocusChange.Gain)

        playbackGateway.playCount shouldBe 0
    }

    @Test
    fun `a play during a call does nothing but tell the user, from the app and from a headset`() = runTest {
        val coordinator = startCoordinator()
        emit(AudioModeChanged(AudioMode.Ringtone))
        emit(FocusChange.LossTransient)

        coordinator.play()
        playbackGateway.emitExternal(TransportCommand.Play)
        runCurrent()

        playbackGateway.playCount shouldBe 0
        events.count { it == FastSessionEvent.PlayIgnoredDuringCall } shouldBe 2
    }

    @Test
    fun `a takeover pauses until the user plays`() = runTest {
        val coordinator = startCoordinator()

        emit(FocusChange.Loss)
        advanceTimeBy(5.seconds)
        emit(FocusChange.Gain)
        playbackGateway.playCount shouldBe 0

        coordinator.play()
        runCurrent()
        playbackGateway.playCount shouldBe 1
    }

    @Test
    fun `a gain does not override a dialog that paused the session first`() = runTest {
        val coordinator = startCoordinator()
        coordinator.pauseTemporarily()
        runCurrent()

        emit(FocusChange.LossTransient)
        emit(FocusChange.Gain)
        playbackGateway.playCount shouldBe 0

        coordinator.endTemporaryPause()
        runCurrent()
        playbackGateway.playCount shouldBe 1
    }

    @Test
    fun `a user pause during the episode cancels the auto-resume`() = runTest {
        val coordinator = startCoordinator()
        emit(FocusChange.LossTransient)

        coordinator.pause()
        runCurrent()
        emit(FocusChange.Gain)

        playbackGateway.playCount shouldBe 0
    }

    private companion object {
        const val SUBCATEGORY_ID = "android-compose"
        val START_INSTANT: Instant = Instant.parse("2026-09-06T10:00:00Z")
    }
}
