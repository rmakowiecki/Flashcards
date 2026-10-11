package com.rossomak.flashcards.feature.study.fast

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.AudioModeChanged
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.FocusChanged
import com.rossomak.flashcards.core.domain.model.AudioMode
import com.rossomak.flashcards.core.domain.model.CurationAction
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.FocusChange
import com.rossomak.flashcards.core.domain.model.PlaybackEvent
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import com.rossomak.flashcards.core.domain.model.TransportCommand
import com.rossomak.flashcards.core.domain.model.TransportCommandType
import com.rossomak.flashcards.core.domain.model.VoiceSettings
import com.rossomak.flashcards.core.domain.repository.CurationRepository
import com.rossomak.flashcards.core.domain.repository.FakeAudioInterruptionGateway
import com.rossomak.flashcards.core.domain.repository.FakeCardProgressRepository
import com.rossomak.flashcards.core.domain.repository.FakeCurationRepository
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import com.rossomak.flashcards.core.domain.repository.FakeStudyVoicePlaybackGateway
import com.rossomak.flashcards.core.domain.repository.FakeStudyVoicePlaybackGateway.Call
import com.rossomak.flashcards.core.domain.session.ANSWER_TO_NEXT_PAUSE
import com.rossomak.flashcards.core.domain.session.FastSessionReducer
import com.rossomak.flashcards.core.domain.session.FastStudySessionCoordinator
import com.rossomak.flashcards.core.domain.session.QUESTION_TO_ANSWER_PAUSE
import com.rossomak.flashcards.core.domain.session.RELEASE_LINGER
import com.rossomak.flashcards.core.domain.usecase.GetFlashcardsUseCase
import com.rossomak.flashcards.core.domain.usecase.GetSessionStartDataUseCase
import com.rossomak.flashcards.core.domain.usecase.GetSubcategoryProgressDetailsUseCase
import com.rossomak.flashcards.core.domain.usecase.SubmitCurationReportUseCase
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.DraftChange
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.navigation.RouteDecoder
import com.rossomak.flashcards.core.ui.voice.VoiceSettingsController
import com.rossomak.flashcards.core.ui.voice.VoiceSettingsDraftState
import com.rossomak.flashcards.feature.study.FastStudySessionRoute
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.CurrentCardExtendedContext
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.ExitSession
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.ReportCurrentCardProblem
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.SessionVoiceSettings as VoiceSettingsDialog
import com.rossomak.flashcards.testutil.MainDispatcherRule
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * A Fast Study Session has no Ratings, no Attempts and no voice answering (ADR-0045) — this class
 * only ever exercises the surface [FastStudySessionViewModel] actually exposes, so a failure here
 * names Fast, never a Rated concept it does not share.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FastStudySessionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val savedStateHandle: SavedStateHandle = mockk()
    private val flashcardRepository = FakeFlashcardRepository()
    private val getFlashcards = GetFlashcardsUseCase(flashcardRepository)
    private val cardProgressRepository = FakeCardProgressRepository()
    private val getSubcategoryProgressDetails = GetSubcategoryProgressDetailsUseCase(cardProgressRepository)
    private val getSessionStartData = GetSessionStartDataUseCase(getFlashcards, getSubcategoryProgressDetails)
    private val playbackGateway = FakeStudyVoicePlaybackGateway()
    private val interruptionGateway = FakeAudioInterruptionGateway()
    private val clock = MutableClock(FIXED_INSTANT)
    private val voiceSettingsController: VoiceSettingsController = mockk(relaxed = true)

    private val sessionTitle = "Compose"
    private val subcategoryId = "android-compose"

    private val route = FastStudySessionRoute(
        categoryId = "android",
        sessionTitle = sessionTitle,
        subcategoryIds = listOf(subcategoryId),
        cardIds = listOf("card-1", "card-2", "card-3"),
        categoryName = "Android",
        subcategoryNames = listOf("Compose"),
        sourceType = SingleSubcategory,
    )

    @Before
    fun setUp() {
        mockkObject(RouteDecoder)
        stubRoute(route)
    }

    @After
    fun tearDown() {
        unmockkObject(RouteDecoder)
    }

    private fun stubRoute(route: FastStudySessionRoute) {
        every { RouteDecoder.decode(any<() -> FastStudySessionRoute>()) } returns route
    }

    /** The real coordinator and reducer on the test fixtures, with a settable clock and the test scheduler's time source. */
    private fun createViewModel(curationRepository: CurationRepository = FakeCurationRepository()): FastStudySessionViewModel =
        FastStudySessionViewModel(
            savedStateHandle,
            SubmitCurationReportUseCase(curationRepository),
            FastStudySessionCoordinator(
                getSessionStartData = getSessionStartData,
                playbackGateway = playbackGateway,
                interruptionGateway = interruptionGateway,
                reducer = FastSessionReducer(mockk(relaxed = true)),
                clock = clock,
                timeSource = mainDispatcherRule.testDispatcher.scheduler.timeSource,
                logger = mockk(relaxed = true),
            ),
            voiceSettingsController,
        )

    /** The player reads the presented question in full; after the pause the answer is presented. */
    private fun TestScope.readQuestionThrough() {
        playbackGateway.finishQuestion()
        advanceUntilIdle()
    }

    /** The player reads the presented answer in full; after the pause read-aloud moves on, holds or ends. */
    private fun TestScope.readAnswerThrough() {
        playbackGateway.finishAnswer()
        advanceUntilIdle()
    }

    /** Reads aloud from the first card to the question of card [index]. */
    private fun TestScope.moveToCard(index: Int) {
        repeat(index) {
            readQuestionThrough()
            readAnswerThrough()
        }
    }

    /** A read-aloud session on three cards, loaded and reading its first question. */
    private fun TestScope.createReadAloudViewModel(): FastStudySessionViewModel {
        stubRoute(route.copy(readAloudEnabled = true))
        loadThreeCards()
        return createViewModel().also { advanceUntilIdle() }
    }

    private fun flashcard(id: String, subcategoryId: String = this.subcategoryId): Flashcard = Flashcard(
        id = id,
        subcategoryId = subcategoryId,
        tags = listOf("General"),
        question = "question-$id",
        answer = "answer-$id",
        difficulty = 5,
        questionCode = null,
        answerCode = null,
        questionSpoken = null,
        answerSpoken = null,
        extendedContext = null,
    )

    private fun openReportProblem(viewModel: FastStudySessionViewModel): ReportCurrentCardProblem {
        val card = requireNotNull(viewModel.state.value.currentCard)
        return ReportCurrentCardProblem(cardId = card.id, subcategoryId = card.subcategoryId)
    }

    private fun loadThreeCards() {
        flashcardRepository.flashcardsBySubcategory[subcategoryId] = Result.success(
            listOf(flashcard("card-1"), flashcard("card-2"), flashcard("card-3")),
        )
    }

    private fun reportDraft(viewModel: FastStudySessionViewModel): ReportCurrentCardProblem =
        viewModel.state.value.activeDialog as ReportCurrentCardProblem

    @Test
    fun `loadFlashcards resolves routed card ids preserving order`() = runTest(mainDispatcherRule.testDispatcher) {
        flashcardRepository.flashcardsBySubcategory[subcategoryId] = Result.success(
            listOf(flashcard("card-3"), flashcard("card-1"), flashcard("card-2")),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.flashcards.map { it.id } shouldBe route.cardIds
        viewModel.state.value.isLoading shouldBe false
    }

    @Test
    fun `a failed card load returns to Preview`() = runTest(mainDispatcherRule.testDispatcher) {
        flashcardRepository.flashcardsBySubcategory[subcategoryId] = Result.failure(IllegalStateException("boom"))
        val viewModel = createViewModel()

        viewModel.events.test {
            advanceUntilIdle()

            awaitItem() shouldBe FastStudySessionDestination.Back
        }
    }

    @Test
    fun `a load that finds none of the routed cards returns to Preview`() = runTest(mainDispatcherRule.testDispatcher) {
        flashcardRepository.flashcardsBySubcategory[subcategoryId] = Result.success(emptyList())
        val viewModel = createViewModel()

        viewModel.events.test {
            advanceUntilIdle()

            awaitItem() shouldBe FastStudySessionDestination.Back
        }
    }

    @Test
    fun `session start issues exactly one progress read for a one-subcategory session`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()

            createViewModel()
            advanceUntilIdle()

            cardProgressRepository.requestedSubcategoryIds shouldBe listOf(subcategoryId)
        }

    @Test
    fun `session start issues exactly three progress reads for a three-subcategory session, never chunked`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val subcategoryIds = listOf("sub-1", "sub-2", "sub-3")
            subcategoryIds.forEach { id -> flashcardRepository.flashcardsBySubcategory[id] = Result.success(listOf(flashcard("card-$id", subcategoryId = id))) }
            stubRoute(route.copy(subcategoryIds = subcategoryIds, cardIds = subcategoryIds.map { "card-$it" }))

            createViewModel()
            advanceUntilIdle()

            cardProgressRepository.requestedSubcategoryIds.toSet() shouldBe subcategoryIds.toSet()
            cardProgressRepository.requestedSubcategoryIds.size shouldBe 3
        }

    @Test
    fun `a failed progress read still produces a running session with no error shown`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            cardProgressRepository.resultToReturn = Result.failure(IllegalStateException("offline"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.value.isLoading shouldBe false
        }

    @Test
    fun `read-aloud off never starts the voice gateway`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(route.copy(readAloudEnabled = false))
        loadThreeCards()

        createViewModel()
        advanceUntilIdle()

        playbackGateway.startCalls.size shouldBe 0
    }

    @Test
    fun `read-aloud on starts the voice gateway once cards load`() = runTest(mainDispatcherRule.testDispatcher) {
        createReadAloudViewModel()

        playbackGateway.startCalls.size shouldBe 1
    }

    @Test
    fun `read-aloud on with no loaded cards never starts the voice gateway`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(route.copy(readAloudEnabled = true))
        flashcardRepository.flashcardsBySubcategory[subcategoryId] = Result.success(emptyList())

        createViewModel()
        advanceUntilIdle()

        playbackGateway.startCalls.size shouldBe 0
    }

    @Test
    fun `read-aloud off with reveal-then-Next advances the deck manually`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(route.copy(readAloudEnabled = false))
        loadThreeCards()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onShowAnswer()
        viewModel.onNextCard()

        playbackGateway.startCalls.size shouldBe 0
        viewModel.state.value.currentCardIndex shouldBe 1
        viewModel.state.value.isAnswerRevealed shouldBe false
    }

    @Test
    fun `onShowAnswer reveals the answer`() = runTest(mainDispatcherRule.testDispatcher) {
        loadThreeCards()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onShowAnswer()

        viewModel.state.value.isAnswerRevealed shouldBe true
        playbackGateway.presentedAnswers shouldBe emptyList()
    }

    @Test
    fun `onNextCard on the last card terminates naturally and navigates to the summary`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.flashcardsBySubcategory[subcategoryId] = Result.success(listOf(flashcard("card-1")))
            stubRoute(route.copy(cardIds = listOf("card-1")))

            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onShowAnswer()

            viewModel.events.test {
                viewModel.onNextCard()
                val destination = awaitItem().shouldBeInstanceOf<FastStudySessionDestination.Summary>()

                destination.route.abandoned shouldBe false
                destination.route.cardIds shouldBe listOf("card-1")
            }
        }

    @Test
    fun `confirming the exit dialog closes it and navigates to the summary with the abandoned flag set`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onShowAnswer()
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<FastStudySessionDestination.Summary>()

                destination.route.abandoned shouldBe true
            }
            viewModel.state.value.activeDialog shouldBe null
        }

    @Test
    fun `dismissing the exit dialog closes it without navigating`() = runTest(mainDispatcherRule.testDispatcher) {
        loadThreeCards()
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(ExitSession))

        viewModel.onDialogEvent(Dismiss)

        viewModel.state.value.activeDialog shouldBe null
        viewModel.events.test { expectNoEvents() }
    }

    @Test
    fun `a card skipped during its question is absent from cardResults`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createReadAloudViewModel()
        // card-1's question is skipped past without its answer ever being shown.
        playbackGateway.emitExternal(TransportCommand.JumpTo(1))
        advanceUntilIdle()
        readQuestionThrough()
        viewModel.onDialogEvent(Open(ExitSession))

        viewModel.events.test {
            viewModel.onDialogEvent(Confirm)
            val destination = awaitItem().shouldBeInstanceOf<FastStudySessionDestination.Summary>()

            destination.route.cardIds shouldBe listOf("card-2")
        }
    }

    @Test
    fun `a completed Fast session records every card whose answer was shown, all Seen with zero Attempts`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onShowAnswer()
            viewModel.onNextCard()
            viewModel.onShowAnswer()
            viewModel.onNextCard()
            viewModel.onShowAnswer()

            viewModel.events.test {
                viewModel.onNextCard()
                val destination = awaitItem().shouldBeInstanceOf<FastStudySessionDestination.Summary>()

                destination.route.abandoned shouldBe false
                destination.route.cardIds shouldBe listOf("card-1", "card-2", "card-3")
                destination.route.cardStates shouldBe List(3) { FlashcardStudyProgressState.Seen }
                // Rated-only (ADR-0014): null for a Fast route, not zero-filled lists.
                destination.route.cardAttemptsUsed shouldBe null
                destination.route.cardWasPreviouslyMastered shouldBe null
            }
        }

    @Test
    fun `revisiting a card whose answer was already shown does not add a second cardResults entry`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onShowAnswer()
            viewModel.onShowAnswer() // a re-tap while still on the same card

            viewModel.onDialogEvent(Open(ExitSession))
            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<FastStudySessionDestination.Summary>()

                destination.route.cardIds shouldBe listOf("card-1")
            }
        }

    @Test
    fun `an abandoned Fast session's cardResults holds only cards seen up to that point`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onShowAnswer()
            viewModel.onNextCard()
            // card-2's answer is never shown before exiting.
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<FastStudySessionDestination.Summary>()

                destination.route.abandoned shouldBe true
                destination.route.cardIds shouldBe listOf("card-1")
            }
        }

    @Test
    fun `under read-aloud, the player revealing an answer records the card and does not by itself end the session`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createReadAloudViewModel()

            viewModel.events.test {
                readQuestionThrough()
                expectNoEvents()
            }
            viewModel.onDialogEvent(Open(ExitSession))
            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<FastStudySessionDestination.Summary>()

                destination.route.cardIds shouldBe listOf("card-1")
            }
        }

    @Test
    fun `read-aloud ends once the pause after the last card's answer has run, with every card recorded`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createReadAloudViewModel()
            moveToCard(2)
            readQuestionThrough()

            viewModel.events.test {
                playbackGateway.finishAnswer()
                advanceTimeBy(ANSWER_TO_NEXT_PAUSE - 1.milliseconds)
                runCurrent()
                expectNoEvents()

                advanceTimeBy(1.milliseconds)
                runCurrent()
                val destination = awaitItem().shouldBeInstanceOf<FastStudySessionDestination.Summary>()

                destination.route.abandoned shouldBe false
                destination.route.cardIds shouldBe listOf("card-1", "card-2", "card-3")
            }
        }

    @Test
    fun `the terminal navigation event fires exactly once even if the player reports the last answer finished twice`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createReadAloudViewModel()
            moveToCard(2)
            readQuestionThrough()

            viewModel.events.test {
                readAnswerThrough()
                awaitItem().shouldBeInstanceOf<FastStudySessionDestination.Summary>()

                // The coordinator's end must guard a second report.
                readAnswerThrough()
                expectNoEvents()
            }
        }

    @Test
    fun `confirming the exit dialog before any answer is revealed navigates back, not to the summary`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)

                awaitItem() shouldBe FastStudySessionDestination.Back
            }
        }

    @Test
    fun `duration is measured from first card shown, not from route entry`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onShowAnswer()
            clock.instant = FIXED_INSTANT.plusSeconds(17)
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<FastStudySessionDestination.Summary>()

                destination.route.durationSeconds shouldBe 17
            }
        }

    @Test
    fun `a long real-world gap between first card shown and termination is counted in full — v1 never pauses the clock`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()

            // Simulates a long backgrounded gap (a phone call, switching apps) with no lifecycle
            // hook to react to it — v1 is deliberately simplistic: wall time only, no pausing.
            viewModel.onShowAnswer()
            clock.instant = FIXED_INSTANT.plusSeconds(1_200)
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<FastStudySessionDestination.Summary>()

                destination.route.durationSeconds shouldBe 1_200
            }
        }

    @Test
    fun `read-aloud on starts the gateway with loaded cards and applies saved settings`() = runTest(mainDispatcherRule.testDispatcher) {
        val savedSettings = VoiceSettings(speechRate = 1.5f, voiceId = "voice-1")
        stubRoute(route.copy(readAloudEnabled = true, speechRate = savedSettings.speechRate, voiceId = savedSettings.voiceId))
        loadThreeCards()

        createViewModel()
        advanceUntilIdle()

        val start = playbackGateway.startCalls.single()
        start.cardIds shouldBe route.cardIds
        start.sessionTitle shouldBe sessionTitle
        start.isVoiceAnsweringSession shouldBe false
        playbackGateway.lastSpeechRate shouldBe savedSettings.speechRate
        playbackGateway.lastVoiceId shouldBe savedSettings.voiceId
    }

    @Test
    fun `an unavailable voice engine pauses read-aloud instead of falling back to tap-through`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createReadAloudViewModel()

        viewModel.messages.test {
            playbackGateway.emit(PlaybackEvent.EngineUnavailable)
            advanceUntilIdle()

            awaitItem() shouldBe FastStudySessionMessage.VoicePlaybackUnavailable
            viewModel.state.value.isVoiceActive shouldBe false
            viewModel.state.value.isVoicePlaying shouldBe false
            viewModel.state.value.isReadAloudMode shouldBe true
            viewModel.state.value.isVoiceEngineUnavailable shouldBe true
        }
    }

    @Test
    fun `a play during a call is ignored and shows the paused during a call message`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createReadAloudViewModel()
        interruptionGateway.emit(AudioModeChanged(AudioMode.InCall))
        interruptionGateway.emit(FocusChanged(FocusChange.LossTransient))
        advanceUntilIdle()

        viewModel.messages.test {
            viewModel.onVoicePlayPause()
            advanceUntilIdle()

            awaitItem() shouldBe FastStudySessionMessage.PlayIgnoredDuringCall
        }
        viewModel.state.value.isVoicePlaying shouldBe false
    }

    @Test
    fun `a call that starts shows the message once, and the next call shows it again`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createReadAloudViewModel()

        viewModel.messages.test {
            interruptionGateway.emit(AudioModeChanged(AudioMode.Ringtone))
            advanceUntilIdle()
            awaitItem() shouldBe FastStudySessionMessage.PlayIgnoredDuringCall
            viewModel.state.value.availableTransportCommands shouldBe emptySet()

            interruptionGateway.emit(AudioModeChanged(AudioMode.InCall))
            advanceUntilIdle()
            expectNoEvents()

            interruptionGateway.emit(AudioModeChanged(AudioMode.Normal))
            advanceUntilIdle()

            interruptionGateway.emit(AudioModeChanged(AudioMode.Ringtone))
            advanceUntilIdle()
            awaitItem() shouldBe FastStudySessionMessage.PlayIgnoredDuringCall
        }
    }

    @Test
    fun `a session opened while a call rings shows the message once and offers no command`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(route.copy(readAloudEnabled = true))
        loadThreeCards()
        interruptionGateway.emit(AudioModeChanged(AudioMode.Ringtone))
        val viewModel = createViewModel()

        viewModel.messages.test {
            advanceUntilIdle()
            awaitItem() shouldBe FastStudySessionMessage.PlayIgnoredDuringCall
            expectNoEvents()
        }
        viewModel.state.value.availableTransportCommands shouldBe emptySet()
    }

    @Test
    fun `play after an engine failure restarts the voice stack at the presented card`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createReadAloudViewModel()
        moveToCard(1)
        playbackGateway.emit(PlaybackEvent.EngineUnavailable)
        advanceUntilIdle()

        viewModel.onVoicePlayPause()
        advanceUntilIdle()

        playbackGateway.startCalls.last().startIndex shouldBe 1
        viewModel.state.value.isVoiceEngineUnavailable shouldBe false
        viewModel.state.value.isVoiceActive shouldBe true
    }

    @Test
    fun `the presented card and its answer reach the screen`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createReadAloudViewModel()

        moveToCard(2)
        readQuestionThrough()

        viewModel.state.value.currentCardIndex shouldBe 2
        viewModel.state.value.isAnswerRevealed shouldBe true
        viewModel.state.value.isVoiceActive shouldBe true
    }

    @Test
    fun `onVoiceNext presents the answer at a question and moves to the next card at an answer`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createReadAloudViewModel()

            viewModel.onVoiceNext()
            advanceUntilIdle()

            playbackGateway.calls.last() shouldBe Call.PresentAnswer(0)
            viewModel.state.value.isAnswerRevealed shouldBe true

            viewModel.onVoiceNext()
            advanceUntilIdle()

            playbackGateway.calls.last() shouldBe Call.PresentQuestion(1)
            viewModel.state.value.currentCardIndex shouldBe 1
        }

    @Test
    fun `the read-aloud next is unavailable at the last card's answer`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createReadAloudViewModel()
        moveToCard(2)

        (TransportCommandType.Next in viewModel.state.value.availableTransportCommands) shouldBe true

        readQuestionThrough()

        (TransportCommandType.Next in viewModel.state.value.availableTransportCommands) shouldBe false
    }

    @Test
    fun `onVoicePlayPause pauses during normal playback`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createReadAloudViewModel()

        viewModel.onVoicePlayPause()

        playbackGateway.pauseCount shouldBe 1
    }

    // Dialogs hold at the auto-advance point

    @Test
    fun `opening report, learn more or exit session never pauses playback`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createReadAloudViewModel()

        listOf(openReportProblem(viewModel), CurrentCardExtendedContext(EXTENDED_CONTEXT), ExitSession).forEach { dialog ->
            viewModel.onDialogEvent(Open(dialog))
            runCurrent()

            playbackGateway.pauseCount shouldBe 0
            viewModel.state.value.isVoicePlaying shouldBe true
            viewModel.onDialogEvent(Dismiss)
            runCurrent()
        }
    }

    @Test
    fun `closing report after a hold keeps the old card for 500 ms, then shows and plays the next card`() =
        runTest(mainDispatcherRule.testDispatcher) {
            closingAfterHoldLingersThenMovesOn { viewModel -> openReportProblem(viewModel) }
        }

    @Test
    fun `closing learn more after a hold keeps the old card for 500 ms, then shows and plays the next card`() =
        runTest(mainDispatcherRule.testDispatcher) {
            closingAfterHoldLingersThenMovesOn { CurrentCardExtendedContext(EXTENDED_CONTEXT) }
        }

    @Test
    fun `dismissing exit session after a hold keeps the old card for 500 ms, then shows and plays the next card`() =
        runTest(mainDispatcherRule.testDispatcher) {
            closingAfterHoldLingersThenMovesOn { ExitSession }
        }

    private fun TestScope.closingAfterHoldLingersThenMovesOn(dialog: (FastStudySessionViewModel) -> StudySessionDialog) {
        val viewModel = createReadAloudViewModel()
        viewModel.onDialogEvent(Open(dialog(viewModel)))
        holdAtAdvancePoint(viewModel)

        viewModel.onDialogEvent(Dismiss)
        advanceTimeBy(RELEASE_LINGER - 1.milliseconds)
        viewModel.state.value.currentCardIndex shouldBe 0

        advanceTimeBy(2.milliseconds)
        viewModel.state.value.currentCardIndex shouldBe 1
        viewModel.state.value.isVoicePlaying shouldBe true
        playbackGateway.calls.takeLast(2) shouldBe listOf(Call.PresentQuestion(1), Call.Play)
    }

    @Test
    fun `closing a dialog without a hold changes nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createReadAloudViewModel()
        viewModel.onDialogEvent(Open(CurrentCardExtendedContext(EXTENDED_CONTEXT)))
        runCurrent()

        viewModel.onDialogEvent(Dismiss)
        advanceUntilIdle()

        viewModel.state.value.currentCardIndex shouldBe 0
        viewModel.state.value.isVoicePlaying shouldBe true
        playbackGateway.calls shouldNotContain Call.PresentQuestion(1)
    }

    @Test
    fun `an external play dismisses the open dialog and drops its draft`() = runTest(mainDispatcherRule.testDispatcher) {
        externalCommandDismissesDialog(TransportCommand.Play)
    }

    @Test
    fun `an external next dismisses the open dialog and drops its draft`() = runTest(mainDispatcherRule.testDispatcher) {
        externalCommandDismissesDialog(TransportCommand.Next)
    }

    private fun TestScope.externalCommandDismissesDialog(command: TransportCommand) {
        val viewModel = createReadAloudViewModel()
        viewModel.onDialogEvent(Open(openReportProblem(viewModel)))
        viewModel.onDialogEvent(DraftChange(reportDraft(viewModel).withAction(CurationAction.Delete, isChecked = true)))
        holdAtAdvancePoint(viewModel)

        playbackGateway.emitExternal(command)
        runCurrent()

        viewModel.state.value.activeDialog shouldBe null
        viewModel.state.value.currentCardIndex shouldBe 1
        viewModel.state.value.isVoicePlaying shouldBe true
        viewModel.onDialogEvent(Open(openReportProblem(viewModel)))
        reportDraft(viewModel).selectedActions shouldBe emptySet()
    }

    @Test
    fun `an external pause keeps the open dialog, and closing it then does not resume`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createReadAloudViewModel()
        viewModel.onDialogEvent(Open(CurrentCardExtendedContext(EXTENDED_CONTEXT)))
        holdAtAdvancePoint(viewModel)

        playbackGateway.emitExternal(TransportCommand.Pause)
        runCurrent()
        viewModel.state.value.activeDialog shouldBe CurrentCardExtendedContext(EXTENDED_CONTEXT)

        viewModel.onDialogEvent(Dismiss)
        advanceUntilIdle()

        viewModel.state.value.currentCardIndex shouldBe 0
        viewModel.state.value.isVoicePlaying shouldBe false
    }

    @Test
    fun `a dialog opened within 500 ms of another closing keeps the session held until it closes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createReadAloudViewModel()
            viewModel.onDialogEvent(Open(CurrentCardExtendedContext(EXTENDED_CONTEXT)))
            holdAtAdvancePoint(viewModel)

            viewModel.onDialogEvent(Dismiss)
            advanceTimeBy(RELEASE_LINGER / 2)
            viewModel.onDialogEvent(Open(openReportProblem(viewModel)))
            advanceTimeBy(RELEASE_LINGER * 2)

            viewModel.state.value.currentCardIndex shouldBe 0
            playbackGateway.calls shouldNotContain Call.PresentQuestion(1)

            viewModel.onDialogEvent(Dismiss)
            advanceTimeBy(RELEASE_LINGER + 1.milliseconds)

            viewModel.state.value.currentCardIndex shouldBe 1
        }

    @Test
    fun `an ignored external next leaves the dialog open`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createReadAloudViewModel()
        moveToCard(2)
        readQuestionThrough()
        viewModel.onDialogEvent(Open(ExitSession))

        playbackGateway.emitExternal(TransportCommand.Next)
        runCurrent()

        viewModel.state.value.activeDialog shouldBe ExitSession
    }

    @Test
    fun `an external play while held on the last card ends the session`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createReadAloudViewModel()
        moveToCard(2)
        viewModel.onDialogEvent(Open(ExitSession))
        holdAtAdvancePoint(viewModel)

        viewModel.events.test {
            playbackGateway.emitExternal(TransportCommand.Play)
            runCurrent()

            awaitItem().shouldBeInstanceOf<FastStudySessionDestination.Summary>().route.abandoned shouldBe false
        }
    }

    @Test
    fun `an external play while voice settings are open dismisses them, and playback continues`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createReadAloudViewModel()
            viewModel.onDialogEvent(Open(VoiceSettingsDialog()))
            runCurrent()
            viewModel.state.value.isVoicePlaying shouldBe false

            playbackGateway.emitExternal(TransportCommand.Play)
            runCurrent()

            viewModel.state.value.activeDialog shouldBe null
            viewModel.state.value.isVoicePlaying shouldBe true
            verify(exactly = 1) { voiceSettingsController.stopPreview() }
            playbackGateway.calls.last { it == Call.Play || it == Call.Pause } shouldBe Call.Play
        }

    @Test
    fun `voice settings pause on open and play again on close`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createReadAloudViewModel()

        viewModel.onDialogEvent(Open(VoiceSettingsDialog()))
        runCurrent()
        viewModel.state.value.isVoicePlaying shouldBe false
        viewModel.onDialogEvent(Dismiss)
        runCurrent()

        viewModel.state.value.isVoicePlaying shouldBe true
    }

    // Report submission

    @Test
    fun `a report stays open with Submit disabled while it is sent, and success closes it and releases the hold`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val curationRepository = FakeCurationRepository().apply { pendingUpsert = CompletableDeferred() }
            stubRoute(route.copy(readAloudEnabled = true))
            loadThreeCards()
            val viewModel = createViewModel(curationRepository).also { advanceUntilIdle() }
            viewModel.onDialogEvent(Open(openReportProblem(viewModel)))
            viewModel.onDialogEvent(DraftChange(reportDraft(viewModel).withAction(CurationAction.Delete, isChecked = true)))
            holdAtAdvancePoint(viewModel)

            viewModel.onDialogEvent(Confirm)
            runCurrent()
            reportDraft(viewModel).isSubmitting shouldBe true
            reportDraft(viewModel).canSubmit shouldBe false

            curationRepository.pendingUpsert?.complete(Unit)
            runCurrent()
            viewModel.state.value.activeDialog shouldBe null
            advanceTimeBy(RELEASE_LINGER + 1.milliseconds)

            viewModel.state.value.currentCardIndex shouldBe 1
        }

    @Test
    fun `a failed report stays open, shows the failure and releases nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        val curationRepository = FakeCurationRepository().apply { upsertResultToReturn = Result.failure(IllegalStateException("offline")) }
        stubRoute(route.copy(readAloudEnabled = true))
        loadThreeCards()
        val viewModel = createViewModel(curationRepository).also { advanceUntilIdle() }
        viewModel.onDialogEvent(Open(openReportProblem(viewModel)))
        viewModel.onDialogEvent(DraftChange(reportDraft(viewModel).withAction(CurationAction.Delete, isChecked = true)))
        holdAtAdvancePoint(viewModel)

        viewModel.messages.test {
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()

            awaitItem() shouldBe FastStudySessionMessage.CurationReportFailed
        }
        reportDraft(viewModel).isSubmitting shouldBe false
        reportDraft(viewModel).canSubmit shouldBe true
        viewModel.state.value.currentCardIndex shouldBe 0
    }

    @Test
    fun `a report dismissed while it is sent releases at once, and its later failure still shows`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val curationRepository = FakeCurationRepository().apply {
                pendingUpsert = CompletableDeferred()
                upsertResultToReturn = Result.failure(IllegalStateException("offline"))
            }
            stubRoute(route.copy(readAloudEnabled = true))
            loadThreeCards()
            val viewModel = createViewModel(curationRepository).also { advanceUntilIdle() }
            viewModel.onDialogEvent(Open(openReportProblem(viewModel)))
            viewModel.onDialogEvent(DraftChange(reportDraft(viewModel).withAction(CurationAction.Delete, isChecked = true)))
            holdAtAdvancePoint(viewModel)
            viewModel.onDialogEvent(Confirm)
            runCurrent()

            viewModel.onDialogEvent(Dismiss)
            advanceTimeBy(RELEASE_LINGER + 1.milliseconds)
            viewModel.state.value.currentCardIndex shouldBe 1

            viewModel.messages.test {
                curationRepository.pendingUpsert?.complete(Unit)
                runCurrent()

                awaitItem() shouldBe FastStudySessionMessage.CurationReportFailed
            }
            viewModel.state.value.activeDialog shouldBe null
        }

    /** Reads the presented card in full and finishes the pause after its answer, with a dialog already holding. */
    private fun TestScope.holdAtAdvancePoint(viewModel: FastStudySessionViewModel) {
        playbackGateway.finishQuestion()
        advanceTimeBy(QUESTION_TO_ANSWER_PAUSE)
        runCurrent()
        playbackGateway.finishAnswer()
        advanceTimeBy(ANSWER_TO_NEXT_PAUSE)
        runCurrent()
        viewModel.state.value.isVoicePlaying shouldBe false
    }

    @Test
    fun `report draft is submittable only once an action is checked`() = runTest(mainDispatcherRule.testDispatcher) {
        loadThreeCards()
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(openReportProblem(viewModel)))

        reportDraft(viewModel).canSubmit shouldBe false

        viewModel.onDialogEvent(
            DraftChange(
                reportDraft(viewModel).withAction(CurationAction.Delete, isChecked = true)
            )
        )

        reportDraft(viewModel).canSubmit shouldBe true
    }

    @Test
    fun `checking a difficulty action clears its opposite in the report draft`() = runTest(mainDispatcherRule.testDispatcher) {
        loadThreeCards()
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(openReportProblem(viewModel)))

        viewModel.onDialogEvent(
            DraftChange(
                reportDraft(viewModel).withAction(CurationAction.DifficultyTooHard, isChecked = true)
            )
        )
        viewModel.onDialogEvent(
            DraftChange(
                reportDraft(viewModel).withAction(CurationAction.DifficultyTooEasy, isChecked = true)
            )
        )

        reportDraft(viewModel).selectedActions shouldBe setOf(CurationAction.DifficultyTooEasy)
    }

    @Test
    fun `unchecking an action removes it from the report draft`() = runTest(mainDispatcherRule.testDispatcher) {
        loadThreeCards()
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(openReportProblem(viewModel)))

        viewModel.onDialogEvent(
            DraftChange(
                reportDraft(viewModel).withAction(CurationAction.WrongTags, isChecked = true)
            )
        )
        viewModel.onDialogEvent(
            DraftChange(
                reportDraft(viewModel).withAction(CurationAction.WrongTags, isChecked = false)
            )
        )

        reportDraft(viewModel).selectedActions shouldBe emptySet()
    }

    @Test
    fun `Confirm submits the whole checked set in one call and closes the dialog`() = runTest(mainDispatcherRule.testDispatcher) {
        loadThreeCards()
        val curationRepository = FakeCurationRepository()
        val viewModel = createViewModel(curationRepository)
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(openReportProblem(viewModel)))
        val draft = viewModel.state.value.activeDialog as ReportCurrentCardProblem
        viewModel.onDialogEvent(DraftChange(draft.withAction(CurationAction.Delete, isChecked = true)))

        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()

        curationRepository.submittedReports shouldBe listOf(
            Triple("card-1", subcategoryId, setOf(CurationAction.Delete))
        )
        viewModel.state.value.activeDialog shouldBe null
    }

    @Test
    fun `a failed curation report submission emits a message`() = runTest(mainDispatcherRule.testDispatcher) {
        loadThreeCards()
        val curationRepository = FakeCurationRepository()
        curationRepository.upsertResultToReturn = Result.failure(IllegalStateException("network error"))
        val viewModel = createViewModel(curationRepository)
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(openReportProblem(viewModel)))
        val draft = viewModel.state.value.activeDialog as ReportCurrentCardProblem
        viewModel.onDialogEvent(DraftChange(draft.withAction(CurationAction.Delete, isChecked = true)))

        viewModel.messages.test {
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()

            awaitItem() shouldBe FastStudySessionMessage.CurationReportFailed
        }
    }

    @Test
    fun `Dismiss discards the report draft without submitting`() = runTest(mainDispatcherRule.testDispatcher) {
        loadThreeCards()
        val curationRepository = FakeCurationRepository()
        val viewModel = createViewModel(curationRepository)
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(openReportProblem(viewModel)))
        viewModel.onDialogEvent(
            DraftChange(
                reportDraft(viewModel).withAction(CurationAction.Delete, isChecked = true)
            )
        )

        viewModel.onDialogEvent(Dismiss)
        advanceUntilIdle()

        curationRepository.submittedReports shouldBe emptyList()
        viewModel.state.value.activeDialog shouldBe null
    }

    @Test
    fun `VoiceSettingsOpen seeds the draft from this session's current settings`() = runTest(mainDispatcherRule.testDispatcher) {
        val sessionSettings = VoiceSettings(speechRate = 1.5f, voiceId = "voice-1")
        stubRoute(route.copy(speechRate = sessionSettings.speechRate, voiceId = sessionSettings.voiceId))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onDialogEvent(Open(VoiceSettingsDialog()))

        verify(exactly = 1) { voiceSettingsController.seedDraft(sessionSettings) }
    }

    @Test
    fun `VoiceSettings confirm without keepAsDefault applies for the session but writes nothing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            every { voiceSettingsController.seedDraft(any()) } returns VoiceSettingsDraftState()
            val viewModel = createReadAloudViewModel()
            viewModel.onDialogEvent(Open(VoiceSettingsDialog()))
            val draft = (viewModel.state.value.activeDialog as VoiceSettingsDialog).draftState
                .copy(draftSpeed = 1.5f, draftVoiceId = "voice-1")
            viewModel.onDialogEvent(DraftChange(VoiceSettingsDialog(draft)))

            viewModel.onDialogEvent(Confirm)

            verify(exactly = 0) { voiceSettingsController.save(any(), any()) }
            verify(exactly = 1) { voiceSettingsController.stopPreview() }
            playbackGateway.lastSpeechRate shouldBe 1.5f
            playbackGateway.lastVoiceId shouldBe "voice-1"
            viewModel.state.value.activeDialog shouldBe null
        }

    @Test
    fun `VoiceSettings confirm with keepAsDefault writes the preference`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(VoiceSettingsDialog()))
        val dialog = viewModel.state.value.activeDialog as VoiceSettingsDialog
        viewModel.onDialogEvent(DraftChange(dialog.copy(keepAsDefault = true)))

        viewModel.onDialogEvent(Confirm)

        verify(exactly = 1) { voiceSettingsController.save(any(), any()) }
        viewModel.state.value.activeDialog shouldBe null
    }

    @Test
    fun `VoiceSettings Dismiss discards the draft through the controller`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(VoiceSettingsDialog()))

        viewModel.onDialogEvent(Dismiss)

        verify(exactly = 1) { voiceSettingsController.stopPreview() }
        verify(exactly = 0) { voiceSettingsController.save(any(), any()) }
        viewModel.state.value.activeDialog shouldBe null
    }

    @Test
    fun `onCleared stops the voice gateway`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onCleared()

        playbackGateway.stopCount shouldBe 1
    }

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = instant
    }

    private companion object {
        const val EXTENDED_CONTEXT = "More about this card."
        val FIXED_INSTANT: Instant = Instant.parse("2026-09-06T10:00:00Z")
    }
}
