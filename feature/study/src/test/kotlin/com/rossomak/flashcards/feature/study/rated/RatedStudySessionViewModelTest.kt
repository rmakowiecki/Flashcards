package com.rossomak.flashcards.feature.study.rated

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.AppPermission
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.AudioModeChanged
import com.rossomak.flashcards.core.domain.model.AudioEnvironmentSignal.FocusChanged
import com.rossomak.flashcards.core.domain.model.AudioMode
import com.rossomak.flashcards.core.domain.model.CaptureEvent
import com.rossomak.flashcards.core.domain.model.CardProgressEntry
import com.rossomak.flashcards.core.domain.model.CurationAction
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.FocusChange
import com.rossomak.flashcards.core.domain.model.GradingFailureReason
import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.core.domain.model.PlaybackEvent
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import com.rossomak.flashcards.core.domain.model.SpokenNotice
import com.rossomak.flashcards.core.domain.model.StudySessionConfig
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressDetails
import com.rossomak.flashcards.core.domain.model.TransportCommand
import com.rossomak.flashcards.core.domain.model.TransportCommandType
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGrade
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGradingEvent
import com.rossomak.flashcards.core.domain.model.VoiceAnswerPhase
import com.rossomak.flashcards.core.domain.model.VoiceCaptureFailureReason
import com.rossomak.flashcards.core.domain.model.VoicePlaybackState
import com.rossomak.flashcards.core.domain.model.VoiceSettings
import com.rossomak.flashcards.core.domain.repository.CurationRepository
import com.rossomak.flashcards.core.domain.repository.FakeAudioInterruptionGateway
import com.rossomak.flashcards.core.domain.repository.FakeCardProgressRepository
import com.rossomak.flashcards.core.domain.repository.FakeCurationRepository
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import com.rossomak.flashcards.core.domain.repository.FakePermissionGateway
import com.rossomak.flashcards.core.domain.repository.FakeStudyVoicePlaybackGateway
import com.rossomak.flashcards.core.domain.repository.FakeVoiceAnswerGradingRepository
import com.rossomak.flashcards.core.domain.repository.FakeVoiceCaptureGateway
import com.rossomak.flashcards.core.domain.session.MIN_TRANSCRIPT_DISPLAY
import com.rossomak.flashcards.core.domain.session.NOTICE_TAIL
import com.rossomak.flashcards.core.domain.session.RELEASE_LINGER
import com.rossomak.flashcards.core.domain.session.RatedSessionReducer
import com.rossomak.flashcards.core.domain.session.RatedStudySessionCoordinator
import com.rossomak.flashcards.core.domain.session.SILENCE_TIMEOUT
import com.rossomak.flashcards.core.domain.usecase.GetFlashcardsUseCase
import com.rossomak.flashcards.core.domain.usecase.GetSessionStartDataUseCase
import com.rossomak.flashcards.core.domain.usecase.GetSubcategoryProgressDetailsUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveVoiceAnswerLevelUseCase
import com.rossomak.flashcards.core.domain.usecase.SubmitCurationReportUseCase
import com.rossomak.flashcards.core.ui.composables.FlashcardsAttemptSlotState
import com.rossomak.flashcards.core.ui.composables.voice.FlashcardsVoiceCaptureIndicatorDefaults
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.DraftChange
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.navigation.RouteDecoder
import com.rossomak.flashcards.core.ui.voice.VoiceSettingsController
import com.rossomak.flashcards.core.ui.voice.VoiceSettingsDraftState
import com.rossomak.flashcards.feature.study.RatedStudySessionRoute
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.CurrentCardExtendedContext
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.ExitSession
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.ReportCurrentCardProblem
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.CurationReportFailed
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerCaptureUnavailable
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerGradingOffline
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerGradingPause
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerGradingServiceError
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerMicPermissionRevoked
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerSilencePause
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerSilenceSkip
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoicePlaybackUnavailable
import com.rossomak.flashcards.testutil.MainDispatcherRule
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
    private val getSubcategoryProgressDetails = GetSubcategoryProgressDetailsUseCase(cardProgressRepository)
    private val getSessionStartData = GetSessionStartDataUseCase(getFlashcards, getSubcategoryProgressDetails)
    private val playbackGateway = FakeStudyVoicePlaybackGateway()
    private val captureGateway = FakeVoiceCaptureGateway()
    private val interruptionGateway = FakeAudioInterruptionGateway()
    private val gradingRepository = FakeVoiceAnswerGradingRepository()
    private val permissionGateway = FakePermissionGateway().apply {
        statuses.value = mapOf(AppPermission.RecordAudio to PermissionStatus.Granted)
    }
    private val clock = MutableClock(FIXED_INSTANT)
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

    private var currentRoute: RatedStudySessionRoute = route

    private fun stubRoute(route: RatedStudySessionRoute) {
        currentRoute = route
        every { RouteDecoder.decode(any<() -> RatedStudySessionRoute>()) } returns route
    }

    /** Turns voice answering on for whatever the route currently routes. */
    private fun enableVoiceAnswering() {
        stubRoute(currentRoute.copy(voiceAnsweringEnabled = true))
    }

    /**
     * The real coordinator and reducer on the test fixtures, with a fixed Random, a settable clock
     * and the test scheduler's time source.
     */
    private fun createViewModel(curationRepository: CurationRepository = FakeCurationRepository()): RatedStudySessionViewModel =
        RatedStudySessionViewModel(
            savedStateHandle,
            SubmitCurationReportUseCase(curationRepository),
            ObserveVoiceAnswerLevelUseCase(captureGateway),
            RatedStudySessionCoordinator(
                getSessionStartData = getSessionStartData,
                playbackGateway = playbackGateway,
                captureGateway = captureGateway,
                interruptionGateway = interruptionGateway,
                gradingRepository = gradingRepository,
                permissionGateway = permissionGateway,
                reducer = RatedSessionReducer(Random(FIXED_SEED), mockk(relaxed = true)),
                clock = clock,
                timeSource = mainDispatcherRule.testDispatcher.scheduler.timeSource,
                logger = mockk(relaxed = true),
            ),
            voiceSettingsController,
        )

    /** A voice-answering session on three cards, loaded and reading its first question. */
    private fun TestScope.createVoiceViewModel(): RatedStudySessionViewModel {
        loadThreeCards()
        enableVoiceAnswering()
        return createViewModel().also { advanceUntilIdle() }
    }

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

    /** Reveals the first card's answer and rates it Correct, so the session has one Studied card. */
    private fun rateFirstCardCorrect(viewModel: RatedStudySessionViewModel) {
        viewModel.onShowAnswer()
        viewModel.onAttemptRating(FlashcardAttemptRating.Correct)
    }

    /** Rates every other head Correct until [cardId] is presented again; fails instead of looping forever. */
    private fun rateCorrectUntilPresented(viewModel: RatedStudySessionViewModel, cardId: String) {
        repeat(MAX_CARDS_BEFORE_REAPPEARING) {
            if (viewModel.state.value.currentCard?.id == cardId) return
            viewModel.onAttemptRating(FlashcardAttemptRating.Correct)
        }
        viewModel.state.value.currentCard?.id shouldBe cardId
    }

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
    fun `a failed card load returns to Preview`() = runTest(mainDispatcherRule.testDispatcher) {
        flashcardRepository.flashcardsBySubcategory[subcategoryId] = Result.failure(IllegalStateException("boom"))
        val viewModel = createViewModel()

        viewModel.events.test {
            advanceUntilIdle()

            awaitItem() shouldBe RatedStudySessionDestination.Back
        }
    }

    @Test
    fun `a load that finds none of the routed cards returns to Preview`() = runTest(mainDispatcherRule.testDispatcher) {
        flashcardRepository.flashcardsBySubcategory[subcategoryId] = Result.success(emptyList())
        val viewModel = createViewModel()

        viewModel.events.test {
            advanceUntilIdle()

            awaitItem() shouldBe RatedStudySessionDestination.Back
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
    fun `a Rated card with an existing Mastered entry has its previously-mastered flag set`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            cardProgressRepository.seed(
                SubcategoryProgressDetails(
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
                SubcategoryProgressDetails(
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
    fun `a failed progress read still produces a running session with every previously-mastered flag false and no error shown`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadThreeCards()
            cardProgressRepository.resultToReturn = Result.failure(IllegalStateException("offline"))
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.value.isLoading shouldBe false
            viewModel.onAttemptRating(FlashcardAttemptRating.Failed)
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>()

                destination.route.cardWasPreviouslyMastered?.all { it == false } shouldBe true
            }
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
    fun `the completed count increases on any Terminal State`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.flashcardsBySubcategory[subcategoryId] = Result.success(
                listOf(flashcard("card-1"), flashcard("card-2")),
            )
            stubRoute(route.copy(cardIds = listOf("card-1", "card-2"), ratedAttempts = 1))
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onAttemptRating(FlashcardAttemptRating.Failed)
            viewModel.state.value.completedCount shouldBe 1

            viewModel.onAttemptRating(FlashcardAttemptRating.PartiallyCorrect)
            viewModel.state.value.completedCount shouldBe 2
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
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onAttemptRating(FlashcardAttemptRating.Failed)
            // The card that just went to the back of the queue is not the head any more, so cycle
            // through the others (Correct finishes them immediately) until it resurfaces.
            rateCorrectUntilPresented(viewModel, "card-1")

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

            val firstViewModel = createViewModel()
            advanceUntilIdle()
            ratingSequence.forEach(firstViewModel::onAttemptRating)
            val firstOrder = firstViewModel.state.value.flashcards.map { it.id }

            val secondViewModel = createViewModel()
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
        rateFirstCardCorrect(viewModel)
        viewModel.onDialogEvent(Open(ExitSession))

        viewModel.onDialogEvent(Confirm)

        viewModel.state.value.activeDialog shouldBe null
        viewModel.events.test { awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>() }
    }

    @Test
    fun `confirming the exit dialog before any rating navigates back, not to the summary`() = runTest(mainDispatcherRule.testDispatcher) {
        loadThreeCards()
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onShowAnswer()
        viewModel.onDialogEvent(Open(ExitSession))

        viewModel.onDialogEvent(Confirm)

        viewModel.events.test { awaitItem() shouldBe RatedStudySessionDestination.Back }
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
    fun `a play during a call is ignored and shows the paused during a call message`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()
        interruptionGateway.emit(AudioModeChanged(AudioMode.InCall))
        interruptionGateway.emit(FocusChanged(FocusChange.LossTransient))
        advanceUntilIdle()

        viewModel.messages.test {
            viewModel.onVoicePlayPause()
            advanceUntilIdle()

            awaitItem() shouldBe RatedStudySessionMessage.PlayIgnoredDuringCall
        }
        viewModel.state.value.isVoicePlaying shouldBe false
    }

    @Test
    fun `a call that starts shows the message once, and the next call shows it again`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()

        viewModel.messages.test {
            interruptionGateway.emit(AudioModeChanged(AudioMode.Ringtone))
            advanceUntilIdle()
            awaitItem() shouldBe RatedStudySessionMessage.PlayIgnoredDuringCall
            viewModel.state.value.availableTransportCommands shouldBe emptySet()

            interruptionGateway.emit(AudioModeChanged(AudioMode.InCall))
            advanceUntilIdle()
            expectNoEvents()

            interruptionGateway.emit(AudioModeChanged(AudioMode.Normal))
            advanceUntilIdle()

            interruptionGateway.emit(AudioModeChanged(AudioMode.Ringtone))
            advanceUntilIdle()
            awaitItem() shouldBe RatedStudySessionMessage.PlayIgnoredDuringCall
        }
    }

    @Test
    fun `a session opened while a call rings shows the message once and offers no command`() = runTest(mainDispatcherRule.testDispatcher) {
        loadThreeCards()
        enableVoiceAnswering()
        interruptionGateway.emit(AudioModeChanged(AudioMode.Ringtone))
        val viewModel = createViewModel()

        viewModel.messages.test {
            advanceUntilIdle()
            awaitItem() shouldBe RatedStudySessionMessage.PlayIgnoredDuringCall
            expectNoEvents()
        }
        viewModel.state.value.availableTransportCommands shouldBe emptySet()
    }

    @Test
    fun `an unavailable voice engine pauses the session on its voice sheet instead of falling back to manual`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()

        viewModel.messages.test {
            playbackGateway.emit(PlaybackEvent.EngineUnavailable)
            advanceUntilIdle()

            awaitItem() shouldBe VoicePlaybackUnavailable
        }
        with(viewModel.state.value) {
            isVoiceActive shouldBe false
            isVoicePlaying shouldBe false
            isVoiceAnsweringSession shouldBe true
            isVoiceAnswerPaused shouldBe true
            isVoiceEngineUnavailable shouldBe true
        }
    }

    @Test
    fun `onVoiceNext skips the presented card and reads the next question`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()

        viewModel.onVoiceNext()
        advanceUntilIdle()

        viewModel.state.value.currentCard?.id shouldBe "card-2"
        viewModel.state.value.currentCardRatings shouldBe emptyList()
        playbackGateway.presentedQuestions.size shouldBe 1
    }

    @Test
    fun `onVoicePrevious restarts the presented card's question`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()

        viewModel.onVoicePrevious()

        playbackGateway.presentedQuestions.size shouldBe 1
        viewModel.state.value.currentCard?.id shouldBe "card-1"
    }

    @Test
    fun `onVoicePlayPause pauses during normal playback`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()

        viewModel.onVoicePlayPause()

        playbackGateway.pauseCount shouldBe 1
    }

    @Test
    fun `opening report, learn more or exit session never pauses playback`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()

        listOf(openReportProblem(viewModel), CurrentCardExtendedContext(EXTENDED_CONTEXT), ExitSession).forEach { dialog ->
            viewModel.onDialogEvent(Open(dialog))
            runCurrent()

            playbackGateway.pauseCount shouldBe 0
            viewModel.onDialogEvent(Dismiss)
            runCurrent()
        }
    }

    @Test
    fun `a dialog opened while listening holds after the silence notice, and closing it plays the next question after 500 ms`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()
            finishQuestion()
            viewModel.onDialogEvent(Open(CurrentCardExtendedContext(EXTENDED_CONTEXT)))

            viewModel.messages.test {
                advanceTimeBy(SILENCE_TIMEOUT)
                runCurrent()
                awaitItem() shouldBe VoiceAnswerSilenceSkip
            }
            finishNotice()
            viewModel.state.value.currentCard?.id shouldBe "card-1"
            playbackGateway.presentedQuestions.size shouldBe 0

            viewModel.onDialogEvent(Dismiss)
            advanceTimeBy(RELEASE_LINGER - 1.milliseconds)
            viewModel.state.value.currentCard?.id shouldBe "card-1"
            advanceTimeBy(2.milliseconds)

            viewModel.state.value.currentCard?.id shouldBe "card-2"
            playbackGateway.presentedQuestions.size shouldBe 1
            viewModel.state.value.isVoicePlaying shouldBe true
        }

    @Test
    fun `a dialog opened during the grading feedback holds after it, and closing it plays the next card after 500 ms`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()
            emitGradeNotice(gradePercent = CORRECT_GRADE_PERCENT)
            viewModel.onDialogEvent(Open(openReportProblem(viewModel)))

            finishNotice()
            viewModel.state.value.currentCard?.id shouldBe "card-1"
            playbackGateway.presentedQuestions.size shouldBe 0

            viewModel.onDialogEvent(Dismiss)
            advanceTimeBy(RELEASE_LINGER + 1.milliseconds)

            viewModel.state.value.currentCard?.id shouldBe "card-2"
            playbackGateway.presentedQuestions.size shouldBe 1
        }

    @Test
    fun `closing a dialog without a hold changes nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()
        viewModel.onDialogEvent(Open(ExitSession))

        viewModel.onDialogEvent(Dismiss)
        advanceUntilIdle()

        viewModel.state.value.currentCard?.id shouldBe "card-1"
        playbackGateway.presentedQuestions.size shouldBe 0
    }

    @Test
    fun `an external play at a hold dismisses the dialog, drops its draft and plays the next card at once`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()
            emitGradeNotice(gradePercent = CORRECT_GRADE_PERCENT)
            viewModel.onDialogEvent(Open(openReportProblem(viewModel)))
            viewModel.onDialogEvent(DraftChange(reportDraft(viewModel).withAction(CurationAction.Delete, isChecked = true)))
            finishNotice()

            playbackGateway.emitExternal(TransportCommand.Play)
            runCurrent()

            viewModel.state.value.activeDialog shouldBe null
            viewModel.state.value.currentCard?.id shouldBe "card-2"
            playbackGateway.presentedQuestions.size shouldBe 1
        }

    @Test
    fun `an external play while voice answering is paused dismisses the dialog and resumes voice answering`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()
            repeat(3) { emitSilenceTimeout() }
            viewModel.state.value.isVoiceAnswerPaused shouldBe true
            viewModel.onDialogEvent(Open(ExitSession))

            playbackGateway.emitExternal(TransportCommand.Play)
            runCurrent()

            viewModel.state.value.activeDialog shouldBe null
            viewModel.state.value.isVoiceAnswerPaused shouldBe false
            captureGateway.isVoiceAnsweringStarted shouldBe true
        }

    @Test
    fun `an external pause keeps the dialog open`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()
        viewModel.onDialogEvent(Open(ExitSession))

        playbackGateway.emitExternal(TransportCommand.Pause)
        runCurrent()

        viewModel.state.value.activeDialog shouldBe ExitSession
    }

    @Test
    fun `an external next ignored while listening leaves the dialog open`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()
        finishQuestion()
        viewModel.onDialogEvent(Open(ExitSession))

        playbackGateway.emitExternal(TransportCommand.Next)
        runCurrent()

        viewModel.state.value.activeDialog shouldBe ExitSession
        viewModel.state.value.currentCard?.id shouldBe "card-1"
    }

    @Test
    fun `confirming exit session while held ends the session abandoned without moving on`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()
        emitGradeNotice(gradePercent = CORRECT_GRADE_PERCENT)
        viewModel.onDialogEvent(Open(ExitSession))
        finishNotice()

        viewModel.events.test {
            viewModel.onDialogEvent(Confirm)
            runCurrent()

            awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>().route.abandoned shouldBe true
        }
        playbackGateway.presentedQuestions.size shouldBe 0
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
            val viewModel = createVoiceViewModel()
            viewModel.onDialogEvent(Open(StudySessionDialog.SessionVoiceSettings()))
            val draft = (viewModel.state.value.activeDialog as StudySessionDialog.SessionVoiceSettings).draftState
                .copy(draftSpeed = 1.5f, draftVoiceId = "voice-1")
            viewModel.onDialogEvent(DraftChange(StudySessionDialog.SessionVoiceSettings(draft)))

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
    fun `a routed voice-answering choice bootstraps voice answering on entry`() =
        runTest(mainDispatcherRule.testDispatcher) {
            createVoiceViewModel()

            playbackGateway.startCalls.size shouldBe 1
            playbackGateway.startCalls.single().cardIds shouldBe route.cardIds
            playbackGateway.startCalls.single().isVoiceAnsweringSession shouldBe true
            captureGateway.isVoiceAnsweringStarted shouldBe true
        }

    @Test
    fun `voice answering off in the route never starts the voice gateway`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(route.copy(voiceAnsweringEnabled = false))
        loadThreeCards()

        createViewModel()
        advanceUntilIdle()

        playbackGateway.startCalls.size shouldBe 0
        captureGateway.startVoiceAnsweringCount shouldBe 0
    }

    private fun reportDraft(viewModel: RatedStudySessionViewModel): ReportCurrentCardProblem =
        viewModel.state.value.activeDialog as ReportCurrentCardProblem

    @Test
    fun `onCleared stops the voice gateway`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onCleared()

        playbackGateway.stopCount shouldBe 1
    }

    @Test
    fun `voice answer state from the gateway is surfaced in screen state`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()

        emitGradeNotice(gradePercent = CORRECT_GRADE_PERCENT)

        viewModel.state.value.lastVoiceAnswerGrade shouldBe VoiceAnswerGrade(SPOKEN_TRANSCRIPT, CORRECT_GRADE_PERCENT, GRADE_RATIONALE)
    }

    @Test
    fun `voice sheet mode is Transport while the question is read`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()

        viewModel.state.value.voiceAnswerPhase shouldBe VoiceAnswerPhase.WaitingForQuestion
        viewModel.state.value.voiceSheetMode shouldBe RatedVoiceSheetMode.Transport
    }

    @Test
    fun `voice sheet mode is Transport before any voice round`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.voiceSheetMode shouldBe RatedVoiceSheetMode.Transport
    }

    @Test
    fun `voice sheet mode is Listening while listening or hearing speech`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()

        finishQuestion()
        viewModel.state.value.voiceSheetMode shouldBe RatedVoiceSheetMode.Listening

        captureGateway.emit(CaptureEvent.SpeechStarted)
        runCurrent()
        viewModel.state.value.voiceSheetMode shouldBe RatedVoiceSheetMode.Listening
    }

    @Test
    fun `voice sheet mode is Pending while the microphone is prepared, then Listening once it records`() = runTest(mainDispatcherRule.testDispatcher) {
        captureGateway.reportsMicrophoneOpened = false
        val viewModel = createVoiceViewModel()

        finishQuestion()
        viewModel.state.value.voiceAnswerPhase shouldBe VoiceAnswerPhase.Listening
        viewModel.state.value.voiceSheetMode shouldBe RatedVoiceSheetMode.Pending

        captureGateway.emit(CaptureEvent.MicrophoneOpened)
        runCurrent()
        viewModel.state.value.voiceSheetMode shouldBe RatedVoiceSheetMode.Listening
    }

    @Test
    fun `voiceSheetModeOf shows Pending while listening with the microphone not yet open`() {
        voiceSheetModeOf(
            voiceAnswerPhase = VoiceAnswerPhase.Listening,
            isMicrophoneOpen = false,
            isVoiceAnswerPaused = false,
            isShortNoticeSpeaking = false,
            sanitizedTranscript = null,
            lastGrade = null,
        ) shouldBe RatedVoiceSheetMode.Pending
    }

    @Test
    fun `voiceSheetModeOf shows Listening while listening with the microphone open`() {
        voiceSheetModeOf(
            voiceAnswerPhase = VoiceAnswerPhase.Listening,
            isMicrophoneOpen = true,
            isVoiceAnswerPaused = false,
            isShortNoticeSpeaking = false,
            sanitizedTranscript = null,
            lastGrade = null,
        ) shouldBe RatedVoiceSheetMode.Listening
    }

    @Test
    fun `voice sheet mode is Pending while grading without a transcript`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()

        answer(
            flow {
                delay(TRANSCRIPT_DELAY)
                emit(VoiceAnswerGradingEvent.TranscriptReady(" "))
                awaitCancellation()
            },
        )
        viewModel.state.value.voiceSheetMode shouldBe RatedVoiceSheetMode.Pending

        advanceTimeBy(TRANSCRIPT_DELAY)
        runCurrent()
        viewModel.state.value.voiceSheetMode shouldBe RatedVoiceSheetMode.Pending
    }

    @Test
    fun `voice sheet mode is GradingWithTranscript while grading with a transcript`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()

        answer(
            flow {
                emit(VoiceAnswerGradingEvent.TranscriptReady(SPOKEN_TRANSCRIPT))
                awaitCancellation()
            },
        )

        viewModel.state.value.voiceSheetMode shouldBe RatedVoiceSheetMode.GradingWithTranscript(SPOKEN_TRANSCRIPT)
    }

    @Test
    fun `voice sheet mode is Graded as Failed just below the Partial band`() = runTest(mainDispatcherRule.testDispatcher) {
        voiceSheetModeWhileSpeakingGrade(gradePercent = 39) shouldBe RatedVoiceSheetMode.Graded(FlashcardAttemptRating.Failed, GRADE_RATIONALE)
    }

    @Test
    fun `voice sheet mode is Graded as Partial at the bottom of the Partial band`() = runTest(mainDispatcherRule.testDispatcher) {
        voiceSheetModeWhileSpeakingGrade(gradePercent = 40) shouldBe RatedVoiceSheetMode.Graded(FlashcardAttemptRating.PartiallyCorrect, GRADE_RATIONALE)
    }

    @Test
    fun `voice sheet mode is Graded as Partial at the top of the Partial band`() = runTest(mainDispatcherRule.testDispatcher) {
        voiceSheetModeWhileSpeakingGrade(gradePercent = 79) shouldBe RatedVoiceSheetMode.Graded(FlashcardAttemptRating.PartiallyCorrect, GRADE_RATIONALE)
    }

    @Test
    fun `voice sheet mode is Graded as Correct at the bottom of the Correct band`() = runTest(mainDispatcherRule.testDispatcher) {
        voiceSheetModeWhileSpeakingGrade(gradePercent = 80) shouldBe RatedVoiceSheetMode.Graded(FlashcardAttemptRating.Correct, GRADE_RATIONALE)
    }

    @Test
    fun `voice sheet mode is Pending while speaking a notice without a grade`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()

        startSilenceNotice()

        viewModel.state.value.voiceAnswerPhase shouldBe VoiceAnswerPhase.SpeakingNotice
        viewModel.state.value.voiceSheetMode shouldBe RatedVoiceSheetMode.Pending
    }

    @Test
    fun `a silence skip shows Pending during its notice with the skip message, then Transport`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()

        viewModel.messages.test {
            startSilenceNotice()

            awaitItem() shouldBe VoiceAnswerSilenceSkip
            viewModel.state.value.voiceSheetMode shouldBe RatedVoiceSheetMode.Pending
        }
        finishNotice()

        viewModel.state.value.voiceSheetMode shouldBe RatedVoiceSheetMode.Transport
        viewModel.state.value.isVoiceAnswerPaused shouldBe false
    }

    @Test
    fun `a pausing silence shows Pending until its notice finishes, then the paused Transport`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()
        repeat(2) { emitSilenceTimeout() }

        viewModel.messages.test {
            startSilenceNotice()

            awaitItem() shouldBe VoiceAnswerSilencePause
        }
        // The pause stops voice answering while its notice keeps playing.
        viewModel.state.value.isVoiceAnswerPaused shouldBe true
        viewModel.state.value.voiceSheetMode shouldBe RatedVoiceSheetMode.Pending

        finishNotice()

        viewModel.state.value.voiceSheetMode shouldBe RatedVoiceSheetMode.Transport
    }

    @Test
    fun `a capture failure shows Pending until its notice finishes, then the paused Transport`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()

        viewModel.messages.test {
            captureGateway.emit(CaptureEvent.CaptureFailed(VoiceCaptureFailureReason.BluetoothMicUnavailable))
            runCurrent()

            awaitItem() shouldBe VoiceAnswerCaptureUnavailable
        }
        viewModel.state.value.isVoiceAnswerPaused shouldBe true
        viewModel.state.value.voiceSheetMode shouldBe RatedVoiceSheetMode.Pending

        finishNotice()

        viewModel.state.value.voiceSheetMode shouldBe RatedVoiceSheetMode.Transport
    }

    @Test
    fun `a grading failure without a connection shows Pending with the offline message`() = runTest(mainDispatcherRule.testDispatcher) {
        messageAndSheetModeForGradingFailure(GradingFailureReason.NoConnection) shouldBe (VoiceAnswerGradingOffline to RatedVoiceSheetMode.Pending)
    }

    @Test
    fun `a grading service failure shows Pending with the service error message`() = runTest(mainDispatcherRule.testDispatcher) {
        messageAndSheetModeForGradingFailure(GradingFailureReason.ServiceError) shouldBe (VoiceAnswerGradingServiceError to RatedVoiceSheetMode.Pending)
    }

    private suspend fun TestScope.messageAndSheetModeForGradingFailure(failure: GradingFailureReason): Pair<RatedStudySessionMessage, RatedVoiceSheetMode> {
        val viewModel = createVoiceViewModel()
        lateinit var message: RatedStudySessionMessage
        viewModel.messages.test {
            answer(
                flow {
                    emit(VoiceAnswerGradingEvent.TranscriptReady(SPOKEN_TRANSCRIPT))
                    emit(VoiceAnswerGradingEvent.Failed(failure))
                },
            )
            advanceTimeBy(MIN_TRANSCRIPT_DISPLAY)
            runCurrent()
            message = awaitItem()
        }
        return message to viewModel.state.value.voiceSheetMode
    }

    private fun TestScope.voiceSheetModeWhileSpeakingGrade(gradePercent: Int): RatedVoiceSheetMode {
        val viewModel = createVoiceViewModel()

        emitGradeNotice(gradePercent = gradePercent)

        return viewModel.state.value.voiceSheetMode
    }

    @Test
    fun `voice sheet mode is Transport while paused, whatever the phase`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()
        captureGateway.emit(CaptureEvent.CaptureFailed(VoiceCaptureFailureReason.PermissionMissing(detail = null)))
        runCurrent()
        finishNotice()

        // A question read while paused never reopens the microphone.
        playbackGateway.play()
        finishQuestion()

        viewModel.state.value.isVoiceAnswerPaused shouldBe true
        viewModel.state.value.voiceSheetMode shouldBe RatedVoiceSheetMode.Transport
    }

    @Test
    fun `a headset pause during the feedback shows the paused transport row, and play reads the feedback again as Graded`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()
            emitGradeNotice(gradePercent = CORRECT_GRADE_PERCENT)

            playbackGateway.emitExternal(TransportCommand.Pause)
            runCurrent()

            with(viewModel.state.value) {
                voiceSheetMode shouldBe RatedVoiceSheetMode.Transport
                currentCard?.id shouldBe "card-1"
                isVoicePlaying shouldBe false
                availableTransportCommands shouldBe setOf(TransportCommandType.Play, TransportCommandType.Next)
            }

            viewModel.onVoicePlayPause()
            runCurrent()

            viewModel.state.value.voiceSheetMode.shouldBeInstanceOf<RatedVoiceSheetMode.Graded>()
            viewModel.state.value.currentCardRatings shouldBe listOf(FlashcardAttemptRating.Correct)
        }

    @Test
    fun `a pause while grading shows the paused transport row, and play goes back to the grading mode`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()
            answer(flow { emit(VoiceAnswerGradingEvent.TranscriptReady(SPOKEN_TRANSCRIPT)) })

            playbackGateway.emitExternal(TransportCommand.Pause)
            runCurrent()
            viewModel.state.value.voiceSheetMode shouldBe RatedVoiceSheetMode.Transport

            viewModel.onVoicePlayPause()
            runCurrent()
            viewModel.state.value.voiceSheetMode shouldBe RatedVoiceSheetMode.GradingWithTranscript(SPOKEN_TRANSCRIPT)
        }

    @Test
    fun `a tap on the feedback skips it and reads the next question`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()
        emitGradeNotice(gradePercent = CORRECT_GRADE_PERCENT)

        viewModel.onVoiceFeedbackSkip()
        runCurrent()

        viewModel.state.value.currentCard?.id shouldNotBe "card-1"
        viewModel.state.value.voiceSheetMode shouldBe RatedVoiceSheetMode.Transport
        playbackGateway.calls.last() shouldBe FakeStudyVoicePlaybackGateway.Call.PresentQuestion(0)
    }

    @Test
    fun `the sheet has no transport row while listening, grading or speaking a short notice, though pause is offered outside the app`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()
            val pauseOnly = setOf(TransportCommandType.Pause, TransportCommandType.Stop)

            finishQuestion()
            viewModel.state.value.voiceSheetMode shouldBe RatedVoiceSheetMode.Listening
            viewModel.state.value.availableTransportCommands shouldBe pauseOnly

            advanceTimeBy(SILENCE_TIMEOUT)
            runCurrent()
            viewModel.state.value.voiceSheetMode shouldBe RatedVoiceSheetMode.Pending
            viewModel.state.value.availableTransportCommands shouldBe pauseOnly
            finishNotice()

            answer(flow { emit(VoiceAnswerGradingEvent.TranscriptReady(SPOKEN_TRANSCRIPT)) })
            viewModel.state.value.voiceSheetMode shouldBe RatedVoiceSheetMode.GradingWithTranscript(SPOKEN_TRANSCRIPT)
            viewModel.state.value.availableTransportCommands shouldBe pauseOnly
        }

    @Test
    fun `previous is enabled at the question of a voice session`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()

        viewModel.state.value.availableTransportCommands shouldContain TransportCommandType.Previous
        viewModel.onVoicePrevious()

        playbackGateway.presentedQuestions.size shouldBe 1
    }

    @Test
    fun `voice bars levels start at rest and shape the gateway raw voice level`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.voiceBarsLevels.test {
            awaitItem() shouldBe List(FlashcardsVoiceCaptureIndicatorDefaults.BAR_COUNT) { 0f }
            // Lets stateIn subscribe upstream first; the fake's SharedFlow drops emissions with no subscriber.
            runCurrent()

            captureGateway.rawVoiceLevel.emit(SPOKEN_RAW_VOICE_LEVEL)
            advanceTimeBy(FlashcardsVoiceCaptureIndicatorDefaults.LEVEL_INTERVAL_MILLIS.milliseconds)
            runCurrent()

            awaitItem() shouldBe listOf(SPOKEN_RAW_VOICE_LEVEL, 0f, 0f, 0f, 0f)
        }
    }

    /** Reads the presented card's question to its end, which opens the listening window. */
    private fun TestScope.finishQuestion() {
        playbackGateway.finishQuestion()
        runCurrent()
    }

    /** Finishes the oldest spoken notice, then waits out the tail that follows an advancing one. */
    private fun TestScope.finishNotice() {
        playbackGateway.finishNotice()
        runCurrent()
        advanceTimeBy(NOTICE_TAIL)
        runCurrent()
    }

    /** A silence round up to its notice, which is still being spoken. */
    private fun TestScope.startSilenceNotice() {
        finishQuestion()
        advanceTimeBy(SILENCE_TIMEOUT)
        runCurrent()
    }

    /** A whole silence round: the question, the silence timeout, its notice and the tail. */
    private fun TestScope.emitSilenceTimeout() {
        startSilenceNotice()
        finishNotice()
    }

    /** Speaks an answer to the presented card, graded by [gradingFlow]. */
    private fun TestScope.answer(gradingFlow: Flow<VoiceAnswerGradingEvent>) {
        gradingRepository.gradingFlow = gradingFlow
        finishQuestion()
        captureGateway.emit(CaptureEvent.SpeechStarted)
        captureGateway.emit(CaptureEvent.SpeechEnded)
        captureGateway.emit(CaptureEvent.UtteranceCaptured(byteArrayOf(1)))
        runCurrent()
    }

    /** A grading-failure round up to its notice, which is still being spoken. */
    private fun TestScope.startGradingFailureNotice(failure: GradingFailureReason = GradingFailureReason.NoConnection) {
        answer(flow { emit(VoiceAnswerGradingEvent.Failed(failure)) })
    }

    /** A whole grading-failure round, notice and tail included. */
    private fun TestScope.emitGradingFailure(failure: GradingFailureReason = GradingFailureReason.NoConnection) {
        startGradingFailureNotice(failure)
        finishNotice()
    }

    /** A whole graded round, notice and tail included. */
    private fun TestScope.emitGrade(gradePercent: Int) {
        emitGradeNotice(gradePercent)
        finishNotice()
    }

    @Test
    fun `a voice grade drives the same Attempt increment and re-insertion as the equivalent manual rating`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()

            emitGrade(gradePercent = FAILED_GRADE_PERCENT)

            viewModel.state.value.currentCard?.id shouldNotBe "card-1"
            viewModel.state.value.flashcards.map { it.id } shouldContain "card-1"
        }

    /** A graded round up to its feedback notice, which is still being spoken. */
    private fun TestScope.emitGradeNotice(gradePercent: Int) {
        answer(
            flow {
                emit(VoiceAnswerGradingEvent.TranscriptReady(SPOKEN_TRANSCRIPT))
                emit(VoiceAnswerGradingEvent.Graded(VoiceAnswerGrade(SPOKEN_TRANSCRIPT, gradePercent, GRADE_RATIONALE)))
            },
        )
        advanceTimeBy(MIN_TRANSCRIPT_DISPLAY)
        runCurrent()
    }

    @Test
    fun `a voice grade shows its Rating on the attempt markers at once, while the card, progress and reveal wait for the notice`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()

            emitGradeNotice(gradePercent = PARTIAL_GRADE_PERCENT)

            with(viewModel.state.value) {
                currentCardRatings shouldBe listOf(FlashcardAttemptRating.PartiallyCorrect)
                currentCard?.id shouldBe "card-1"
                completedCount shouldBe 0
                isAnswerRevealed shouldBe true
            }
        }

    @Test
    fun `a voice Correct counts at once and keeps its marker on the graded card during the feedback, then switches to the next card`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()

            emitGradeNotice(gradePercent = CORRECT_GRADE_PERCENT)

            viewModel.state.value.currentCardRatings shouldBe listOf(FlashcardAttemptRating.Correct)
            viewModel.state.value.completedCount shouldBe 1

            finishNotice()

            with(viewModel.state.value) {
                currentCard?.id shouldBe "card-2"
                currentCardRatings shouldBe emptyList()
                completedCount shouldBe 1
                isAnswerRevealed shouldBe false
            }
        }

    @Test
    fun `a voice Failed that re-inserts the card shows the marker on the graded card, not the next head`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadTenCards()
            enableVoiceAnswering()
            val viewModel = createViewModel()
            advanceUntilIdle()

            emitGradeNotice(gradePercent = FAILED_GRADE_PERCENT)

            viewModel.state.value.currentCard?.id shouldBe "card-1"
            viewModel.state.value.currentCardRatings shouldBe listOf(FlashcardAttemptRating.Failed)
        }

    @Test
    fun `a voice Correct on the last card updates the marker at once and navigates to the summary only after the notice`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.flashcardsBySubcategory[subcategoryId] = Result.success(listOf(flashcard("card-1")))
            stubRoute(route.copy(cardIds = listOf("card-1"), voiceAnsweringEnabled = true))
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.events.test {
                emitGradeNotice(gradePercent = CORRECT_GRADE_PERCENT)

                viewModel.state.value.currentCardRatings shouldBe listOf(FlashcardAttemptRating.Correct)
                expectNoEvents()

                finishNotice()

                awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>()
            }
        }

    @Test
    fun `a silence timeout or grading failure leaves the attempt markers unchanged during its notice`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()
            // card-1 fails manually and comes back as the head with one Failed marker.
            viewModel.onAttemptRating(FlashcardAttemptRating.Failed)
            repeat(2) { viewModel.onAttemptRating(FlashcardAttemptRating.Correct) }
            viewModel.state.value.currentCard?.id shouldBe "card-1"
            val markersBefore = listOf(FlashcardAttemptRating.Failed)
            viewModel.state.value.currentCardRatings shouldBe markersBefore

            startSilenceNotice()
            viewModel.state.value.currentCardRatings shouldBe markersBefore
            finishNotice()

            startGradingFailureNotice()
            viewModel.state.value.currentCardRatings shouldBe markersBefore
        }

    @Test
    fun `a stale voice grade for a card other than the head leaves the attempt markers unchanged`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()
            answer(
                flow {
                    emit(VoiceAnswerGradingEvent.TranscriptReady(SPOKEN_TRANSCRIPT))
                    delay(TRANSCRIPT_DELAY)
                    emit(VoiceAnswerGradingEvent.Graded(VoiceAnswerGrade(SPOKEN_TRANSCRIPT, CORRECT_GRADE_PERCENT, GRADE_RATIONALE)))
                },
            )
            // The head moves on while card-1 is still being graded.
            viewModel.onAttemptRating(FlashcardAttemptRating.Correct)

            advanceTimeBy(MIN_TRANSCRIPT_DISPLAY + TRANSCRIPT_DELAY)
            runCurrent()

            viewModel.state.value.currentCard?.id shouldBe "card-2"
            viewModel.state.value.currentCardRatings shouldBe emptyList()
        }

    @Test
    fun `a grade in the Correct band finishes the card as Mastered, exactly as a manual Correct does`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.flashcardsBySubcategory[subcategoryId] = Result.success(listOf(flashcard("card-1")))
            stubRoute(route.copy(cardIds = listOf("card-1"), voiceAnsweringEnabled = true))
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.events.test {
                emitGrade(gradePercent = CORRECT_GRADE_PERCENT)
                val destination = awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>()
                destination.route.cardStates shouldBe listOf(FlashcardStudyProgressState.Mastered)
            }
        }

    @Test
    fun `a silence timeout leaves the card's Rating list and best rating unchanged, and re-queues the card`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadTenCards()
            enableVoiceAnswering()
            val viewModel = createViewModel()
            advanceUntilIdle()

            emitSilenceTimeout()

            viewModel.state.value.currentCard?.id shouldNotBe "card-1"
            viewModel.state.value.flashcards.map { it.id } shouldContain "card-1"
            rateCorrectUntilPresented(viewModel, "card-1")

            viewModel.state.value.currentCardRatings shouldBe emptyList()
        }

    @Test
    fun `a silence timeout re-queues within the Failed gap range`() = runTest(mainDispatcherRule.testDispatcher) {
        loadTenCards()
        enableVoiceAnswering()
        val viewModel = createViewModel()
        advanceUntilIdle()

        emitSilenceTimeout()

        val index = viewModel.state.value.flashcards.indexOfFirst { it.id == "card-1" }
        (index in StudySessionConfig.FAILED_REQUEUE_MIN_GAP..StudySessionConfig.FAILED_REQUEUE_MAX_GAP) shouldBe true
    }

    @Test
    fun `two silence timeouts do not pause the session, but the third does`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()

            repeat(2) { emitSilenceTimeout() }
            viewModel.state.value.isVoiceAnswerPaused shouldBe false

            emitSilenceTimeout()

            viewModel.state.value.isVoiceAnswerPaused shouldBe true
        }

    @Test
    fun `the consecutive silence counter resets on a graded answer, so silence-silence-grade-silence does not pause`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()

            repeat(2) { emitSilenceTimeout() }
            emitGrade(gradePercent = CORRECT_GRADE_PERCENT)

            emitSilenceTimeout()

            viewModel.state.value.isVoiceAnswerPaused shouldBe false
        }

    @Test
    fun `pausing after three silences stops playback, exposes the resume affordance, and records no outcome`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadFourCards()
            enableVoiceAnswering()
            val viewModel = createViewModel()
            advanceUntilIdle()
            val completedBefore = viewModel.state.value.completedCount
            val flashcardsBefore = viewModel.state.value.flashcards.map { it.id }

            // Independently reproduces the silence requeue's exact draw order/range with an
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
            playbackGateway.pauseCount shouldBe 1
            captureGateway.isVoiceAnsweringStarted shouldBe false
            viewModel.state.value.completedCount shouldBe completedBefore
            viewModel.state.value.flashcards.map { it.id } shouldBe expectedQueue
        }

    @Test
    fun `resuming continues from the same card with the counter reset`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()
        repeat(3) { emitSilenceTimeout() }
        val cardBeforePause = viewModel.state.value.currentCard?.id

        viewModel.onVoicePlayPause()
        runCurrent()

        viewModel.state.value.isVoiceAnswerPaused shouldBe false
        viewModel.state.value.currentCard?.id shouldBe cardBeforePause
        captureGateway.isVoiceAnsweringStarted shouldBe true

        // Counter reset: two more silences must not re-pause.
        repeat(2) { emitSilenceTimeout() }
        viewModel.state.value.isVoiceAnswerPaused shouldBe false
    }

    @Test
    fun `a silence timeout under the pause threshold emits the skip snackbar message`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()

            viewModel.messages.test {
                emitSilenceTimeout()

                awaitItem() shouldBe VoiceAnswerSilenceSkip
            }
        }

    @Test
    fun `the third consecutive silence timeout emits the pause snackbar message instead of the skip message`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()
            repeat(2) { emitSilenceTimeout() }

            viewModel.messages.test {
                emitSilenceTimeout()

                awaitItem() shouldBe VoiceAnswerSilencePause
            }
        }

    @Test
    fun `a grading failure re-queues the card within the Failed gap, records no Rating, and shows the next card after its notice`() =
        runTest(mainDispatcherRule.testDispatcher) {
            loadTenCards()
            enableVoiceAnswering()
            val viewModel = createViewModel()
            advanceUntilIdle()

            emitGradingFailure()

            viewModel.state.value.currentCard?.id shouldNotBe "card-1"
            val index = viewModel.state.value.flashcards.indexOfFirst { it.id == "card-1" }
            (index in StudySessionConfig.FAILED_REQUEUE_MIN_GAP..StudySessionConfig.FAILED_REQUEUE_MAX_GAP) shouldBe true
            rateCorrectUntilPresented(viewModel, "card-1")
            viewModel.state.value.currentCardRatings shouldBe emptyList()
        }

    @Test
    fun `a grading failure keeps showing its revealed card until the notice finishes, then shows the next card hidden`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()

        startGradingFailureNotice()

        viewModel.state.value.currentCard?.id shouldBe "card-1"
        viewModel.state.value.isAnswerRevealed shouldBe true

        finishNotice()

        viewModel.state.value.currentCard?.id shouldBe "card-2"
        viewModel.state.value.isAnswerRevealed shouldBe false
    }

    @Test
    fun `three grading failures in a row pause the session with the grading pause message`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()

            viewModel.messages.test {
                emitGradingFailure(GradingFailureReason.NoConnection)
                awaitItem() shouldBe VoiceAnswerGradingOffline
                emitGradingFailure(GradingFailureReason.ServiceError)
                awaitItem() shouldBe VoiceAnswerGradingServiceError
                viewModel.state.value.isVoiceAnswerPaused shouldBe false

                emitGradingFailure(GradingFailureReason.NoConnection)

                awaitItem() shouldBe VoiceAnswerGradingPause
            }
            viewModel.state.value.isVoiceAnswerPaused shouldBe true
            captureGateway.isVoiceAnsweringStarted shouldBe false
        }

    @Test
    fun `a pausing grading failure keeps showing its revealed card until the pause notice finishes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()
            repeat(2) { emitGradingFailure() }
            val pausingCardId = viewModel.state.value.currentCard?.id

            startGradingFailureNotice()

            viewModel.state.value.isVoiceAnswerPaused shouldBe true
            viewModel.state.value.currentCard?.id shouldBe pausingCardId
            viewModel.state.value.isAnswerRevealed shouldBe true

            finishNotice()

            viewModel.state.value.currentCard?.id shouldNotBe pausingCardId
            viewModel.state.value.isAnswerRevealed shouldBe false
        }

    @Test
    fun `a pausing silence keeps showing its card until the pause notice finishes`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()
        repeat(2) { emitSilenceTimeout() }
        val pausingCardId = viewModel.state.value.currentCard?.id

        startSilenceNotice()

        viewModel.state.value.currentCard?.id shouldBe pausingCardId

        finishNotice()

        viewModel.state.value.currentCard?.id shouldNotBe pausingCardId
    }

    @Test
    fun `a silence does not reset the grading failure count, so failure-silence-failure-failure pauses on the third failure`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()

            emitGradingFailure()
            emitSilenceTimeout()
            emitGradingFailure()
            viewModel.state.value.isVoiceAnswerPaused shouldBe false

            viewModel.messages.test {
                emitGradingFailure()

                awaitItem() shouldBe VoiceAnswerGradingPause
            }
            viewModel.state.value.isVoiceAnswerPaused shouldBe true
        }

    @Test
    fun `a grading failure does not reset the silence count, so silence-failure-silence-silence pauses on the third silence`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()

            emitSilenceTimeout()
            emitGradingFailure()
            emitSilenceTimeout()
            viewModel.state.value.isVoiceAnswerPaused shouldBe false

            viewModel.messages.test {
                emitSilenceTimeout()

                awaitItem() shouldBe VoiceAnswerSilencePause
            }
            viewModel.state.value.isVoiceAnswerPaused shouldBe true
        }

    @Test
    fun `a graded answer resets the grading failure count, so failure-failure-grade-failure does not pause`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()

            repeat(2) { emitGradingFailure() }
            emitGrade(gradePercent = CORRECT_GRADE_PERCENT)

            emitGradingFailure()

            viewModel.state.value.isVoiceAnswerPaused shouldBe false
        }

    @Test
    fun `onVoicePlayPause pauses rather than resumes when the player plays during a voice answering pause`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()
            repeat(3) { emitSilenceTimeout() }
            playbackGateway.state.value = VoicePlaybackState(isActive = true, isPlaying = true)
            runCurrent()
            val pausesBefore = playbackGateway.pauseCount

            viewModel.onVoicePlayPause()
            runCurrent()

            playbackGateway.pauseCount shouldBe pausesBefore + 1
            viewModel.state.value.isVoiceAnswerPaused shouldBe true
            captureGateway.isVoiceAnsweringStarted shouldBe false
        }

    @Test
    fun `resuming after a grading failure pause resets the grading failure count`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceViewModel()
        repeat(3) { emitGradingFailure() }

        viewModel.onVoicePlayPause()
        runCurrent()

        viewModel.state.value.isVoiceAnswerPaused shouldBe false
        repeat(2) { emitGradingFailure() }
        viewModel.state.value.isVoiceAnswerPaused shouldBe false
    }

    @Test
    fun `each grading failure's notice names its cause, and the third in a row announces the pause instead`() =
        runTest(mainDispatcherRule.testDispatcher) {
            createVoiceViewModel()

            emitGradingFailure(GradingFailureReason.NoConnection)
            emitGradingFailure(GradingFailureReason.ServiceError)
            emitGradingFailure(GradingFailureReason.NoConnection)

            playbackGateway.spokenNotices shouldBe listOf(
                SpokenNotice.GradingFailed(GradingFailureReason.NoConnection),
                SpokenNotice.GradingFailed(GradingFailureReason.ServiceError),
                SpokenNotice.GradingPause,
            )
        }

    @Test
    fun `a grading failure leaves the silence count untouched, so the next silence still announces the pause`() = runTest(mainDispatcherRule.testDispatcher) {
        createVoiceViewModel()
        repeat(2) { emitSilenceTimeout() }

        emitGradingFailure()
        emitSilenceTimeout()

        playbackGateway.spokenNotices.last() shouldBe SpokenNotice.SilencePause
    }

    @Test
    fun `a capture failure from a missing mic permission pauses the session like any other capture failure`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // Revoking in system Settings kills the process, so a capture-time SecurityException is
            // only a narrow race; it takes the recoverable path rather than a dedicated one.
            val viewModel = createVoiceViewModel()

            viewModel.messages.test {
                captureGateway.emit(CaptureEvent.CaptureFailed(VoiceCaptureFailureReason.PermissionMissing(detail = null)))
                runCurrent()

                awaitItem() shouldBe VoiceAnswerCaptureUnavailable
            }
            viewModel.events.test { expectNoEvents() }
            viewModel.state.value.isVoiceAnswerPaused shouldBe true
        }

    @Test
    fun `a session restored with the mic permission revoked before any rating returns to Preview`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // Revoking in system Settings kills the process; the restored route starts voice
            // answering again and finds the permission gone.
            permissionGateway.statuses.value = mapOf(AppPermission.RecordAudio to PermissionStatus.PermanentlyDenied)
            loadThreeCards()
            enableVoiceAnswering()
            val viewModel = createViewModel()

            viewModel.messages.test {
                advanceUntilIdle()

                awaitItem() shouldBe VoiceAnswerMicPermissionRevoked
            }
            viewModel.events.test {
                awaitItem() shouldBe RatedStudySessionDestination.Back
            }
            playbackGateway.startCalls.size shouldBe 0
        }

    @Test
    fun `a capture failure unrelated to mic permission pauses the session instead of ending it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()

            viewModel.messages.test {
                captureGateway.emit(CaptureEvent.CaptureFailed(VoiceCaptureFailureReason.BluetoothMicUnavailable))
                runCurrent()

                awaitItem() shouldBe VoiceAnswerCaptureUnavailable
            }
            viewModel.events.test { expectNoEvents() }
            viewModel.state.value.isVoiceAnswerPaused shouldBe true
            captureGateway.isVoiceAnsweringStarted shouldBe false
            playbackGateway.presentedQuestions.size shouldBe 1
        }

    @Test
    fun `isAnswerRevealed stays false while a silence-timeout notice is speaking`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()

            startSilenceNotice()
            runCurrent()

            viewModel.state.value.isAnswerRevealed shouldBe false
        }

    @Test
    fun `isAnswerRevealed stays true while a grading-failure notice is speaking`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()

            startGradingFailureNotice(GradingFailureReason.ServiceError)
            runCurrent()

            viewModel.state.value.isAnswerRevealed shouldBe true
        }

    @Test
    fun `isAnswerRevealed turns true while a real grade's notice is speaking`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()

            emitGradeNotice(gradePercent = CORRECT_GRADE_PERCENT)
            runCurrent()

            viewModel.state.value.isAnswerRevealed shouldBe true
        }

    @Test
    fun `the third silence's pause fires the instant its notice starts, before the notice finishes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            createVoiceViewModel()
            repeat(2) { emitSilenceTimeout() }

            startSilenceNotice()

            playbackGateway.speakingNotices shouldBe listOf(SpokenNotice.SilencePause)
            playbackGateway.pauseCount shouldBe 1
            captureGateway.isVoiceAnsweringStarted shouldBe false
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

            awaitItem() shouldBe CurationReportFailed
        }
        reportDraft(viewModel).isSubmitting shouldBe false
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
    fun `a card that received only a silence timeout is not Studied, so leaving returns to Preview`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceViewModel()
            emitSilenceTimeout()
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)

                awaitItem() shouldBe RatedStudySessionDestination.Back
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
            advanceUntilIdle()

            rateFirstCardCorrect(viewModel)
            clock.instant = FIXED_INSTANT.plusSeconds(42)
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>()

                destination.route.durationSeconds shouldBe 42
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
            rateFirstCardCorrect(viewModel)
            clock.instant = FIXED_INSTANT.plusSeconds(1_200)
            viewModel.onDialogEvent(Open(ExitSession))

            viewModel.events.test {
                viewModel.onDialogEvent(Confirm)
                val destination = awaitItem().shouldBeInstanceOf<RatedStudySessionDestination.Summary>()

                destination.route.durationSeconds shouldBe 1_200
            }
        }

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = instant
    }

    private companion object {
        const val FIXED_SEED = 42L
        const val EXTENDED_CONTEXT = "More about this card."
        val TRANSCRIPT_DELAY = 200.milliseconds
        const val MAX_CARDS_BEFORE_REAPPEARING = 10
        const val CORRECT_GRADE_PERCENT = 95
        const val PARTIAL_GRADE_PERCENT = 60
        const val FAILED_GRADE_PERCENT = 20
        const val SPOKEN_RAW_VOICE_LEVEL = 0.9f
        const val SPOKEN_TRANSCRIPT = "remember keeps state across recompositions"
        const val GRADE_RATIONALE = "You named the key difference."
        val FIXED_INSTANT: Instant = Instant.parse("2026-09-06T10:00:00Z")
    }
}
