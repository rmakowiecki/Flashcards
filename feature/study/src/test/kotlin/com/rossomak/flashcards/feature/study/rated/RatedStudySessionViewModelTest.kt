package com.rossomak.flashcards.feature.study.rated

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.CardProgressEntry
import com.rossomak.flashcards.core.domain.model.CurationAction
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.StudySessionConfig
import com.rossomak.flashcards.core.domain.model.SubcategoryProgress
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGrade
import com.rossomak.flashcards.core.domain.model.VoiceSettings
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.repository.CurationRepository
import com.rossomak.flashcards.core.domain.repository.FakeCardProgressRepository
import com.rossomak.flashcards.core.domain.repository.FakeCurationRepository
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import com.rossomak.flashcards.core.domain.repository.FakeUserPreferencesRepository
import com.rossomak.flashcards.core.domain.repository.FakeXpConfigRepository
import com.rossomak.flashcards.core.domain.usecase.GetFlashcardsUseCase
import com.rossomak.flashcards.core.domain.usecase.GetSessionStartDataUseCase
import com.rossomak.flashcards.core.domain.usecase.GetSubcategoryProgressUseCase
import com.rossomak.flashcards.core.domain.usecase.GetXpConfigUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveUserPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveUserPreferenceUseCase
import com.rossomak.flashcards.core.domain.usecase.SubmitCurationReportUseCase
import com.rossomak.flashcards.core.ui.composables.FlashcardsAttemptSlotState
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.DraftChange
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.navigation.RouteDecoder
import com.rossomak.flashcards.core.ui.voice.VoiceSettingsController
import com.rossomak.flashcards.core.ui.voice.VoiceSettingsDraftState
import com.rossomak.flashcards.core.voice.VoiceCaptureFailureReason
import com.rossomak.flashcards.feature.study.RatedStudySessionRoute
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.ExitSession
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.ReportCurrentCardProblem
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.VoiceAnswerConsent
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.CurationSubmissionFailed
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerCaptureUnavailable
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerConsentSaveFailed
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerGradingFailed
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerMicPermissionRevoked
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerSilencePause
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerSilenceSkip
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoicePlaybackUnavailable
import com.rossomak.flashcards.feature.study.voice.VoiceAnswerFailureReason
import com.rossomak.flashcards.feature.study.voice.VoiceAnswerPhase
import com.rossomak.flashcards.feature.study.voice.VoiceAnswerState
import com.rossomak.flashcards.feature.study.voice.VoiceGateway
import com.rossomak.flashcards.feature.study.voice.VoicePhase
import com.rossomak.flashcards.feature.study.voice.VoicePlaybackState
import com.rossomak.flashcards.testutil.MainDispatcherRule
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * A Rated Study Session owns Ratings and voice answering; it has no Read-aloud auto-start and no
 * notification-permission path — those are Fast concepts (ADR-0045).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RatedStudySessionViewModelTest {

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
    private val userPreferencesRepository = FakeUserPreferencesRepository()
    private val voiceGateway = FakeVoiceGateway()
    private val voiceSettingsController: VoiceSettingsController = mockk(relaxed = true)

    private val sessionTitle = "Compose"
    private val subcategoryId = "android-compose"

    private val route = RatedStudySessionRoute(
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

    private fun stubRoute(route: RatedStudySessionRoute) {
        every { RouteDecoder.decode(any<() -> RatedStudySessionRoute>()) } returns route
    }

    private fun createViewModel(curationRepository: CurationRepository = FakeCurationRepository()): RatedStudySessionViewModel =
        RatedStudySessionViewModel(
            savedStateHandle,
            getSessionStartData,
            SubmitCurationReportUseCase(curationRepository),
            ObserveUserPreferencesUseCase(userPreferencesRepository),
            SaveUserPreferenceUseCase(userPreferencesRepository),
            voiceGateway,
            voiceSettingsController,
        )

    private fun flashcard(
        id: String,
        subcategoryId: String = this.subcategoryId,
        extendedContext: String? = null,
    ): Flashcard = Flashcard(
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
        extendedContext = extendedContext,
    )

    /** What the toolbar hands over: the report dialog seeded from the card on screen. */
    private fun openReportProblem(viewModel: RatedStudySessionViewModel): ReportCurrentCardProblem {
        val card = requireNotNull(viewModel.state.value.currentCard)
        return ReportCurrentCardProblem(cardId = card.id, subcategoryId = card.subcategoryId)
    }

    private fun loadThreeCards() {
        flashcardRepository.flashcardsBySubcategory[subcategoryId] = Result.success(
            listOf(flashcard("card-1"), flashcard("card-2"), flashcard("card-3")),
        )
    }

    /**
     * A pool small enough to keep this file's tests cheap, but with a remaining-queue size (3)
     * bigger than [StudySessionConfig.FAILED_REQUEUE_MIN_GAP] — unlike a 3-card pool, whose
     * remaining size (2) forces every re-insertion to clamp to the same tail position, making 3
     * rotations trivially cyclic back to the original order regardless of the actual gap drawn.
     */
    private fun loadFourCards() {
        val cardIds = (1..4).map { "card-$it" }
        flashcardRepository.flashcardsBySubcategory[subcategoryId] = Result.success(cardIds.map { flashcard(it) })
        stubRoute(route.copy(cardIds = cardIds))
    }

    /** A pool large enough that a re-insertion gap lands mid-queue instead of clamping to the end. */
    private fun loadTenCards() {
        val cardIds = (1..10).map { "card-$it" }
        flashcardRepository.flashcardsBySubcategory[subcategoryId] = Result.success(cardIds.map { flashcard(it) })
        stubRoute(route.copy(cardIds = cardIds))
    }

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

        viewModel.state.value.error shouldBe "Could not load flashcards"
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
    fun `a Rated card with an existing Mastered entry has its previously-mastered flag set`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            cardProgressRepository.seed(
                SubcategoryProgress(
                    subcategoryId = subcategoryId,
                    categoryId = "android",
                    cards = mapOf("card-1" to CardProgressEntry(state = FlashcardStudyProgressState.Mastered, firstStudiedAt = FIXED_INSTANT, masteredAt = FIXED_INSTANT)),
                ),
            )
            val viewModel = createViewModel()
            advanceUntilIdle()
            // One completed Attempt is enough to make card-1 Studied and force-resolvable on
            // abandon — the seeded flag is carried regardless of what this session itself rates it.
            viewModel.onAttemptRating(FlashcardAttemptRating.Failed)
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>()

                val index = destination.route.cardIds.indexOf("card-1")
                index shouldNotBe -1
                destination.route.cardWasPreviouslyMastered?.get(index) shouldBe true
            }
        }

    @Test
    fun `a Rated card with an existing non-Mastered entry does not have its previously-mastered flag set`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            cardProgressRepository.seed(
                SubcategoryProgress(
                    subcategoryId = subcategoryId,
                    categoryId = "android",
                    cards = mapOf("card-1" to CardProgressEntry(state = FlashcardStudyProgressState.Failed, firstStudiedAt = FIXED_INSTANT, masteredAt = null)),
                ),
            )
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onAttemptRating(FlashcardAttemptRating.Failed)
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>()

                val index = destination.route.cardIds.indexOf("card-1")
                index shouldNotBe -1
                destination.route.cardWasPreviouslyMastered?.get(index) shouldBe false
            }
        }

    @Test
    fun `a card with no prior entry is identifiable as new`() = runTest(mainDispatcherRule.testDispatcher) {
        loadThreeCards()

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.priorProgressByCardId.keys shouldNotContain "card-1"
    }

    @Test
    fun `a failed progress read still produces a running session with every previously-mastered flag false and no error shown`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            cardProgressRepository.resultToReturn = Result.failure(IllegalStateException("offline"))
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.value.error shouldBe null
            viewModel.state.value.isLoading shouldBe false
            viewModel.priorProgressByCardId shouldBe emptyMap()
            viewModel.onAttemptRating(FlashcardAttemptRating.Failed)
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>()

                destination.route.cardWasPreviouslyMastered?.all { it == false } shouldBe true
            }
        }

    @Test
    fun `the xp configuration is fetched at session start and appears in the session result`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            xpConfigRepository.resultToReturn = Result.success(CUSTOM_XP_CONFIG)
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onAttemptRating(FlashcardAttemptRating.Failed)
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>()

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
            viewModel.onAttemptRating(FlashcardAttemptRating.Failed)
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>()

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
            viewModel.onAttemptRating(FlashcardAttemptRating.Failed)
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>()

                destination.route.xpConfig shouldBe XpConfig()
            }
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
    fun `rating the current card Correct removes it and advances to the next`() = runTest(mainDispatcherRule.testDispatcher) {
        loadThreeCards()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onShowAnswer()
        viewModel.onAttemptRating(FlashcardAttemptRating.Correct)

        viewModel.state.value.currentCard?.id shouldBe "card-2"
        viewModel.state.value.isAnswerRevealed shouldBe false
    }

    @Test
    fun `onAttemptRating on the last card terminates naturally and navigates to the summary`() = runTest(mainDispatcherRule.testDispatcher) {
        flashcardRepository.flashcardsBySubcategory[subcategoryId] = Result.success(listOf(flashcard("card-1")))
        stubRoute(route.copy(cardIds = listOf("card-1")))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAttemptRating(FlashcardAttemptRating.Correct)

        viewModel.events.test { awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>() }
    }

    @Test
    fun `rating it Failed keeps it in the session and brings it back later`() = runTest(mainDispatcherRule.testDispatcher) {
        loadThreeCards()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAttemptRating(FlashcardAttemptRating.Failed)

        viewModel.state.value.currentCard?.id shouldNotBe "card-1"
        viewModel.state.value.flashcards.map { it.id } shouldContain "card-1"
    }

    @Test
    fun `the mastered count increases only on a Terminal Mastered`() = runTest(mainDispatcherRule.testDispatcher) {
        loadThreeCards()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAttemptRating(FlashcardAttemptRating.Failed)
        viewModel.state.value.masteredCount shouldBe 0

        viewModel.onAttemptRating(FlashcardAttemptRating.Correct)
        viewModel.state.value.masteredCount shouldBe 1
    }

    @Test
    fun `the mastered count does not move on a card finishing Partial or Failed`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.flashcardsBySubcategory[subcategoryId] = Result.success(
                listOf(flashcard("card-1"), flashcard("card-2")),
            )
            stubRoute(route.copy(cardIds = listOf("card-1", "card-2"), ratedAttempts = 1))
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onAttemptRating(FlashcardAttemptRating.Failed)
            viewModel.state.value.masteredCount shouldBe 0

            viewModel.onAttemptRating(FlashcardAttemptRating.PartiallyCorrect)
            viewModel.state.value.masteredCount shouldBe 0
        }

    @Test
    fun `the completed count increases on any Terminal State, unlike the mastered count`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.flashcardsBySubcategory[subcategoryId] = Result.success(
                listOf(flashcard("card-1"), flashcard("card-2")),
            )
            stubRoute(route.copy(cardIds = listOf("card-1", "card-2"), ratedAttempts = 1))
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onAttemptRating(FlashcardAttemptRating.Failed)
            viewModel.state.value.completedCount shouldBe 1
            viewModel.state.value.masteredCount shouldBe 0

            viewModel.onAttemptRating(FlashcardAttemptRating.PartiallyCorrect)
            viewModel.state.value.completedCount shouldBe 2
            viewModel.state.value.masteredCount shouldBe 0
        }

    @Test
    fun `the distinct card total is fixed at session start and does not grow as the queue grows`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()
            val distinctCountBefore = viewModel.state.value.distinctCardCount

            viewModel.onAttemptRating(FlashcardAttemptRating.Failed)

            viewModel.state.value.distinctCardCount shouldBe distinctCountBefore
            viewModel.state.value.distinctCardCount shouldBe 3
        }

    @Test
    fun `the attempt indicator's slots reflect the current card's Rating list in order`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadTenCards()
            val viewModel = createViewModel().apply { random = Random(FIXED_SEED) }
            advanceUntilIdle()

            viewModel.onAttemptRating(FlashcardAttemptRating.Failed)
            // The card that just went to the back of the queue is not the head any more, so cycle
            // through the others (Correct finishes them immediately) until it resurfaces.
            while (viewModel.state.value.currentCard?.id != "card-1") {
                viewModel.onAttemptRating(FlashcardAttemptRating.Correct)
            }

            viewModel.state.value.currentCardRatings shouldBe listOf(FlashcardAttemptRating.Failed)
            viewModel.state.value.attemptSlots shouldBe listOf(
                FlashcardsAttemptSlotState.Failed,
                FlashcardsAttemptSlotState.Current,
                FlashcardsAttemptSlotState.Future,
            )
        }

    @Test
    fun `the attempt indicator's Current position and Future count match the configured Attempts limit`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            stubRoute(route.copy(cardIds = listOf("card-1", "card-2", "card-3"), ratedAttempts = 4))
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.value.attemptSlots shouldBe listOf(
                FlashcardsAttemptSlotState.Current,
                FlashcardsAttemptSlotState.Future,
                FlashcardsAttemptSlotState.Future,
                FlashcardsAttemptSlotState.Future,
            )
        }

    @Test
    fun `the terminal navigation event fires exactly once, when the last card finishes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onAttemptRating(FlashcardAttemptRating.Correct)
            viewModel.onAttemptRating(FlashcardAttemptRating.Correct)

            viewModel.events.test {
                viewModel.onAttemptRating(FlashcardAttemptRating.Correct)
                awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>()
            }
        }

    @Test
    fun `confirming the exit dialog after natural end already fired does not send a second Summary event`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.flashcardsBySubcategory[subcategoryId] = Result.success(listOf(flashcard("card-1")))
            stubRoute(route.copy(cardIds = listOf("card-1")))
            val viewModel = createViewModel()
            advanceUntilIdle()
            // The exit dialog was already open when the last (only) card's rating naturally
            // completed the deck and sent its own Summary event.
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onAttemptRating(FlashcardAttemptRating.Correct)
                awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>()

                viewModel.onDialogEvent(Confirm)
                expectNoEvents()
            }
        }

    @Test
    fun `partialRatingCardRequeueingEnabled false ends a Partial rating immediately as Terminal Partial`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.flashcardsBySubcategory[subcategoryId] = Result.success(listOf(flashcard("card-1")))
            stubRoute(route.copy(cardIds = listOf("card-1"), partialRatingCardRequeueingEnabled = false))
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onAttemptRating(FlashcardAttemptRating.PartiallyCorrect)

            viewModel.events.test { awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>() }
        }

    @Test
    fun `partialRatingCardRequeueingEnabled true re-queues a Partial rating`() = runTest(mainDispatcherRule.testDispatcher) {
        loadThreeCards()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAttemptRating(FlashcardAttemptRating.PartiallyCorrect)

        viewModel.state.value.currentCard?.id shouldNotBe "card-1"
        viewModel.state.value.flashcards.map { it.id } shouldContain "card-1"
    }

    @Test
    fun `a rating sequence produces the expected order of displayed cards under a fixed Random`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadTenCards()
            val ratingSequence = listOf(FlashcardAttemptRating.Failed, FlashcardAttemptRating.PartiallyCorrect, FlashcardAttemptRating.Failed)

            val firstViewModel = createViewModel().apply { random = Random(FIXED_SEED) }
            advanceUntilIdle()
            ratingSequence.forEach(firstViewModel::onAttemptRating)
            val firstOrder = firstViewModel.state.value.flashcards.map { it.id }

            val secondViewModel = createViewModel().apply { random = Random(FIXED_SEED) }
            advanceUntilIdle()
            ratingSequence.forEach(secondViewModel::onAttemptRating)
            val secondOrder = secondViewModel.state.value.flashcards.map { it.id }

            firstOrder shouldBe secondOrder
        }

    @Test
    fun `confirming the exit dialog closes it and navigates to the summary`() = runTest(mainDispatcherRule.testDispatcher) {
        loadThreeCards()
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(ExitSession))

        viewModel.onDialogEvent(Confirm)

        viewModel.state.value.activeDialog shouldBe null
        viewModel.events.test { awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>() }
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
    fun `observeVoiceState surfaces a voice error and clears active playback`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.messages.test {
            voiceGateway.stateFlow.value = VoicePlaybackState(isActive = true, isPlaying = true, error = "playback failed")
            advanceUntilIdle()

            awaitItem() shouldBe VoicePlaybackUnavailable
        }
        viewModel.state.value.isVoiceActive shouldBe false
        viewModel.state.value.isVoicePlaying shouldBe false
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
        viewModel.onDialogEvent(
            DraftChange(
                reportDraft(viewModel).withAction(CurationAction.Delete, isChecked = true)
            )
        )
        viewModel.onDialogEvent(
            DraftChange(
                reportDraft(viewModel).withAction(CurationAction.WrongTags, isChecked = true)
            )
        )

        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()

        curationRepository.submittedReports shouldBe listOf(
            Triple("card-1", subcategoryId, setOf(CurationAction.Delete, CurationAction.WrongTags))
        )
        viewModel.state.value.activeDialog shouldBe null
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

        viewModel.onDialogEvent(Open(StudySessionDialog.SessionVoiceSettings()))

        viewModel.state.value.activeDialog.shouldBeInstanceOf<StudySessionDialog.SessionVoiceSettings>()
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
            viewModel.onDialogEvent(Open(StudySessionDialog.SessionVoiceSettings()))
            val draft = (viewModel.state.value.activeDialog as StudySessionDialog.SessionVoiceSettings).draftState
                .copy(draftSpeed = 1.5f, draftVoiceId = "voice-1")
            viewModel.onDialogEvent(DraftChange(StudySessionDialog.SessionVoiceSettings(draft)))

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
        viewModel.onDialogEvent(Open(StudySessionDialog.SessionVoiceSettings()))
        val dialog = viewModel.state.value.activeDialog as StudySessionDialog.SessionVoiceSettings
        viewModel.onDialogEvent(DraftChange(dialog.copy(keepAsDefault = true)))

        viewModel.onDialogEvent(Confirm)

        verify(exactly = 1) { voiceSettingsController.save(any(), any()) }
        viewModel.state.value.activeDialog shouldBe null
    }

    @Test
    fun `VoiceSettings Dismiss discards the draft through the controller`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(StudySessionDialog.SessionVoiceSettings()))

        viewModel.onDialogEvent(Dismiss)

        verify(exactly = 1) { voiceSettingsController.stopPreview() }
        verify(exactly = 0) { voiceSettingsController.save(any(), any()) }
        viewModel.state.value.activeDialog shouldBe null
    }

    @Test
    fun `ExitSessionOpen shows the confirmation and Dismiss cancels it`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onDialogEvent(Open(ExitSession))
        viewModel.state.value.activeDialog shouldBe ExitSession

        viewModel.onDialogEvent(Dismiss)
        viewModel.state.value.activeDialog shouldBe null
    }

    @Test
    fun `a routed voice-answering choice without consent opens the consent dialog on entry`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(route.copy(voiceAnsweringEnabled = true))
            loadThreeCards()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.value.activeDialog shouldBe VoiceAnswerConsent
        }

    @Test
    fun `a routed voice-answering choice with consent requests the mic permission on entry`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(route.copy(voiceAnsweringEnabled = true))
            userPreferencesRepository.preferences.value = userPreferencesRepository.preferences.value.copy(voiceAnswerConsentGranted = true)
            loadThreeCards()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.value.isMicPermissionRequestPending shouldBe true
        }

    private fun reportDraft(viewModel: RatedStudySessionViewModel): ReportCurrentCardProblem =
        viewModel.state.value.activeDialog as ReportCurrentCardProblem

    @Test
    fun `onCleared stops the voice gateway`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onCleared()

        voiceGateway.stopCalls shouldBe 1
    }

    @Test
    fun `accepting voice-answer consent persists it and requests the mic permission`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(route.copy(voiceAnsweringEnabled = true))
        loadThreeCards()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()

        userPreferencesRepository.preferences.value.voiceAnswerConsentGranted shouldBe true
        viewModel.state.value.activeDialog shouldBe null
        viewModel.state.value.isMicPermissionRequestPending shouldBe true
    }

    @Test
    fun `a failed consent save keeps the dialog open, surfaces an error, and skips the mic request`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(route.copy(voiceAnsweringEnabled = true))
            loadThreeCards()
            userPreferencesRepository.saveError = IllegalStateException("disk full")
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.messages.test {
                viewModel.onDialogEvent(Confirm)
                advanceUntilIdle()

                awaitItem() shouldBe VoiceAnswerConsentSaveFailed
            }
            userPreferencesRepository.preferences.value.voiceAnswerConsentGranted shouldBe false
            viewModel.state.value.activeDialog shouldBe VoiceAnswerConsent
            viewModel.state.value.isMicPermissionRequestPending shouldBe false
        }

    @Test
    fun `onMicPermissionResult granted enables voice answering on the gateway`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onMicPermissionResult(true)

        voiceGateway.lastVoiceAnswering shouldBe true
        viewModel.state.value.isMicPermissionRequestPending shouldBe false
    }

    @Test
    fun `onMicPermissionResult granted bootstraps the gateway`() = runTest(mainDispatcherRule.testDispatcher) {
        loadThreeCards()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onMicPermissionResult(true)

        voiceGateway.startCalls shouldBe 1
        voiceGateway.lastStartCards?.map { it.id } shouldBe route.cardIds
        voiceGateway.lastVoiceAnswering shouldBe true
    }

    @Test
    fun `onMicPermissionResult denied leaves voice answering off`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onMicPermissionResult(false)

        voiceGateway.lastVoiceAnswering shouldBe null
        viewModel.state.value.isMicPermissionRequestPending shouldBe false
    }

    @Test
    fun `voice answer state from the gateway is surfaced in screen state`() = runTest(mainDispatcherRule.testDispatcher) {
        val grade = VoiceAnswerGrade(sanitizedTranscript = "clean", gradePercent = 82, feedback = "good")
        val viewModel = createViewModel()
        advanceUntilIdle()

        voiceGateway.voiceAnswerStateFlow.value = VoiceAnswerState(isEnabled = true, lastGrade = grade)
        advanceUntilIdle()

        viewModel.state.value.isVoiceAnswerEnabled shouldBe true
        viewModel.state.value.lastVoiceAnswerGrade shouldBe grade
    }

    /**
     * Three [MutableStateFlow] writes, each followed by [advanceUntilIdle], so the collector
     * actually observes every intermediate phase — writing SpeakingNotice twice in a row without
     * that would conflate into one emission (equal consecutive [VoiceAnswerState] values), silently
     * dropping a silence timeout. The final WaitingForQuestion write mirrors the real notice-finished
     * transition ([VoiceAnswerController.onNoticeFinishedSpeaking]) — the screen's queue/currentCard
     * sync is deferred until the phase actually leaves SpeakingNotice, so a test that stopped at
     * SpeakingNotice would never see it applied.
     */
    private fun TestScope.emitSilenceTimeout() {
        voiceGateway.voiceAnswerStateFlow.value = VoiceAnswerState(isEnabled = true, phase = VoiceAnswerPhase.Listening)
        advanceUntilIdle()
        voiceGateway.voiceAnswerStateFlow.value = VoiceAnswerState(isEnabled = true, phase = VoiceAnswerPhase.SpeakingNotice)
        advanceUntilIdle()
        voiceGateway.voiceAnswerStateFlow.value = VoiceAnswerState(isEnabled = true, phase = VoiceAnswerPhase.WaitingForQuestion)
        advanceUntilIdle()
    }

    private fun TestScope.emitGrade(grade: VoiceAnswerGrade, cardId: String) {
        voiceGateway.voiceAnswerStateFlow.value = VoiceAnswerState(isEnabled = true, phase = VoiceAnswerPhase.Grading)
        advanceUntilIdle()
        voiceGateway.voiceAnswerStateFlow.value = VoiceAnswerState(
            isEnabled = true,
            phase = VoiceAnswerPhase.SpeakingNotice,
            lastGrade = grade,
            lastGradedCardId = cardId,
        )
        advanceUntilIdle()
        // Notice-finished edge (see emitSilenceTimeout's kdoc) — this is what actually applies the
        // deferred queue/currentCard sync in production.
        voiceGateway.voiceAnswerStateFlow.value = VoiceAnswerState(
            isEnabled = true,
            phase = VoiceAnswerPhase.WaitingForQuestion,
            lastGrade = grade,
            lastGradedCardId = cardId,
        )
        advanceUntilIdle()
    }

    @Test
    fun `a voice grade drives the same Attempt increment and re-insertion as the equivalent manual rating`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()

            emitGrade(grade = VoiceAnswerGrade(sanitizedTranscript = "t", gradePercent = 20, feedback = "missed it"), cardId = "card-1")

            viewModel.state.value.currentCard?.id shouldNotBe "card-1"
            viewModel.state.value.flashcards.map { it.id } shouldContain "card-1"
        }

    @Test
    fun `a grade in the Correct band finishes the card as Mastered, exactly as a manual Correct does`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.flashcardsBySubcategory[subcategoryId] = Result.success(listOf(flashcard("card-1")))
            stubRoute(route.copy(cardIds = listOf("card-1")))
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.events.test {
                emitGrade(grade = VoiceAnswerGrade(sanitizedTranscript = "t", gradePercent = 95, feedback = "great"), cardId = "card-1")
                awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>()
            }
            viewModel.state.value.masteredCount shouldBe 1
        }

    @Test
    fun `a silence timeout leaves the card's Rating list and best rating unchanged, and re-queues the card`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadTenCards()
            val viewModel = createViewModel().apply { random = Random(FIXED_SEED) }
            advanceUntilIdle()

            emitSilenceTimeout()

            viewModel.state.value.currentCard?.id shouldNotBe "card-1"
            viewModel.state.value.flashcards.map { it.id } shouldContain "card-1"
            while (viewModel.state.value.currentCard?.id != "card-1") {
                viewModel.onAttemptRating(FlashcardAttemptRating.Correct)
            }

            viewModel.state.value.currentCardRatings shouldBe emptyList()
        }

    @Test
    fun `a silence timeout re-queues within the Failed gap range`() = runTest(mainDispatcherRule.testDispatcher) {
        loadTenCards()
        val viewModel = createViewModel()
        advanceUntilIdle()

        emitSilenceTimeout()

        val index = viewModel.state.value.flashcards.indexOfFirst { it.id == "card-1" }
        (index in StudySessionConfig.FAILED_REQUEUE_MIN_GAP..StudySessionConfig.FAILED_REQUEUE_MAX_GAP) shouldBe true
    }

    @Test
    fun `two silence timeouts do not pause the session, but the third does`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()

            repeat(2) { emitSilenceTimeout() }
            viewModel.state.value.isVoiceAnswerPaused shouldBe false

            emitSilenceTimeout()

            viewModel.state.value.isVoiceAnswerPaused shouldBe true
        }

    @Test
    fun `the consecutive silence counter resets on a graded answer, so silence-silence-grade-silence does not pause`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()

            repeat(2) { emitSilenceTimeout() }
            emitGrade(
                grade = VoiceAnswerGrade(sanitizedTranscript = "t", gradePercent = 90, feedback = "f"),
                cardId = viewModel.state.value.currentCard?.id.orEmpty(),
            )

            emitSilenceTimeout()

            viewModel.state.value.isVoiceAnswerPaused shouldBe false
        }

    @Test
    fun `pausing after three silences stops playback, exposes the resume affordance, and records no outcome`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadFourCards()
            val viewModel = createViewModel().apply { random = Random(FIXED_SEED) }
            advanceUntilIdle()
            voiceGateway.stateFlow.value = VoicePlaybackState(isActive = true, isPlaying = true)
            advanceUntilIdle()
            val masteredBefore = viewModel.state.value.masteredCount
            val flashcardsBefore = viewModel.state.value.flashcards.map { it.id }

            // Independently reproduces requeueAfterSilence's exact draw order/range with an
            // unrelated Random instance seeded identically — a 4-card pool's remaining-queue size
            // (3) exceeds the Failed gap's minimum (2), so unlike a 3-card pool this genuinely
            // exercises re-insertion position rather than clamping to a fixed spot every time.
            val referenceRandom = Random(FIXED_SEED)
            val expectedQueue = flashcardsBefore.toMutableList()
            repeat(3) {
                val head = expectedQueue.removeAt(0)
                val gap = referenceRandom.nextInt(
                    StudySessionConfig.FAILED_REQUEUE_MIN_GAP,
                    StudySessionConfig.FAILED_REQUEUE_MAX_GAP + 1,
                )
                expectedQueue.add(gap.coerceAtMost(expectedQueue.size), head)
            }

            viewModel.events.test {
                repeat(3) { emitSilenceTimeout() }
                expectNoEvents()
            }

            viewModel.state.value.isVoiceAnswerPaused shouldBe true
            voiceGateway.togglePlayPauseCalls shouldBe 1
            voiceGateway.lastVoiceAnswering shouldBe false
            viewModel.state.value.masteredCount shouldBe masteredBefore
            viewModel.state.value.flashcards.map { it.id } shouldBe expectedQueue
        }

    @Test
    fun `resuming continues from the same card with the counter reset`() = runTest(mainDispatcherRule.testDispatcher) {
        loadThreeCards()
        val viewModel = createViewModel()
        advanceUntilIdle()
        repeat(3) { emitSilenceTimeout() }
        val cardBeforePause = viewModel.state.value.currentCard?.id

        viewModel.onResumeSession()

        viewModel.state.value.isVoiceAnswerPaused shouldBe false
        viewModel.state.value.currentCard?.id shouldBe cardBeforePause
        voiceGateway.lastVoiceAnswering shouldBe true

        // Counter reset: two more silences must not re-pause.
        repeat(2) { emitSilenceTimeout() }
        viewModel.state.value.isVoiceAnswerPaused shouldBe false
    }

    @Test
    fun `a silence timeout under the pause threshold emits the skip snackbar message`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.messages.test {
                emitSilenceTimeout()

                awaitItem() shouldBe VoiceAnswerSilenceSkip
            }
        }

    @Test
    fun `the third consecutive silence timeout emits the pause snackbar message instead of the skip message`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()
            repeat(2) { emitSilenceTimeout() }

            viewModel.messages.test {
                emitSilenceTimeout()

                awaitItem() shouldBe VoiceAnswerSilencePause
            }
        }

    @Test
    fun `a grading or transcription failure emits a snackbar message and is not counted as a silence`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.messages.test {
                repeat(3) {
                    voiceGateway.voiceAnswerStateFlow.value = VoiceAnswerState(isEnabled = true, phase = VoiceAnswerPhase.Grading)
                    advanceUntilIdle()
                    voiceGateway.voiceAnswerStateFlow.value = VoiceAnswerState(
                        isEnabled = true,
                        phase = VoiceAnswerPhase.SpeakingNotice,
                        error = VoiceAnswerFailureReason.GradingFailed("boom"),
                    )
                    advanceUntilIdle()
                    awaitItem() shouldBe VoiceAnswerGradingFailed
                    voiceGateway.voiceAnswerStateFlow.value = VoiceAnswerState(isEnabled = true, phase = VoiceAnswerPhase.WaitingForQuestion)
                    advanceUntilIdle()
                }
            }
            // Three in a row — a real silence timeout would have paused by now.
            viewModel.state.value.isVoiceAnswerPaused shouldBe false
        }

    @Test
    fun `a mid-session capture failure from a missing mic permission ends the session`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.messages.test {
                // CaptureFailed resets phase to WaitingForQuestion, never SpeakingNotice — this must
                // still be caught, unlike an ordinary grading/transcription failure.
                voiceGateway.voiceAnswerStateFlow.value = VoiceAnswerState(
                    isEnabled = true,
                    phase = VoiceAnswerPhase.WaitingForQuestion,
                    error = VoiceAnswerFailureReason.CaptureFailed(VoiceCaptureFailureReason.PermissionMissing(detail = null)),
                )
                advanceUntilIdle()

                awaitItem() shouldBe VoiceAnswerMicPermissionRevoked
            }
            viewModel.events.test {
                awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>()
            }
        }

    @Test
    fun `a bare mic-permission-missing voice-answer state also ends the session`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // VoiceAnswerController.start() sets this bare (unwrapped) form directly if the
            // permission is already gone the moment voice answering (re)enables — e.g. resuming
            // after a pause with the permission revoked in the meantime.
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.messages.test {
                voiceGateway.voiceAnswerStateFlow.value = VoiceAnswerState(error = VoiceAnswerFailureReason.PermissionMissing)
                advanceUntilIdle()

                awaitItem() shouldBe VoiceAnswerMicPermissionRevoked
            }
            viewModel.events.test {
                awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>()
            }
        }

    @Test
    fun `a capture failure unrelated to mic permission pauses the session instead of ending it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.messages.test {
                voiceGateway.voiceAnswerStateFlow.value = VoiceAnswerState(
                    isEnabled = true,
                    phase = VoiceAnswerPhase.WaitingForQuestion,
                    error = VoiceAnswerFailureReason.CaptureFailed(VoiceCaptureFailureReason.BluetoothMicUnavailable),
                )
                advanceUntilIdle()

                awaitItem() shouldBe VoiceAnswerCaptureUnavailable
            }
            viewModel.events.test { expectNoEvents() }
            viewModel.state.value.isVoiceAnswerPaused shouldBe true
            voiceGateway.lastVoiceAnswering shouldBe false
            voiceGateway.restartCurrentCardCalls shouldBe 1
        }

    @Test
    fun `isAnswerRevealed stays false while a silence-timeout notice is speaking`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()
            voiceGateway.stateFlow.value = VoicePlaybackState(isActive = true)
            advanceUntilIdle()

            voiceGateway.voiceAnswerStateFlow.value = VoiceAnswerState(isEnabled = true, phase = VoiceAnswerPhase.Listening)
            advanceUntilIdle()
            voiceGateway.voiceAnswerStateFlow.value = VoiceAnswerState(isEnabled = true, phase = VoiceAnswerPhase.SpeakingNotice)
            advanceUntilIdle()
            // Any TTS-engine (not voice-answer) emission while a no-grade SpeakingNotice is active
            // re-evaluates isAnswerRevealed off the now-current voiceAnswerPhase — exactly the broad
            // leak this guards against, since it used to treat SpeakingNotice alone as reveal-worthy.
            voiceGateway.stateFlow.value = VoicePlaybackState(isActive = true, isPlaying = true)
            advanceUntilIdle()

            viewModel.state.value.isAnswerRevealed shouldBe false
        }

    @Test
    fun `isAnswerRevealed stays false while a grading-failure notice is speaking`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()
            voiceGateway.stateFlow.value = VoicePlaybackState(isActive = true)
            advanceUntilIdle()

            voiceGateway.voiceAnswerStateFlow.value = VoiceAnswerState(isEnabled = true, phase = VoiceAnswerPhase.Grading)
            advanceUntilIdle()
            voiceGateway.voiceAnswerStateFlow.value = VoiceAnswerState(
                isEnabled = true,
                phase = VoiceAnswerPhase.SpeakingNotice,
                error = VoiceAnswerFailureReason.GradingFailed("boom"),
            )
            advanceUntilIdle()
            voiceGateway.stateFlow.value = VoicePlaybackState(isActive = true, isPlaying = true)
            advanceUntilIdle()

            viewModel.state.value.isAnswerRevealed shouldBe false
        }

    @Test
    fun `isAnswerRevealed turns true while a real grade's notice is speaking`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()
            voiceGateway.stateFlow.value = VoicePlaybackState(isActive = true)
            advanceUntilIdle()
            val grade = VoiceAnswerGrade(sanitizedTranscript = "t", gradePercent = 80, feedback = "ok")

            voiceGateway.voiceAnswerStateFlow.value = VoiceAnswerState(isEnabled = true, phase = VoiceAnswerPhase.Grading)
            advanceUntilIdle()
            voiceGateway.voiceAnswerStateFlow.value = VoiceAnswerState(
                isEnabled = true,
                phase = VoiceAnswerPhase.SpeakingNotice,
                lastGrade = grade,
                lastGradedCardId = viewModel.state.value.currentCard?.id,
            )
            advanceUntilIdle()
            voiceGateway.stateFlow.value = VoicePlaybackState(isActive = true, isPlaying = true)
            advanceUntilIdle()

            viewModel.state.value.isAnswerRevealed shouldBe true
        }

    @Test
    fun `the third silence's pause command fires the instant SpeakingNotice is entered, before any notice-finished advance could arrive`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()
            voiceGateway.stateFlow.value = VoicePlaybackState(isActive = true, isPlaying = true)
            advanceUntilIdle()
            repeat(2) { emitSilenceTimeout() }

            // Only the phase-entry write, not emitSilenceTimeout()'s full WaitingForQuestion
            // sequence — mirrors production timing exactly: VoiceAnswerController flips to
            // SpeakingNotice synchronously, strictly before it starts speaking the notice or (later
            // still, only once that finishes) requests an advance via ADR-0025's ADVANCE_DELAY_MS.
            voiceGateway.voiceAnswerStateFlow.value = VoiceAnswerState(isEnabled = true, phase = VoiceAnswerPhase.Listening)
            advanceUntilIdle()
            voiceGateway.voiceAnswerStateFlow.value = VoiceAnswerState(isEnabled = true, phase = VoiceAnswerPhase.SpeakingNotice)
            advanceUntilIdle()

            voiceGateway.togglePlayPauseCalls shouldBe 1
            voiceGateway.lastVoiceAnswering shouldBe false
        }

    @Test
    fun `report submission failure emits a curation-failed snackbar message`() = runTest(mainDispatcherRule.testDispatcher) {
        loadThreeCards()
        val curationRepository = FakeCurationRepository()
        curationRepository.upsertResultToReturn = Result.failure(IllegalStateException("boom"))
        val viewModel = createViewModel(curationRepository)
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(openReportProblem(viewModel)))
        viewModel.onDialogEvent(
            DraftChange(reportDraft(viewModel).withAction(CurationAction.Delete, isChecked = true))
        )

        viewModel.messages.test {
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()

            awaitItem() shouldBe CurationSubmissionFailed
        }
    }

    @Test
    fun `a completed Rated session seals cardResults with one entry per distinct card and the abandoned flag clear`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onAttemptRating(FlashcardAttemptRating.Correct)
            viewModel.onAttemptRating(FlashcardAttemptRating.Correct)

            viewModel.events.test {
                viewModel.onAttemptRating(FlashcardAttemptRating.Correct)
                val destination = awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>()

                destination.route.abandoned shouldBe false
                destination.route.cardIds.toSet() shouldBe setOf("card-1", "card-2", "card-3")
                destination.route.cardStates shouldBe List(3) { FlashcardStudyProgressState.Mastered }
            }
        }

    @Test
    fun `an abandoned Rated session's cardResults holds only cards that completed at least one Attempt`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()
            // card-1 resolves Mastered; card-2 becomes current but is never rated; card-3 is never drawn to.
            viewModel.onAttemptRating(FlashcardAttemptRating.Correct)
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>()

                destination.route.abandoned shouldBe true
                destination.route.cardIds shouldBe listOf("card-1")
                destination.route.cardStates shouldBe listOf(FlashcardStudyProgressState.Mastered)
            }
        }

    @Test
    fun `a card that received only a silence timeout is absent from cardResults`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()
            emitSilenceTimeout()
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>()

                destination.route.cardIds shouldNotContain "card-1"
            }
        }

    @Test
    fun `abandoning mid re-insertion force-resolves a card with a completed Attempt using its best-rating-so-far`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            val viewModel = createViewModel()
            advanceUntilIdle()
            // Attempts limit defaults to 3: one PartiallyCorrect re-inserts card-1 rather than
            // resolving it — still mid re-insertion, not yet Terminal, when the session is abandoned.
            viewModel.onAttemptRating(FlashcardAttemptRating.PartiallyCorrect)
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>()

                val index = destination.route.cardIds.indexOf("card-1")
                index shouldNotBe -1
                destination.route.cardStates[index] shouldBe FlashcardStudyProgressState.Partial
                destination.route.cardAttemptsUsed?.get(index) shouldBe 1
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

            clockInstant = FIXED_INSTANT.plusSeconds(42)
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>()

                destination.route.durationSeconds shouldBe 42
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
                val destination = awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>()

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
                val destination = awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>()

                destination.route.durationSeconds shouldBe 1_200
            }
        }

    private companion object {
        const val FIXED_SEED = 42L
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
    var updateQueueCalls = 0
    var lastUpdateQueueCards: List<Flashcard>? = null
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

    override fun updateQueue(cards: List<Flashcard>) {
        updateQueueCalls++
        lastUpdateQueueCards = cards
    }
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
