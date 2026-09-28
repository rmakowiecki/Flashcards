package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.logging.FakeDomainLogger
import com.rossomak.flashcards.core.domain.model.CardProgressEntry
import com.rossomak.flashcards.core.domain.model.FastSessionStateSnapshot
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.PlaybackEvent
import com.rossomak.flashcards.core.domain.model.SessionPauseReason
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SubcategoryProgress
import com.rossomak.flashcards.core.domain.model.TransportCommand
import com.rossomak.flashcards.core.domain.model.VoicePhase
import com.rossomak.flashcards.core.domain.model.VoiceSettings
import com.rossomak.flashcards.core.domain.repository.FakeCardProgressRepository
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import com.rossomak.flashcards.core.domain.repository.FakeStudyVoicePlaybackGateway
import com.rossomak.flashcards.core.domain.repository.FakeStudyVoicePlaybackGateway.Call
import com.rossomak.flashcards.core.domain.repository.FakeXpConfigRepository
import com.rossomak.flashcards.core.domain.usecase.GetFlashcardsUseCase
import com.rossomak.flashcards.core.domain.usecase.GetSessionStartDataUseCase
import com.rossomak.flashcards.core.domain.usecase.GetSubcategoryProgressUseCase
import com.rossomak.flashcards.core.domain.usecase.GetXpConfigUseCase
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FastStudySessionCoordinatorTest {

    private val flashcardRepository = FakeFlashcardRepository()
    private val cardProgressRepository = FakeCardProgressRepository()
    private val getSessionStartData = GetSessionStartDataUseCase(
        GetFlashcardsUseCase(flashcardRepository),
        GetSubcategoryProgressUseCase(cardProgressRepository),
        GetXpConfigUseCase(FakeXpConfigRepository()),
    )
    private val playbackGateway = FakeStudyVoicePlaybackGateway()
    private val logger = FakeDomainLogger()

    private val setup = FastSessionSetup(
        categoryId = "android",
        categoryName = "Android",
        subcategoryIds = listOf(SUBCATEGORY_ID),
        subcategoryNames = listOf("Compose"),
        cardIds = listOf("card-1", "card-2", "card-3"),
        sessionTitle = "Compose",
        voiceSettings = VoiceSettings(speechRate = SPEECH_RATE),
        readAloudEnabled = true,
    )

    private val events = mutableListOf<FastSessionEvent>()

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

    private fun TestScope.startCoordinator(sessionSetup: FastSessionSetup = setup, runsLoad: Boolean = true): FastStudySessionCoordinator {
        flashcardRepository.flashcardsBySubcategory.putIfAbsent(SUBCATEGORY_ID, Result.success(sessionSetup.cardIds.map(::flashcard)))
        val coordinator = FastStudySessionCoordinator(
            getSessionStartData = getSessionStartData,
            playbackGateway = playbackGateway,
            reducer = FastSessionReducer(),
            clock = Clock.fixed(START_INSTANT, ZoneOffset.UTC),
            timeSource = testScheduler.timeSource,
            logger = logger,
        )
        backgroundScope.launch { coordinator.events.collect { events += it } }
        coordinator.start(backgroundScope, sessionSetup)
        if (runsLoad) runCurrent()
        return coordinator
    }

    private fun TestScope.moveToCard(index: Int) {
        playbackGateway.state.update { it.copy(currentIndex = index, phase = VoicePhase.Question) }
        runCurrent()
    }

    @Test
    fun `read-aloud starts the player with the loaded cards and the session's speech rate`() = runTest {
        startCoordinator()

        playbackGateway.startCalls.single() shouldBe Call.Start(listOf("card-1", "card-2", "card-3"), 0, "Compose", false)
        playbackGateway.lastSpeechRate shouldBe SPEECH_RATE
    }

    @Test
    fun `read-aloud off never starts the player`() = runTest {
        startCoordinator(setup.copy(readAloudEnabled = false))

        playbackGateway.startCalls.size shouldBe 0
    }

    // The rewind threshold

    @Test
    fun `previous within 3 seconds of a card starting goes to the previous card`() = runTest {
        startCoordinator().also { moveToCard(1) }
        advanceTimeBy(REWIND_THRESHOLD - 1.milliseconds)

        playbackGateway.emitExternal(TransportCommand.Previous)
        runCurrent()

        playbackGateway.calls.last() shouldBe Call.MoveToPreviousCard
    }

    @Test
    fun `previous 3 seconds after a card started restarts it`() = runTest {
        val coordinator = startCoordinator().also { moveToCard(1) }
        advanceTimeBy(REWIND_THRESHOLD)

        coordinator.previous()

        playbackGateway.calls.last() shouldBe Call.RestartCurrentCard
    }

    @Test
    fun `previous on the first card always restarts it`() = runTest {
        val coordinator = startCoordinator()

        coordinator.previous()

        playbackGateway.calls.last() shouldBe Call.RestartCurrentCard
    }

    // External commands

    @Test
    fun `external commands map onto the player`() = runTest {
        startCoordinator()

        listOf(
            TransportCommand.Pause,
            TransportCommand.Play,
            TransportCommand.Next,
            TransportCommand.PreviousCard,
            TransportCommand.JumpTo(2),
            TransportCommand.Stop,
        ).forEach(playbackGateway::emitExternal)
        runCurrent()

        playbackGateway.calls.takeLast(6) shouldBe listOf(
            Call.Pause,
            Call.Play,
            Call.MoveToNextCard,
            Call.MoveToPreviousCard,
            Call.JumpTo(2),
            Call.Pause,
        )
        playbackGateway.stopCount shouldBe 0
    }

    // Engine unavailable

    @Test
    fun `an unavailable engine pauses the session, and play restarts the voice stack at the presented card`() = runTest {
        val coordinator = startCoordinator().also { moveToCard(2) }
        playbackGateway.emit(PlaybackEvent.EngineUnavailable)
        runCurrent()

        coordinator.runningSnapshot.pauseReason shouldBe SessionPauseReason.VoiceEngineUnavailable
        coordinator.runningSnapshot.currentIndex shouldBe 2
        events shouldContain FastSessionEvent.VoicePlaybackUnavailable
        playbackGateway.stopCount shouldBe 1

        coordinator.play()
        runCurrent()

        coordinator.runningSnapshot.pauseReason shouldBe null
        playbackGateway.startCalls.last().startIndex shouldBe 2
    }

    // Studied set and result

    @Test
    fun `the last card's answer read in full ends the session with every Seen card`() = runTest {
        startCoordinator()
        (0..2).forEach { index ->
            playbackGateway.state.update { it.copy(currentIndex = index, phase = VoicePhase.Answer) }
            runCurrent()
        }

        playbackGateway.state.update { it.copy(isPlaying = false, phase = VoicePhase.Question) }
        runCurrent()

        val result = events.filterIsInstance<FastSessionEvent.SessionEnded>().single().result.shouldBeInstanceOf<SessionResult.Fast>()
        result.abandoned shouldBe false
        result.cardResults.map { it.cardId } shouldBe listOf("card-1", "card-2", "card-3")
    }

    @Test
    fun `leaving before the cards load seals an empty abandoned result with zero duration`() = runTest {
        val coordinator = startCoordinator(runsLoad = false)

        coordinator.end(abandoned = true)
        runCurrent()

        val result = events.filterIsInstance<FastSessionEvent.SessionEnded>().single().result
        result.abandoned shouldBe true
        result.cardResults shouldBe emptyList()
        result.durationSeconds shouldBe 0
    }

    @Test
    fun `the prior progress read is recorded, and a card with no entry is new`() = runTest {
        cardProgressRepository.seed(
            SubcategoryProgress(
                subcategoryId = SUBCATEGORY_ID,
                categoryId = "android",
                cards = mapOf("card-1" to CardProgressEntry(state = FlashcardStudyProgressState.Seen, firstStudiedAt = START_INSTANT, masteredAt = null)),
            ),
        )

        val coordinator = startCoordinator(setup.copy(readAloudEnabled = false))

        coordinator.priorProgressByCardId.keys shouldBe setOf("card-1")
    }

    private companion object {
        const val SUBCATEGORY_ID = "android-compose"
        const val SPEECH_RATE = 1.5f
        val START_INSTANT: Instant = Instant.parse("2026-09-06T10:00:00Z")
    }
}
