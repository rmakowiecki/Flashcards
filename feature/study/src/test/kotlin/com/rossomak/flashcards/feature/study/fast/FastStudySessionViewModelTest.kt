package com.rossomak.flashcards.feature.study.fast

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.CardProgressEntry
import com.rossomak.flashcards.core.domain.model.CurationAction
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.SubcategoryProgress
import com.rossomak.flashcards.core.domain.model.VoiceSettings
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.repository.CurationRepository
import com.rossomak.flashcards.core.domain.repository.FakeCardProgressRepository
import com.rossomak.flashcards.core.domain.repository.FakeCurationRepository
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import com.rossomak.flashcards.core.domain.repository.FakeXpConfigRepository
import com.rossomak.flashcards.core.domain.usecase.GetFlashcardsUseCase
import com.rossomak.flashcards.core.domain.usecase.GetSessionStartDataUseCase
import com.rossomak.flashcards.core.domain.usecase.GetSubcategoryProgressUseCase
import com.rossomak.flashcards.core.domain.usecase.GetXpConfigUseCase
import com.rossomak.flashcards.core.domain.usecase.SubmitCurationReportUseCase
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.DraftChange
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.navigation.RouteDecoder
import com.rossomak.flashcards.core.ui.voice.VoiceSettingsController
import com.rossomak.flashcards.core.ui.voice.VoiceSettingsDraftState
import com.rossomak.flashcards.feature.study.FastStudySessionRoute
import com.rossomak.flashcards.feature.study.R
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.ExitSession
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.ReportCurrentCardProblem
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.SessionVoiceSettings as VoiceSettingsDialog
import com.rossomak.flashcards.feature.study.voice.VoiceAnswerState
import com.rossomak.flashcards.feature.study.voice.VoiceGateway
import com.rossomak.flashcards.feature.study.voice.VoicePhase
import com.rossomak.flashcards.feature.study.voice.VoicePlaybackState
import com.rossomak.flashcards.testutil.MainDispatcherRule
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
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
    private val getSubcategoryProgress = GetSubcategoryProgressUseCase(cardProgressRepository)
    private val xpConfigRepository = FakeXpConfigRepository()
    private val getXpConfig = GetXpConfigUseCase(xpConfigRepository)
    private val getSessionStartData = GetSessionStartDataUseCase(getFlashcards, getSubcategoryProgress, getXpConfig)
    private val voiceGateway = FakeVoiceGateway()
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

    private fun createViewModel(curationRepository: CurationRepository = FakeCurationRepository()): FastStudySessionViewModel =
        FastStudySessionViewModel(
            savedStateHandle,
            getSessionStartData,
            SubmitCurationReportUseCase(curationRepository),
            voiceGateway,
            voiceSettingsController,
        )

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
    fun `loadFlashcards surfaces error when any subcategory fetch fails`() = runTest(mainDispatcherRule.testDispatcher) {
        flashcardRepository.flashcardsBySubcategory[subcategoryId] = Result.failure(IllegalStateException("boom"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.error shouldBe R.string.study_session_load_error_message
        viewModel.state.value.isLoading shouldBe false
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
    fun `a card with no prior entry is identifiable as new`() = runTest(mainDispatcherRule.testDispatcher) {
        loadThreeCards()

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.priorProgressByCardId.keys shouldNotContain "card-1"
    }

    @Test
    fun `an existing entry for a card leaves it out of the new-card signal, either mode`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            cardProgressRepository.seed(
                SubcategoryProgress(
                    subcategoryId = subcategoryId,
                    categoryId = "android",
                    cards = mapOf("card-1" to CardProgressEntry(state = FlashcardStudyProgressState.Seen, firstStudiedAt = FIXED_INSTANT, masteredAt = null)),
                ),
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.priorProgressByCardId.keys shouldContain "card-1"
        }

    @Test
    fun `a failed progress read still produces a running session with no error shown`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            cardProgressRepository.resultToReturn = Result.failure(IllegalStateException("offline"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.value.error shouldBe null
            viewModel.state.value.isLoading shouldBe false
            viewModel.priorProgressByCardId shouldBe emptyMap()
        }

    @Test
    fun `the xp configuration is fetched at session start and appears in the session result`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            xpConfigRepository.resultToReturn = Result.success(CUSTOM_XP_CONFIG)
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<FastStudySessionDestination.Summary>()

                destination.route.xpConfig shouldBe CUSTOM_XP_CONFIG
            }
        }

    @Test
    fun `an xp configuration change after the session has started does not change what the result carries`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            xpConfigRepository.resultToReturn = Result.success(CUSTOM_XP_CONFIG)
            val viewModel = createViewModel()
            advanceUntilIdle()
            xpConfigRepository.resultToReturn = Result.success(CUSTOM_XP_CONFIG.copy(newCardStudied = 12345))
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<FastStudySessionDestination.Summary>()

                destination.route.xpConfig shouldBe CUSTOM_XP_CONFIG
            }
        }

    @Test
    fun `a failed xp configuration fetch still starts the session, carrying defaults, with no error shown`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            xpConfigRepository.resultToReturn = Result.failure(IllegalStateException("offline"))
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.value.error shouldBe null
            viewModel.state.value.isLoading shouldBe false
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<FastStudySessionDestination.Summary>()

                destination.route.xpConfig shouldBe XpConfig()
            }
        }

    @Test
    fun `read-aloud off never starts the voice gateway`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(route.copy(readAloudEnabled = false))
        loadThreeCards()

        createViewModel()
        advanceUntilIdle()

        voiceGateway.startCalls shouldBe 0
    }

    @Test
    fun `read-aloud on starts the voice gateway once cards load`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(route.copy(readAloudEnabled = true))
        loadThreeCards()

        createViewModel()
        advanceUntilIdle()

        voiceGateway.startCalls shouldBe 1
    }

    @Test
    fun `read-aloud on with no loaded cards never starts the voice gateway`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(route.copy(readAloudEnabled = true))
        flashcardRepository.flashcardsBySubcategory[subcategoryId] = Result.success(emptyList())

        createViewModel()
        advanceUntilIdle()

        voiceGateway.startCalls shouldBe 0
    }

    @Test
    fun `read-aloud off with reveal-then-Next advances the deck manually`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(route.copy(readAloudEnabled = false))
        loadThreeCards()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onShowAnswer()
        viewModel.onNextCard()

        voiceGateway.startCalls shouldBe 0
        viewModel.state.value.currentCardIndex shouldBe 1
        viewModel.state.value.isAnswerRevealed shouldBe false
    }

    @Test
    fun `onShowAnswer reveals answer when voice inactive`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onShowAnswer()

        viewModel.state.value.isAnswerRevealed shouldBe true
        voiceGateway.showAnswerCalls shouldBe 0
    }

    @Test
    fun `onShowAnswer delegates to gateway when voice active`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        voiceGateway.stateFlow.value = VoicePlaybackState(isActive = true)
        advanceUntilIdle()

        viewModel.onShowAnswer()

        voiceGateway.showAnswerCalls shouldBe 1
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
        loadThreeCards()
        val viewModel = createViewModel()
        advanceUntilIdle()
        // card-1's question is skipped past without its answer ever being shown.
        viewModel.onDialogEvent(Open(ExitSession))

        viewModel.events.test {
            viewModel.onDialogEvent(Confirm)
            val destination = awaitItem().shouldBeInstanceOf<FastStudySessionDestination.Summary>()

            destination.route.cardIds shouldNotContain "card-1"
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
    fun `under read-aloud, reaching the answer phase records the card and does not by itself end the session`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.events.test {
                voiceGateway.stateFlow.value =
                    VoicePlaybackState(isActive = true, isPlaying = true, currentIndex = 0, totalCards = 3, phase = VoicePhase.Answer)
                advanceUntilIdle()
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
    fun `read-aloud natural end fires once the queue settles back on the last card's question, not when its answer starts`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()
            // Each card's answer phase is reached in turn as read-aloud progresses through the deck.
            listOf(0, 1, 2).forEach { index ->
                voiceGateway.stateFlow.value =
                    VoicePlaybackState(isActive = true, isPlaying = true, currentIndex = index, totalCards = 3, phase = VoicePhase.Answer)
                advanceUntilIdle()
            }

            viewModel.events.test {
                voiceGateway.stateFlow.value =
                    VoicePlaybackState(isActive = true, isPlaying = false, currentIndex = 2, totalCards = 3, phase = VoicePhase.Question)
                advanceUntilIdle()
                val destination = awaitItem().shouldBeInstanceOf<FastStudySessionDestination.Summary>()

                destination.route.abandoned shouldBe false
                destination.route.cardIds shouldBe listOf("card-1", "card-2", "card-3")
            }
        }

    @Test
    fun `the terminal navigation event fires exactly once even if voice state re-settles after natural end`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()
            voiceGateway.stateFlow.value =
                VoicePlaybackState(isActive = true, isPlaying = true, currentIndex = 2, totalCards = 3, phase = VoicePhase.Answer)
            advanceUntilIdle()

            viewModel.events.test {
                voiceGateway.stateFlow.value =
                    VoicePlaybackState(isActive = true, isPlaying = false, currentIndex = 2, totalCards = 3, phase = VoicePhase.Question)
                advanceUntilIdle()
                awaitItem().shouldBeInstanceOf<FastStudySessionDestination.Summary>()

                // A distinct value (speechRate) so the StateFlow actually re-emits, still matching
                // the same natural-end condition — terminated must guard this second collection.
                voiceGateway.stateFlow.value = VoicePlaybackState(
                    isActive = true,
                    isPlaying = false,
                    currentIndex = 2,
                    totalCards = 3,
                    phase = VoicePhase.Question,
                    speechRate = 1.5f,
                )
                advanceUntilIdle()
                expectNoEvents()
            }
        }

    @Test
    fun `duration is measured from first card shown, not from route entry`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            var clockInstant = FIXED_INSTANT
            viewModel.now = { clockInstant }
            advanceUntilIdle()

            clockInstant = FIXED_INSTANT.plusSeconds(17)
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<FastStudySessionDestination.Summary>()

                destination.route.durationSeconds shouldBe 17
            }
        }

    @Test
    fun `a session whose card load fails and is then abandoned reports zero duration and empty cardResults`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.flashcardsBySubcategory[subcategoryId] = Result.failure(IllegalStateException("boom"))
            val viewModel = createViewModel()
            var clockInstant = FIXED_INSTANT
            viewModel.now = { clockInstant }
            advanceUntilIdle()

            clockInstant = FIXED_INSTANT.plusSeconds(999)
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<FastStudySessionDestination.Summary>()

                destination.route.durationSeconds shouldBe 0
                destination.route.cardIds.shouldBeEmpty()
            }
        }

    @Test
    fun `a long real-world gap between first card shown and termination is counted in full — v1 never pauses the clock`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            var clockInstant = FIXED_INSTANT
            viewModel.now = { clockInstant }
            advanceUntilIdle()

            // Simulates a long backgrounded gap (a phone call, switching apps) with no lifecycle
            // hook to react to it — v1 is deliberately simplistic: wall time only, no pausing.
            clockInstant = FIXED_INSTANT.plusSeconds(1_200)
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

        voiceGateway.startCalls shouldBe 1
        voiceGateway.lastStartCards?.map { it.id } shouldBe route.cardIds
        voiceGateway.lastStartSubcategoryName shouldBe sessionTitle
        voiceGateway.lastSpeechRate shouldBe savedSettings.speechRate
        voiceGateway.lastVoiceId shouldBe savedSettings.voiceId
    }

    @Test
    fun `observeVoiceState surfaces a voice error and clears active playback`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(route.copy(readAloudEnabled = true))
        loadThreeCards()

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.messages.test {
            voiceGateway.stateFlow.value = VoicePlaybackState(isActive = true, isPlaying = true, error = "playback failed")
            advanceUntilIdle()

            awaitItem() shouldBe FastStudySessionMessage.VoicePlaybackUnavailable
            viewModel.state.value.isVoiceActive shouldBe false
            viewModel.state.value.isVoicePlaying shouldBe false
            viewModel.state.value.isReadAloudMode shouldBe false
        }
    }

    @Test
    fun `observeVoiceState propagates active index and answer phase`() = runTest(mainDispatcherRule.testDispatcher) {
        loadThreeCards()
        val viewModel = createViewModel()
        advanceUntilIdle()

        voiceGateway.stateFlow.value = VoicePlaybackState(isActive = true, currentIndex = 2, phase = VoicePhase.Answer)
        advanceUntilIdle()

        viewModel.state.value.currentCardIndex shouldBe 2
        viewModel.state.value.isAnswerRevealed shouldBe true
        viewModel.state.value.isVoiceActive shouldBe true
    }

    @Test
    fun `onVoiceNext rewinds the gateway to the next card`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onVoiceNext()

        voiceGateway.rewindToNextCalls shouldBe 1
    }

    @Test
    fun `onVoicePlayPause toggles the gateway during normal playback`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onVoicePlayPause()

        voiceGateway.togglePlayPauseCalls shouldBe 1
    }

    @Test
    fun `onVoiceSpeedChange forwards the rate to the gateway`() = runTest(mainDispatcherRule.testDispatcher) {
        val rate = 1.75f
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onVoiceSpeedChange(rate)

        voiceGateway.lastSpeechRate shouldBe rate
    }

    @Test
    fun `ReportProblemOpen pauses playback when voice is playing`() = runTest(mainDispatcherRule.testDispatcher) {
        loadThreeCards()
        val viewModel = createViewModel()
        advanceUntilIdle()
        voiceGateway.stateFlow.value = VoicePlaybackState(isActive = true, isPlaying = true)
        advanceUntilIdle()

        viewModel.onDialogEvent(Open(openReportProblem(viewModel)))
        advanceUntilIdle()

        voiceGateway.togglePlayPauseCalls shouldBe 1
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
            val viewModel = createViewModel()
            advanceUntilIdle()
            voiceGateway.stateFlow.value = VoicePlaybackState(isActive = true)
            advanceUntilIdle()
            viewModel.onDialogEvent(Open(VoiceSettingsDialog()))
            val draft = (viewModel.state.value.activeDialog as VoiceSettingsDialog).draftState
                .copy(draftSpeed = 1.5f, draftVoiceId = "voice-1")
            viewModel.onDialogEvent(DraftChange(VoiceSettingsDialog(draft)))

            viewModel.onDialogEvent(Confirm)

            verify(exactly = 0) { voiceSettingsController.save(any(), any()) }
            verify(exactly = 1) { voiceSettingsController.stopPreview() }
            voiceGateway.lastSpeechRate shouldBe 1.5f
            voiceGateway.lastVoiceId shouldBe "voice-1"
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

        voiceGateway.stopCalls shouldBe 1
    }

    private companion object {
        val FIXED_INSTANT: Instant = Instant.parse("2026-09-06T10:00:00Z")

        // Distinct from XpConfig()'s defaults in every field, so a test asserting this exact value
        // landed can't accidentally pass against the untouched default instead.
        val CUSTOM_XP_CONFIG = XpConfig(
            newCardStudied = 1,
            cardMastered = 2,
            cardPartial = 3,
            masteryDefended = 4,
            cardDemastered = -5,
            sessionCompleted = 6,
            dailyGoalMet = 7,
            streakPerDay = 8,
            streakMaxPerDay = 9,
            minuteStudied = 11,
            levelCurveBase = 13.0,
            levelCurveExponent = 14.0,
        )
    }
}

private class FakeVoiceGateway : VoiceGateway {
    val stateFlow = MutableStateFlow(VoicePlaybackState())
    override val state: StateFlow<VoicePlaybackState> = stateFlow

    val voiceAnswerStateFlow = MutableStateFlow(VoiceAnswerState())
    override val voiceAnswerState: StateFlow<VoiceAnswerState> = voiceAnswerStateFlow

    var lastVoiceAnswering: Boolean? = null
    var lastNextSilenceWillPauseSession: Boolean? = null

    var startCalls = 0
    var lastStartCards: List<Flashcard>? = null
    var lastStartIndex: Int? = null
    var lastStartSubcategoryName: String? = null
    var togglePlayPauseCalls = 0
    var rewindToNextCalls = 0
    var rewindToPreviousCalls = 0
    var restartCurrentCardCalls = 0
    var showAnswerCalls = 0
    var stopCalls = 0
    var lastSpeechRate: Float? = null
    var lastVoiceId: String? = null

    override fun start(cards: List<Flashcard>, startIndex: Int, subcategoryName: String) {
        startCalls++
        lastStartCards = cards
        lastStartIndex = startIndex
        lastStartSubcategoryName = subcategoryName
    }

    override fun updateQueue(cards: List<Flashcard>) = Unit

    override fun stop() {
        stopCalls++
    }
    override fun togglePlayPause() {
        togglePlayPauseCalls++
    }
    override fun rewindToNext() {
        rewindToNextCalls++
    }
    override fun rewindToPrevious() {
        rewindToPreviousCalls++
    }
    override fun restartCurrentCard() {
        restartCurrentCardCalls++
    }
    override fun showAnswer() {
        showAnswerCalls++
    }
    override fun setSpeechRate(rate: Float) {
        lastSpeechRate = rate
    }
    override fun setVoice(voiceId: String?) {
        lastVoiceId = voiceId
    }
    override fun setVoiceAnswering(enabled: Boolean) {
        lastVoiceAnswering = enabled
    }
    override fun setNextSilenceWillPauseSession(willPause: Boolean) {
        lastNextSilenceWillPauseSession = willPause
    }
}
