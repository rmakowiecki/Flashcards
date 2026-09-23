package com.rossomak.flashcards.feature.study.preview

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.AppPermission
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FlashcardSortOrder
import com.rossomak.flashcards.core.domain.model.PermissionStatus
import com.rossomak.flashcards.core.domain.model.PermissionStatus.Denied
import com.rossomak.flashcards.core.domain.model.PermissionStatus.Granted
import com.rossomak.flashcards.core.domain.model.PermissionStatus.PermanentlyDenied
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.StudySessionConfig
import com.rossomak.flashcards.core.domain.model.StudySessionPreferences
import com.rossomak.flashcards.core.domain.model.VoiceSettings as SavedVoiceSettings
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import com.rossomak.flashcards.core.domain.repository.FakePermissionGateway
import com.rossomak.flashcards.core.domain.repository.FakeStudySessionPreferencesRepository
import com.rossomak.flashcards.core.domain.repository.FakeUserPreferencesRepository
import com.rossomak.flashcards.core.domain.usecase.FilterFlashcardsUseCase
import com.rossomak.flashcards.core.domain.usecase.GetFlashcardsUseCase
import com.rossomak.flashcards.core.domain.usecase.ObservePermissionStatusUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveStudySessionPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveUserPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.RequestPermissionUseCase
import com.rossomak.flashcards.core.domain.usecase.SampleQuickSessionSubcategoriesUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveStudySessionPreferenceUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveUserPreferenceUseCase
import com.rossomak.flashcards.core.domain.usecase.SelectSessionFlashcardsUseCase
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardFilters
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.DraftChange
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.navigation.RouteDecoder
import com.rossomak.flashcards.core.ui.voice.VoiceSettingsController
import com.rossomak.flashcards.core.ui.voice.VoiceSettingsDraftState
import com.rossomak.flashcards.feature.study.PreviewStudySessionRoute
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.FastSessionReadAloud
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.Filters
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.QuickSessionSubcategoryCountRange
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.RatedSessionMaxCardAttempts
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.RatedSessionPartialRatingCardRequeueing
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.RatedSessionVoiceAnswering
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.SessionCardCount
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.SessionCardsSortingOrder
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.SessionMode
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.SessionVoiceSettings
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.VoiceAnsweringInfo
import com.rossomak.flashcards.testutil.MainDispatcherRule
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreviewStudySessionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val savedStateHandle: SavedStateHandle = mockk()
    private val flashcardRepository = FakeFlashcardRepository()
    private val studySessionPreferencesRepository = FakeStudySessionPreferencesRepository()
    private val userPreferencesRepository = FakeUserPreferencesRepository()
    private val permissionGateway = FakePermissionGateway()
    private val voiceSettingsController: VoiceSettingsController = mockk(relaxed = true)

    /** Anything but [StudySessionConfig.DEFAULT_RATED_ATTEMPTS], so a commit is visible. */
    private val strictAttempts = StudySessionConfig.MIN_RATED_ATTEMPTS

    /** Anything but [StudySessionConfig.DEFAULT_LENGTH], so a seeded value is visible. */
    private val seededLength = StudySessionConfig.MIN_LENGTH

    /** Narrower than [StudySessionConfig.DEFAULT_SUBCATEGORY_COUNT_RANGE], so seeding it is visible. */
    private val narrowerSubcategoryCountRange = 1..2

    private val categoryId = "android"
    private val categoryName = "Android"
    private val subcategoryId = "android-compose"
    private val subcategoryName = "Compose"

    private val singleSubcategoryRoute = PreviewStudySessionRoute(
        categoryId = categoryId,
        categoryName = categoryName,
        subcategoryIds = listOf(subcategoryId),
        subcategoryNames = listOf(subcategoryName),
    )

    private val multiSubcategoryRoute = singleSubcategoryRoute.copy(
        subcategoryIds = listOf("android-compose", "android-coroutines"),
        subcategoryNames = listOf("Compose", "Coroutines"),
    )

    /** More candidates than [StudySessionConfig.DEFAULT_SUBCATEGORY_COUNT_RANGE]'s maximum. */
    private val quickSessionRoute = singleSubcategoryRoute.copy(
        subcategoryIds = (1..8).map { index -> "android-sub-$index" },
        subcategoryNames = (1..8).map { index -> "Sub $index" },
        isQuickSession = true,
    )

    @Before
    fun setUp() {
        mockkObject(RouteDecoder)
    }

    @After
    fun tearDown() {
        unmockkObject(RouteDecoder)
    }

    private fun stubRoute(route: PreviewStudySessionRoute) {
        every { RouteDecoder.decode(any<() -> PreviewStudySessionRoute>()) } returns route
    }

    /**
     * Fixed, not [Random.Default]: a real `Random` occasionally draws the same subset or
     * subcategory sample twice in a row, which would flake the `shouldNotBe` assertions on
     * reshuffle. This seed is verified (see the reshuffle tests below) to advance to a different
     * draw/sample on a second call.
     */
    private fun createViewModel(): PreviewStudySessionViewModel = PreviewStudySessionViewModel(
        savedStateHandle,
        SelectSessionFlashcardsUseCase(
            getFlashcards = GetFlashcardsUseCase(flashcardRepository),
            filterFlashcards = FilterFlashcardsUseCase(),
            random = Random(CARD_DRAW_RANDOM_SEED),
        ),
        SampleQuickSessionSubcategoriesUseCase(random = Random(SUBCATEGORY_SAMPLE_RANDOM_SEED)),
        ObserveStudySessionPreferencesUseCase(studySessionPreferencesRepository),
        SaveStudySessionPreferenceUseCase(studySessionPreferencesRepository),
        ObserveUserPreferencesUseCase(userPreferencesRepository),
        SaveUserPreferenceUseCase(userPreferencesRepository),
        ObservePermissionStatusUseCase(permissionGateway),
        RequestPermissionUseCase(permissionGateway),
        voiceSettingsController,
    )

    private fun flashcard(
        id: String,
        subcategoryId: String = this.subcategoryId,
        tags: List<String> = listOf("General"),
        difficulty: Int = 5
    ): Flashcard = Flashcard(
        id = id,
        subcategoryId = subcategoryId,
        tags = tags,
        question = "question-$id",
        answer = "answer-$id",
        difficulty = difficulty,
        questionCode = null,
        answerCode = null,
        questionSpoken = null,
        answerSpoken = null,
        extendedContext = null
    )

    @Test
    fun `selection caps card count at session size`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)
        flashcardRepository.flashcardsToReturn =
            Result.success((1..30).map { index -> flashcard(id = "card-$index") })

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.selectedCardCount shouldBe 20
        viewModel.state.value.isLoading shouldBe false
    }

    @Test
    fun `selection uses whole pool when smaller than session size`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)
        flashcardRepository.flashcardsToReturn =
            Result.success((1..5).map { index -> flashcard(id = "card-$index") })

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.selectedCardCount shouldBe 5
    }

    @Test
    fun `routed tag filter seeds the config and keeps only cards carrying an active tag`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(singleSubcategoryRoute.copy(filterTagIds = listOf("State")))
            flashcardRepository.flashcardsToReturn = Result.success(
                listOf(
                    flashcard(id = "card-1", tags = listOf("State")),
                    flashcard(id = "card-2", tags = listOf("Modifiers")),
                    flashcard(id = "card-3", tags = listOf("State", "Modifiers")),
                )
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.value.config.tagIds shouldBe setOf("State")
            viewModel.state.value.selectedCardCount shouldBe 2
        }

    @Test
    fun `multi subcategory route pools cards across subcategories`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(multiSubcategoryRoute)
        flashcardRepository.flashcardsBySubcategory["android-compose"] =
            Result.success(listOf(flashcard(id = "card-1")))
        flashcardRepository.flashcardsBySubcategory["android-coroutines"] =
            Result.success(listOf(flashcard(id = "card-2", subcategoryId = "android-coroutines")))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.selectedCardCount shouldBe 2
    }

    @Test
    fun `failed fetch surfaces error`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)
        flashcardRepository.flashcardsToReturn = Result.failure(IllegalStateException("boom"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.error shouldBe "Could not load flashcards"
        viewModel.state.value.canStart shouldBe false
    }

    /**
     * Also covers the tag-seeding side of the same load: a single-subcategory session with no routed
     * filter materializes `config.tagIds` to every available tag, while a multi-subcategory session has
     * no tag vocabulary to seed from at all (ADR-0030), so it stays empty.
     */
    @Test
    fun `available tags come from the pool for single subcategory sessions only`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)
        flashcardRepository.flashcardsToReturn = Result.success(
            listOf(
                flashcard(id = "card-1", tags = listOf("State")),
                flashcard(id = "card-2", tags = listOf("Modifiers", "State")),
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.availableTags shouldBe listOf("Modifiers", "State")
        viewModel.state.value.config.tagIds shouldBe setOf("Modifiers", "State")

        stubRoute(multiSubcategoryRoute)
        flashcardRepository.flashcardsBySubcategory["android-compose"] =
            Result.success(listOf(flashcard(id = "card-1", tags = listOf("State"))))
        flashcardRepository.flashcardsBySubcategory["android-coroutines"] =
            Result.success(listOf(flashcard(id = "card-2", subcategoryId = "android-coroutines")))

        val multiSubcategoryViewModel = createViewModel()
        advanceUntilIdle()

        multiSubcategoryViewModel.state.value.availableTags shouldBe emptyList()
        multiSubcategoryViewModel.state.value.config.tagIds shouldBe emptySet()
    }

    @Test
    fun `a draft change leaves the committed config untouched until confirm`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)
        flashcardRepository.flashcardsToReturn = Result.success(listOf(flashcard(id = "card-1")))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(SessionMode(draftState = viewModel.state.value.config.mode)))
        viewModel.onDialogEvent(
            DraftChange(SessionMode(draftState = StudyMode.Fast))
        )

        viewModel.state.value.activeDialog shouldBe SessionMode(draftState = StudyMode.Fast)
        viewModel.state.value.config.mode shouldBe StudyMode.Rated
    }

    @Test
    fun `dismissing discards the draft`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)
        flashcardRepository.flashcardsToReturn = Result.success(listOf(flashcard(id = "card-1")))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(SessionCardsSortingOrder(draftState = viewModel.state.value.config.sortOrder)))
        viewModel.onDialogEvent(
            DraftChange(SessionCardsSortingOrder(draftState = FlashcardSortOrder.HardestFirst))
        )
        viewModel.onDialogEvent(Dismiss)

        viewModel.state.value.activeDialog shouldBe null
        viewModel.state.value.config.sortOrder shouldBe FlashcardSortOrder.Default
    }

    @Test
    fun `confirming the mode dialog commits the draft`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)
        flashcardRepository.flashcardsToReturn = Result.success(listOf(flashcard(id = "card-1")))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(SessionMode(draftState = viewModel.state.value.config.mode)))
        viewModel.onDialogEvent(
            DraftChange(SessionMode(draftState = StudyMode.Fast))
        )
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()

        viewModel.state.value.config.mode shouldBe StudyMode.Fast
        viewModel.state.value.activeDialog shouldBe null
    }

    @Test
    fun `confirming the length dialog reselects at the new count`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)
        flashcardRepository.flashcardsToReturn =
            Result.success((1..30).map { index -> flashcard(id = "card-$index") })

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(SessionCardCount(draftState = viewModel.state.value.config.length)))
        viewModel.onDialogEvent(DraftChange(SessionCardCount(draftState = 10)))
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()

        viewModel.state.value.config.length shouldBe 10
        viewModel.state.value.selectedCardCount shouldBe 10
    }

    @Test
    fun `confirming the attempts dialog commits the draft`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(RatedSessionMaxCardAttempts(draftState = viewModel.state.value.config.ratedAttempts)))
        viewModel.onDialogEvent(DraftChange(RatedSessionMaxCardAttempts(draftState = strictAttempts)))
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()

        viewModel.state.value.config.ratedAttempts shouldBe strictAttempts
        viewModel.state.value.activeDialog shouldBe null
    }

    @Test
    fun `dismissing the attempts dialog discards the draft`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)

        val viewModel = createViewModel()
        advanceUntilIdle()
        val committedAttempts = viewModel.state.value.config.ratedAttempts
        viewModel.onDialogEvent(Open(RatedSessionMaxCardAttempts(draftState = committedAttempts)))
        viewModel.onDialogEvent(DraftChange(RatedSessionMaxCardAttempts(draftState = strictAttempts)))
        viewModel.onDialogEvent(Dismiss)
        advanceUntilIdle()

        viewModel.state.value.config.ratedAttempts shouldBe committedAttempts
        viewModel.state.value.activeDialog shouldBe null
    }

    @Test
    fun `confirming the read-aloud dialog commits the draft`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(FastSessionReadAloud(draftState = viewModel.state.value.config.readAloudEnabled)))
        viewModel.onDialogEvent(DraftChange(FastSessionReadAloud(draftState = true)))
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()

        viewModel.state.value.config.readAloudEnabled shouldBe true
        viewModel.state.value.activeDialog shouldBe null
    }

    @Test
    fun `confirming the partial-rating-card-requeueing dialog commits the draft`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(singleSubcategoryRoute)

            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onDialogEvent(
                Open(RatedSessionPartialRatingCardRequeueing(draftState = viewModel.state.value.config.partialRatingCardRequeueingEnabled))
            )
            viewModel.onDialogEvent(DraftChange(RatedSessionPartialRatingCardRequeueing(draftState = false)))
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()

            viewModel.state.value.config.partialRatingCardRequeueingEnabled shouldBe false
            viewModel.state.value.activeDialog shouldBe null
        }

    @Test
    fun `dismissing the partial-rating-card-requeueing dialog discards the draft`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(singleSubcategoryRoute)

            val viewModel = createViewModel()
            advanceUntilIdle()
            val committed = viewModel.state.value.config.partialRatingCardRequeueingEnabled
            viewModel.onDialogEvent(Open(RatedSessionPartialRatingCardRequeueing(draftState = committed)))
            viewModel.onDialogEvent(DraftChange(RatedSessionPartialRatingCardRequeueing(draftState = false)))
            viewModel.onDialogEvent(Dismiss)
            advanceUntilIdle()

            viewModel.state.value.config.partialRatingCardRequeueingEnabled shouldBe committed
            viewModel.state.value.activeDialog shouldBe null
        }

    @Test
    fun `confirming the filters dialog narrows the pool by difficulty and tags`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)
        flashcardRepository.flashcardsToReturn = Result.success(
            listOf(
                flashcard(id = "card-1", tags = listOf("State"), difficulty = 2),
                flashcard(id = "card-2", tags = listOf("State"), difficulty = 5),
                flashcard(id = "card-3", tags = listOf("Modifiers"), difficulty = 5),
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(
            Filters(
                draftState = FlashcardFilters(
                    selectedTags = viewModel.state.value.config.tagIds,
                    difficultyRange = viewModel.state.value.config.difficultyRange,
                ),
                availableTags = viewModel.state.value.availableTags,
            )
        ))
        val filtersDialog = viewModel.state.value.activeDialog as Filters
        viewModel.onDialogEvent(
            DraftChange(
                filtersDialog.copy(draftState = FlashcardFilters(selectedTags = setOf("State"), difficultyRange = 4..6))
            )
        )
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()

        viewModel.state.value.config.tagIds shouldBe setOf("State")
        viewModel.state.value.config.difficultyRange shouldBe 4..6
        viewModel.state.value.selectedCardCount shouldBe 1
    }

    @Test
    fun `confirming the sort dialog orders session cards easiest first`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)
        flashcardRepository.flashcardsToReturn = Result.success(
            listOf(
                flashcard(id = "card-1", difficulty = 8),
                flashcard(id = "card-2", difficulty = 2),
                flashcard(id = "card-3", difficulty = 5),
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(SessionCardsSortingOrder(draftState = viewModel.state.value.config.sortOrder)))
        viewModel.onDialogEvent(
            DraftChange(SessionCardsSortingOrder(draftState = FlashcardSortOrder.EasiestFirst))
        )
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()
        viewModel.onStartSession()

        viewModel.state.value.config.sortOrder shouldBe FlashcardSortOrder.EasiestFirst
        viewModel.events.test {
            val destination = awaitItem() as PreviewStudySessionDestination.RatedStudySession
            destination.route.cardIds shouldBe listOf("card-2", "card-3", "card-1")
        }
    }

    @Test
    fun `confirming the sort dialog orders session cards hardest first`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)
        flashcardRepository.flashcardsToReturn = Result.success(
            listOf(
                flashcard(id = "card-1", difficulty = 8),
                flashcard(id = "card-2", difficulty = 2),
                flashcard(id = "card-3", difficulty = 5),
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(SessionCardsSortingOrder(draftState = viewModel.state.value.config.sortOrder)))
        viewModel.onDialogEvent(
            DraftChange(SessionCardsSortingOrder(draftState = FlashcardSortOrder.HardestFirst))
        )
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()
        viewModel.onStartSession()

        viewModel.events.test {
            val destination = awaitItem() as PreviewStudySessionDestination.RatedStudySession
            destination.route.cardIds shouldBe listOf("card-1", "card-3", "card-2")
        }
    }

    @Test
    fun `seeded study session preferences reach the config before the first card selection`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(singleSubcategoryRoute)
            studySessionPreferencesRepository.preferences.value = StudySessionPreferences(
                defaultStudyMode = StudyMode.Fast,
                sessionLength = seededLength,
                sortOrder = FlashcardSortOrder.HardestFirst,
                partialRatingCardRequeueingEnabled = false,
            )
            flashcardRepository.flashcardsToReturn =
                Result.success((1..30).map { index -> flashcard(id = "card-$index") })

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.value.config.mode shouldBe StudyMode.Fast
            viewModel.state.value.config.length shouldBe seededLength
            viewModel.state.value.config.sortOrder shouldBe FlashcardSortOrder.HardestFirst
            viewModel.state.value.config.partialRatingCardRequeueingEnabled shouldBe false
            viewModel.state.value.selectedCardCount shouldBe seededLength
        }

    @Test
    fun `a saved voice-answering default survives a Fast default study mode`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(singleSubcategoryRoute)
            studySessionPreferencesRepository.preferences.value = StudySessionPreferences(
                defaultStudyMode = StudyMode.Fast,
                voiceAnsweringEnabled = true,
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.value.config.mode shouldBe StudyMode.Fast
            viewModel.state.value.config.voiceAnsweringEnabled shouldBe true
        }

    @Test
    fun `confirming with keepAsDefault true writes the preference`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)
        flashcardRepository.flashcardsToReturn = Result.success(listOf(flashcard(id = "card-1")))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(SessionCardCount(draftState = viewModel.state.value.config.length)))
        viewModel.onDialogEvent(DraftChange(SessionCardCount(draftState = 10, keepAsDefault = true)))
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()

        studySessionPreferencesRepository.preferences.value.sessionLength shouldBe 10
        viewModel.state.value.config.length shouldBe 10
    }

    @Test
    fun `confirming with keepAsDefault false applies the draft but writes nothing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(singleSubcategoryRoute)
            flashcardRepository.flashcardsToReturn = Result.success(listOf(flashcard(id = "card-1")))

            val viewModel = createViewModel()
            advanceUntilIdle()
            val committedLength = studySessionPreferencesRepository.preferences.value.sessionLength
            viewModel.onDialogEvent(Open(SessionCardCount(draftState = viewModel.state.value.config.length)))
            viewModel.onDialogEvent(DraftChange(SessionCardCount(draftState = 10, keepAsDefault = false)))
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()

            studySessionPreferencesRepository.preferences.value.sessionLength shouldBe committedLength
            viewModel.state.value.config.length shouldBe 10
        }

    @Test
    fun `confirming partial-rating-card-requeueing with keepAsDefault true writes the preference`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(singleSubcategoryRoute)
            flashcardRepository.flashcardsToReturn = Result.success(listOf(flashcard(id = "card-1")))

            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onDialogEvent(
                Open(RatedSessionPartialRatingCardRequeueing(draftState = viewModel.state.value.config.partialRatingCardRequeueingEnabled))
            )
            viewModel.onDialogEvent(DraftChange(RatedSessionPartialRatingCardRequeueing(draftState = false, keepAsDefault = true)))
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()

            studySessionPreferencesRepository.preferences.value.partialRatingCardRequeueingEnabled shouldBe false
            viewModel.state.value.config.partialRatingCardRequeueingEnabled shouldBe false
        }

    @Test
    fun `confirming partial-rating-card-requeueing with keepAsDefault false applies the draft but writes nothing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(singleSubcategoryRoute)
            flashcardRepository.flashcardsToReturn = Result.success(listOf(flashcard(id = "card-1")))

            val viewModel = createViewModel()
            advanceUntilIdle()
            val committed = studySessionPreferencesRepository.preferences.value.partialRatingCardRequeueingEnabled
            viewModel.onDialogEvent(
                Open(RatedSessionPartialRatingCardRequeueing(draftState = viewModel.state.value.config.partialRatingCardRequeueingEnabled))
            )
            viewModel.onDialogEvent(DraftChange(RatedSessionPartialRatingCardRequeueing(draftState = false, keepAsDefault = false)))
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()

            studySessionPreferencesRepository.preferences.value.partialRatingCardRequeueingEnabled shouldBe committed
            viewModel.state.value.config.partialRatingCardRequeueingEnabled shouldBe false
        }

    @Test
    fun `confirming a subcategory count range with keepAsDefault true writes the preference`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(quickSessionRoute)
            quickSessionRoute.subcategoryIds.forEach { id ->
                flashcardRepository.flashcardsBySubcategory[id] =
                    Result.success(listOf(flashcard(id = "$id-card", subcategoryId = id)))
            }

            val viewModel = createViewModel()
            advanceUntilIdle()
            val sampledIdsBeforeConfirm = viewModel.state.value.config.subcategoryIds
            val draftDialog = QuickSessionSubcategoryCountRange(draftState = viewModel.state.value.config.subcategoryCountRange)
            viewModel.onDialogEvent(Open(draftDialog))
            viewModel.onDialogEvent(
                DraftChange(QuickSessionSubcategoryCountRange(draftState = narrowerSubcategoryCountRange, keepAsDefault = true))
            )
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()

            studySessionPreferencesRepository.preferences.value.subcategoryCountRange shouldBe
                narrowerSubcategoryCountRange
            viewModel.state.value.config.subcategoryCountRange shouldBe narrowerSubcategoryCountRange
            // A count-range change edits a setting for the *next* sample — it never itself
            // re-samples (ADR-0040), so the held sample from the initial load is untouched here.
            viewModel.state.value.config.subcategoryIds shouldBe sampledIdsBeforeConfirm

            viewModel.onReshuffleSubcategories()
            advanceUntilIdle()

            (viewModel.state.value.config.subcategoryIds.size in narrowerSubcategoryCountRange) shouldBe true
        }

    @Test
    fun `confirming a subcategory count range with keepAsDefault false applies the draft but writes nothing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(quickSessionRoute)
            quickSessionRoute.subcategoryIds.forEach { id ->
                flashcardRepository.flashcardsBySubcategory[id] =
                    Result.success(listOf(flashcard(id = "$id-card", subcategoryId = id)))
            }

            val viewModel = createViewModel()
            advanceUntilIdle()
            val committedRange = studySessionPreferencesRepository.preferences.value.subcategoryCountRange
            val draftDialog = QuickSessionSubcategoryCountRange(draftState = viewModel.state.value.config.subcategoryCountRange)
            viewModel.onDialogEvent(Open(draftDialog))
            viewModel.onDialogEvent(
                DraftChange(QuickSessionSubcategoryCountRange(draftState = narrowerSubcategoryCountRange, keepAsDefault = false))
            )
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()

            studySessionPreferencesRepository.preferences.value.subcategoryCountRange shouldBe committedRange
            viewModel.state.value.config.subcategoryCountRange shouldBe narrowerSubcategoryCountRange
        }

    @Test
    fun `confirming filters never writes a default`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)
        flashcardRepository.flashcardsToReturn = Result.success(
            listOf(flashcard(id = "card-1", tags = listOf("State"))),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        val defaultsBeforeConfirm = studySessionPreferencesRepository.preferences.value
        viewModel.onDialogEvent(
            Open(
                Filters(
                    draftState = FlashcardFilters(
                        selectedTags = viewModel.state.value.config.tagIds,
                        difficultyRange = viewModel.state.value.config.difficultyRange,
                    ),
                    availableTags = viewModel.state.value.availableTags,
                ),
            ),
        )
        val filtersDialog = viewModel.state.value.activeDialog as Filters
        viewModel.onDialogEvent(
            DraftChange(
                filtersDialog.copy(
                    draftState = FlashcardFilters(
                        selectedTags = setOf("State"),
                        difficultyRange = viewModel.state.value.config.difficultyRange,
                    ),
                ),
            ),
        )
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()

        studySessionPreferencesRepository.preferences.value shouldBe defaultsBeforeConfirm
    }

    @Test
    fun `confirming the voice dialog with keepAsDefault true writes the preference`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(singleSubcategoryRoute)
            flashcardRepository.flashcardsToReturn = Result.success(listOf(flashcard(id = "card-1")))
            val voiceSettings = SavedVoiceSettings(speechRate = 1.5f, voiceId = "voice-1")
            every { voiceSettingsController.seedDraft(any()) } returns VoiceSettingsDraftState()

            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onDialogEvent(Open(SessionVoiceSettings()))
            val draft = (viewModel.state.value.activeDialog as SessionVoiceSettings).draftState
                .copy(draftSpeed = voiceSettings.speechRate, draftVoiceId = voiceSettings.voiceId)
            viewModel.onDialogEvent(DraftChange(SessionVoiceSettings(draftState = draft, keepAsDefault = true)))
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()

            studySessionPreferencesRepository.preferences.value.voiceSettings shouldBe voiceSettings
            viewModel.state.value.config.voiceSettings shouldBe voiceSettings
        }

    @Test
    fun `confirming the voice dialog with keepAsDefault false applies the draft but writes nothing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(singleSubcategoryRoute)
            flashcardRepository.flashcardsToReturn = Result.success(listOf(flashcard(id = "card-1")))
            val voiceSettings = SavedVoiceSettings(speechRate = 1.5f, voiceId = "voice-1")
            every { voiceSettingsController.seedDraft(any()) } returns VoiceSettingsDraftState()

            val viewModel = createViewModel()
            advanceUntilIdle()
            val committedVoiceSettings = studySessionPreferencesRepository.preferences.value.voiceSettings
            viewModel.onDialogEvent(Open(SessionVoiceSettings()))
            val draft = (viewModel.state.value.activeDialog as SessionVoiceSettings).draftState
                .copy(draftSpeed = voiceSettings.speechRate, draftVoiceId = voiceSettings.voiceId)
            viewModel.onDialogEvent(DraftChange(SessionVoiceSettings(draftState = draft, keepAsDefault = false)))
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()

            studySessionPreferencesRepository.preferences.value.voiceSettings shouldBe committedVoiceSettings
            viewModel.state.value.config.voiceSettings shouldBe voiceSettings
        }

    @Test
    fun `onStartSession emits RatedStudySession route with selected cards, voice answering and attempts`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(singleSubcategoryRoute)
            markVoiceAnsweringInfoSeen()
            flashcardRepository.flashcardsToReturn = Result.success(
                listOf(flashcard(id = "card-1"), flashcard(id = "card-2"))
            )

            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onDialogEvent(Open(RatedSessionVoiceAnswering(draftState = viewModel.state.value.config.voiceAnsweringEnabled)))
            viewModel.onDialogEvent(
                DraftChange(RatedSessionVoiceAnswering(draftState = true))
            )
            viewModel.onDialogEvent(Confirm)
            viewModel.onDialogEvent(Open(RatedSessionMaxCardAttempts(draftState = viewModel.state.value.config.ratedAttempts)))
            viewModel.onDialogEvent(DraftChange(RatedSessionMaxCardAttempts(draftState = 5)))
            viewModel.onDialogEvent(Confirm)
            viewModel.onDialogEvent(
                Open(RatedSessionPartialRatingCardRequeueing(draftState = viewModel.state.value.config.partialRatingCardRequeueingEnabled))
            )
            viewModel.onDialogEvent(DraftChange(RatedSessionPartialRatingCardRequeueing(draftState = false)))
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()
            viewModel.onStartSession()

            viewModel.events.test {
                val destination = awaitItem() as PreviewStudySessionDestination.RatedStudySession
                destination.route.categoryId shouldBe categoryId
                destination.route.sessionTitle shouldBe subcategoryName
                destination.route.subcategoryIds shouldBe listOf(subcategoryId)
                destination.route.cardIds shouldContainAll listOf("card-1", "card-2")
                destination.route.voiceAnsweringEnabled shouldBe true
                destination.route.ratedAttempts shouldBe 5
                destination.route.partialRatingCardRequeueingEnabled shouldBe false
            }
        }

    @Test
    fun `onStartSession with Fast mode emits FastStudySession route`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)
        flashcardRepository.flashcardsToReturn = Result.success(
            listOf(flashcard(id = "card-1"), flashcard(id = "card-2"))
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(SessionMode(draftState = StudyMode.Fast)))
        viewModel.onDialogEvent(Confirm)
        viewModel.onDialogEvent(Open(FastSessionReadAloud(draftState = viewModel.state.value.config.readAloudEnabled)))
        viewModel.onDialogEvent(DraftChange(FastSessionReadAloud(draftState = true)))
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()
        viewModel.onStartSession()

        viewModel.events.test {
            val destination = awaitItem() as PreviewStudySessionDestination.FastStudySession
            destination.route.categoryId shouldBe categoryId
            destination.route.sessionTitle shouldBe subcategoryName
            destination.route.subcategoryIds shouldBe listOf(subcategoryId)
            destination.route.cardIds shouldContainAll listOf("card-1", "card-2")
            destination.route.readAloudEnabled shouldBe true
        }
    }

    @Test
    fun `onStartSession carries the confirmed voice settings on the route`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)
        flashcardRepository.flashcardsToReturn = Result.success(listOf(flashcard(id = "card-1")))
        val voiceSettings = SavedVoiceSettings(speechRate = 1.5f, voiceId = "voice-1")
        every { voiceSettingsController.seedDraft(any()) } returns VoiceSettingsDraftState()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(SessionVoiceSettings()))
        val draft = (viewModel.state.value.activeDialog as SessionVoiceSettings).draftState
            .copy(draftSpeed = voiceSettings.speechRate, draftVoiceId = voiceSettings.voiceId)
        viewModel.onDialogEvent(DraftChange(SessionVoiceSettings(draftState = draft)))
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()
        viewModel.onStartSession()

        viewModel.events.test {
            val destination = awaitItem() as PreviewStudySessionDestination.RatedStudySession
            destination.route.voiceSettings shouldBe voiceSettings
        }
    }

    @Test
    fun `confirming the voice settings dialog stops preview playback`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)
        every { voiceSettingsController.seedDraft(any()) } returns VoiceSettingsDraftState()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(SessionVoiceSettings()))
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()

        verify { voiceSettingsController.stopPreview() }
    }

    @Test
    fun `confirming a non-voice dialog never touches preview playback`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onDialogEvent(Open(SessionMode(draftState = StudyMode.Fast)))
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()

        verify(exactly = 0) { voiceSettingsController.stopPreview() }
    }

    @Test
    fun `onStartSession with empty pool emits nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onStartSession()

        viewModel.events.test {
            expectNoEvents()
        }
    }

    @Test
    fun `onStartSession ignores re-entrant calls while a session is already pending`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)
        flashcardRepository.flashcardsToReturn = Result.success(listOf(flashcard(id = "card-1")))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onStartSession()
        viewModel.onStartSession()

        viewModel.events.test {
            awaitItem() as PreviewStudySessionDestination.RatedStudySession
            expectNoEvents()
        }
    }

    @Test
    fun `onRetry recovers from a previous failure and loads the pool`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)
        flashcardRepository.flashcardsToReturn = Result.failure(IllegalStateException("boom"))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.state.value.error shouldBe "Could not load flashcards"

        flashcardRepository.flashcardsToReturn = Result.success(listOf(flashcard(id = "card-1")))
        viewModel.onRetry()
        advanceUntilIdle()

        viewModel.state.value.error shouldBe null
        viewModel.state.value.selectedCardCount shouldBe 1
    }

    @Test
    fun `sessionTitle uses category name for multi subcategory sessions`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(multiSubcategoryRoute)
        flashcardRepository.flashcardsBySubcategory["android-compose"] =
            Result.success(listOf(flashcard(id = "card-1")))
        flashcardRepository.flashcardsBySubcategory["android-coroutines"] =
            Result.success(listOf(flashcard(id = "card-2", subcategoryId = "android-coroutines")))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onStartSession()

        viewModel.events.test {
            val destination = awaitItem() as PreviewStudySessionDestination.RatedStudySession
            destination.route.sessionTitle shouldBe categoryName
        }
    }

    @Test
    fun `estimatedMinutes rounds up to the nearest minute`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute)
        flashcardRepository.flashcardsToReturn =
            Result.success((1..5).map { index -> flashcard(id = "card-$index") })

        val viewModel = createViewModel()
        advanceUntilIdle()

        // 5 cards * 40s/card = 200s -> ceil(200/60) = 4 minutes
        viewModel.state.value.estimatedMinutes shouldBe 4
    }

    @Test
    fun `quick session on a single subcategory can still reshuffle subcategories`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(singleSubcategoryRoute.copy(isQuickSession = true))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.canReshuffleSubcategories.shouldBeTrue()
    }

    @Test
    fun `onReshuffleSubcategories redraws a different set of cards, keeping session size`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(multiSubcategoryRoute)
            flashcardRepository.flashcardsBySubcategory["android-compose"] =
                Result.success((1..30).map { index -> flashcard(id = "compose-$index") })
            flashcardRepository.flashcardsBySubcategory["android-coroutines"] =
                Result.success(
                    (1..30).map { index -> flashcard(id = "coroutines-$index", subcategoryId = "android-coroutines") },
                )

            val viewModel = createViewModel()
            advanceUntilIdle()
            val cardIdsBeforeReshuffle = viewModel.selectedCardIds

            viewModel.onReshuffleSubcategories()
            advanceUntilIdle()

            viewModel.state.value.selectedCardCount shouldBe 20
            viewModel.selectedCardIds shouldNotBe cardIdsBeforeReshuffle
        }

    @Test
    fun `a Custom session cannot reshuffle subcategories, single or multi`() = runTest(mainDispatcherRule.testDispatcher) {
        // Custom's subcategories are hand-picked by the user, not sampled — nothing to reshuffle,
        // unlike Quick (see `quick session on a single subcategory can still reshuffle subcategories`).
        stubRoute(singleSubcategoryRoute)
        createViewModel().state.value.canReshuffleSubcategories shouldBe false

        stubRoute(multiSubcategoryRoute)
        createViewModel().state.value.canReshuffleSubcategories shouldBe false
    }

    @Test
    fun `a quick session samples a bounded subset of the candidate subcategories`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(quickSessionRoute)
            quickSessionRoute.subcategoryIds.forEach { id ->
                flashcardRepository.flashcardsBySubcategory[id] =
                    Result.success(listOf(flashcard(id = "$id-card", subcategoryId = id)))
            }

            val viewModel = createViewModel()
            advanceUntilIdle()

            val sampledIds = viewModel.state.value.config.subcategoryIds
            (sampledIds.size in StudySessionConfig.DEFAULT_SUBCATEGORY_COUNT_RANGE) shouldBe true
            viewModel.state.value.subcategoryCount shouldBe sampledIds.size
            sampledIds.forEach { id -> (id in quickSessionRoute.subcategoryIds) shouldBe true }
        }

    @Test
    fun `a quick session's sample respects a seeded subcategoryCountRange preference`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(quickSessionRoute)
            studySessionPreferencesRepository.preferences.value = StudySessionPreferences(
                subcategoryCountRange = narrowerSubcategoryCountRange,
            )
            quickSessionRoute.subcategoryIds.forEach { id ->
                flashcardRepository.flashcardsBySubcategory[id] =
                    Result.success(listOf(flashcard(id = "$id-card", subcategoryId = id)))
            }

            val viewModel = createViewModel()
            advanceUntilIdle()

            val sampledIds = viewModel.state.value.config.subcategoryIds
            (sampledIds.size in narrowerSubcategoryCountRange) shouldBe true
        }

    @Test
    fun `a filter, length or sort change never resamples a quick session's subcategories`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(quickSessionRoute)
            quickSessionRoute.subcategoryIds.forEach { id ->
                flashcardRepository.flashcardsBySubcategory[id] =
                    Result.success((1..30).map { index -> flashcard(id = "$id-card-$index", subcategoryId = id) })
            }

            val viewModel = createViewModel()
            advanceUntilIdle()
            val sampledIds = viewModel.state.value.config.subcategoryIds

            viewModel.onDialogEvent(Open(SessionCardCount(draftState = viewModel.state.value.config.length)))
            viewModel.onDialogEvent(DraftChange(SessionCardCount(draftState = 10)))
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()
            viewModel.state.value.config.subcategoryIds shouldBe sampledIds

            viewModel.onDialogEvent(Open(SessionCardsSortingOrder(draftState = viewModel.state.value.config.sortOrder)))
            viewModel.onDialogEvent(DraftChange(SessionCardsSortingOrder(draftState = FlashcardSortOrder.HardestFirst)))
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()
            viewModel.state.value.config.subcategoryIds shouldBe sampledIds

            viewModel.onDialogEvent(
                Open(
                    Filters(
                        draftState = FlashcardFilters(
                            selectedTags = viewModel.state.value.config.tagIds,
                            difficultyRange = viewModel.state.value.config.difficultyRange,
                        ),
                        availableTags = viewModel.state.value.availableTags,
                    ),
                ),
            )
            val filtersDialog = viewModel.state.value.activeDialog as Filters
            viewModel.onDialogEvent(
                DraftChange(
                    filtersDialog.copy(draftState = FlashcardFilters(selectedTags = emptySet(), difficultyRange = 1..5)),
                ),
            )
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()
            viewModel.state.value.config.subcategoryIds shouldBe sampledIds
        }

    @Test
    fun `reshuffling subcategories on a quick session changes the subcategory sample, not just the draw`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(quickSessionRoute)
            quickSessionRoute.subcategoryIds.forEach { id ->
                flashcardRepository.flashcardsBySubcategory[id] =
                    Result.success(listOf(flashcard(id = "$id-card", subcategoryId = id)))
            }

            val viewModel = createViewModel()
            advanceUntilIdle()
            val sampleBeforeReshuffle = viewModel.state.value.config.subcategoryIds

            viewModel.onReshuffleSubcategories()
            advanceUntilIdle()

            viewModel.state.value.config.subcategoryIds shouldNotBe sampleBeforeReshuffle
        }

    @Test
    fun `a single subcategory quick session samples the same one subcategory`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(singleSubcategoryRoute.copy(isQuickSession = true))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.value.config.subcategoryIds shouldBe listOf(subcategoryId)
            viewModel.state.value.subcategoryCount shouldBe 1
        }

    @Test
    fun `multi subcategory sessions ignore any routed tag filter, filtering by difficulty only`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(multiSubcategoryRoute.copy(filterTagIds = listOf("State")))
            flashcardRepository.flashcardsBySubcategory["android-compose"] =
                Result.success(listOf(flashcard(id = "card-1", tags = listOf("State"))))
            flashcardRepository.flashcardsBySubcategory["android-coroutines"] = Result.success(
                listOf(flashcard(id = "card-2", subcategoryId = "android-coroutines", tags = listOf("Modifiers")))
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Both cards count: a tags = {"State"} config carried on the route would otherwise
            // exclude card-2, but a multi-Subcategory pool is never filtered by tags.
            viewModel.state.value.selectedCardCount shouldBe 2
        }

    @Test
    fun `onResetFilters restores the routed filters exactly and reselects`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(singleSubcategoryRoute.copy(filterTagIds = listOf("State"), difficultyMin = 3, difficultyMax = 7))
            flashcardRepository.flashcardsToReturn = Result.success(
                listOf(
                    flashcard(id = "card-1", tags = listOf("State"), difficulty = 5),
                    flashcard(id = "card-2", tags = listOf("Modifiers"), difficulty = 5),
                )
            )

            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onDialogEvent(
                Open(
                    Filters(
                        draftState = FlashcardFilters(selectedTags = setOf("Modifiers"), difficultyRange = 1..1),
                        availableTags = viewModel.state.value.availableTags,
                    ),
                ),
            )
            val filtersDialog = viewModel.state.value.activeDialog as Filters
            viewModel.onDialogEvent(
                DraftChange(
                    filtersDialog.copy(draftState = FlashcardFilters(selectedTags = setOf("Modifiers"), difficultyRange = 1..1)),
                ),
            )
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()
            viewModel.state.value.selectedCardCount shouldBe 0

            viewModel.onResetFilters()
            advanceUntilIdle()

            viewModel.state.value.config.tagIds shouldBe setOf("State")
            viewModel.state.value.config.difficultyRange shouldBe 3..7
            viewModel.state.value.selectedCardCount shouldBe 1
            viewModel.state.value.activeDialog shouldBe null
        }

    /**
     * Regression test for a bug Copilot review flagged on PR #64: with the card draw stateful
     * (ADR-0040, no session seed), confirming Sort used to reselect through
     * [SelectSessionFlashcardsUseCase], which could redraw a different subset of a pool larger
     * than the session length — see SYSTEMDESIGN.md:107,377. Sort must only reorder the cast
     * already drawn.
     */
    @Test
    fun `confirming the sort dialog never changes which cards are in the session`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubRoute(singleSubcategoryRoute)
            flashcardRepository.flashcardsToReturn = Result.success(
                (1..25).map { index -> flashcard(id = "card-$index", difficulty = index) }
            )

            val viewModel = createViewModel()
            advanceUntilIdle()
            val cardIdsBeforeSort = viewModel.selectedCardIds.toSet()

            viewModel.onDialogEvent(Open(SessionCardsSortingOrder(draftState = viewModel.state.value.config.sortOrder)))
            viewModel.onDialogEvent(DraftChange(SessionCardsSortingOrder(draftState = FlashcardSortOrder.EasiestFirst)))
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()

            viewModel.selectedCardIds.toSet() shouldBe cardIdsBeforeSort
        }

    private fun markVoiceAnsweringInfoSeen() {
        userPreferencesRepository.preferences.value =
            userPreferencesRepository.preferences.value.copy(hasSeenVoiceAnsweringInfo = true)
    }

    /** A loaded single-subcategory Rated session whose saved default has voice answering on. */
    private fun TestScope.createVoiceAnsweringViewModel(): PreviewStudySessionViewModel {
        stubRoute(singleSubcategoryRoute)
        flashcardRepository.flashcardsToReturn = Result.success(listOf(flashcard(id = "card-1")))
        studySessionPreferencesRepository.preferences.value = StudySessionPreferences(voiceAnsweringEnabled = true)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onResume()
        advanceUntilIdle()
        return viewModel
    }

    private fun setMicPermissionStatus(status: PermissionStatus) {
        permissionGateway.statuses.value = mapOf(AppPermission.RecordAudio to status)
    }

    @Test
    fun `canStart stays true for every microphone status, a permanent denial included`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceAnsweringViewModel()

            listOf(Denied, PermanentlyDenied, Granted).forEach { status ->
                setMicPermissionStatus(status)
                viewModel.onResume()
                advanceUntilIdle()
                viewModel.state.value.canStart shouldBe true
                viewModel.state.value.isMicPermissionRejected shouldBe (status == PermanentlyDenied)
            }
        }

    @Test
    fun `a permanent denial is not a rejection once the session no longer needs the microphone`() =
        runTest(mainDispatcherRule.testDispatcher) {
            setMicPermissionStatus(PermanentlyDenied)
            val viewModel = createVoiceAnsweringViewModel()
            viewModel.state.value.isMicPermissionRejected shouldBe true

            viewModel.onDialogEvent(Open(SessionMode(draftState = StudyMode.Fast)))
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()
            viewModel.state.value.isMicPermissionRejected shouldBe false

            viewModel.onDialogEvent(Open(SessionMode(draftState = StudyMode.Rated)))
            viewModel.onDialogEvent(Confirm)
            viewModel.onDialogEvent(Open(RatedSessionVoiceAnswering(draftState = true)))
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()
            viewModel.state.value.isMicPermissionRejected shouldBe true

            viewModel.onSwitchToManualAnswering()
            viewModel.state.value.isMicPermissionRejected shouldBe false
            viewModel.state.value.canStart shouldBe true
        }

    @Test
    fun `onResume re-reads the microphone status`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createVoiceAnsweringViewModel()
        viewModel.state.value.micPermissionStatus shouldBe Denied

        setMicPermissionStatus(Granted)
        viewModel.onResume()
        advanceUntilIdle()

        viewModel.state.value.micPermissionStatus shouldBe Granted
    }

    @Test
    fun `Start with the info unseen shows the info dialog and neither requests nor navigates`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceAnsweringViewModel()

            viewModel.onStartSession()
            advanceUntilIdle()

            viewModel.state.value.activeDialog shouldBe VoiceAnsweringInfo
            permissionGateway.launchedRequests shouldBe emptyList()
            viewModel.events.test { expectNoEvents() }
        }

    @Test
    fun `OK on the info dialog persists the seen flag, requests the microphone and navigates once granted`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceAnsweringViewModel()
            viewModel.onStartSession()
            advanceUntilIdle()

            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()

            userPreferencesRepository.preferences.value.hasSeenVoiceAnsweringInfo shouldBe true
            viewModel.state.value.activeDialog shouldBe null
            permissionGateway.launchedRequests shouldBe listOf(AppPermission.RecordAudio)
            viewModel.events.test {
                val destination = awaitItem() as PreviewStudySessionDestination.RatedStudySession
                destination.route.voiceAnsweringEnabled shouldBe true
            }
        }

    @Test
    fun `OK on the info dialog still requests and navigates when saving the seen flag fails`() =
        runTest(mainDispatcherRule.testDispatcher) {
            userPreferencesRepository.saveError = IllegalStateException("disk full")
            val viewModel = createVoiceAnsweringViewModel()
            viewModel.onStartSession()
            advanceUntilIdle()

            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()

            permissionGateway.launchedRequests shouldBe listOf(AppPermission.RecordAudio)
            viewModel.events.test {
                awaitItem() as PreviewStudySessionDestination.RatedStudySession
            }
        }

    @Test
    fun `dismissing the info dialog acts like OK - persists the seen flag, requests and navigates once granted`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createVoiceAnsweringViewModel()
            viewModel.onStartSession()
            advanceUntilIdle()

            viewModel.onDialogEvent(Dismiss)
            advanceUntilIdle()

            userPreferencesRepository.preferences.value.hasSeenVoiceAnsweringInfo shouldBe true
            viewModel.state.value.activeDialog shouldBe null
            permissionGateway.launchedRequests shouldBe listOf(AppPermission.RecordAudio)
            viewModel.events.test {
                awaitItem() as PreviewStudySessionDestination.RatedStudySession
            }
        }

    @Test
    fun `Start with the info already seen requests the microphone directly`() =
        runTest(mainDispatcherRule.testDispatcher) {
            markVoiceAnsweringInfoSeen()
            val viewModel = createVoiceAnsweringViewModel()

            viewModel.onStartSession()
            advanceUntilIdle()

            viewModel.state.value.activeDialog shouldBe null
            permissionGateway.launchedRequests shouldBe listOf(AppPermission.RecordAudio)
            viewModel.events.test {
                awaitItem() as PreviewStudySessionDestination.RatedStudySession
            }
        }

    @Test
    fun `a soft denial keeps the user here and lets Start ask again`() =
        runTest(mainDispatcherRule.testDispatcher) {
            markVoiceAnsweringInfoSeen()
            permissionGateway.nextRequestResult = Denied
            val viewModel = createVoiceAnsweringViewModel()

            repeat(2) { index ->
                viewModel.onStartSession()
                advanceUntilIdle()

                viewModel.state.value.micPermissionStatus shouldBe Denied
                viewModel.state.value.config.voiceAnsweringEnabled shouldBe true
                viewModel.state.value.canStart shouldBe true
                permissionGateway.launchedRequests.size shouldBe index + 1
            }
            viewModel.events.test { expectNoEvents() }
        }

    @Test
    fun `a first permanent denial shows the empty state, keeps Start enabled and shows no snackbar`() =
        runTest(mainDispatcherRule.testDispatcher) {
            markVoiceAnsweringInfoSeen()
            permissionGateway.nextRequestResult = PermanentlyDenied
            val viewModel = createVoiceAnsweringViewModel()

            viewModel.messages.test {
                viewModel.onStartSession()
                advanceUntilIdle()

                expectNoEvents()
            }
            viewModel.state.value.isMicPermissionRejected shouldBe true
            viewModel.state.value.canStart shouldBe true
            viewModel.events.test { expectNoEvents() }
        }

    @Test
    fun `Start while permanently denied asks again and shows a snackbar when still refused`() =
        runTest(mainDispatcherRule.testDispatcher) {
            markVoiceAnsweringInfoSeen()
            setMicPermissionStatus(PermanentlyDenied)
            permissionGateway.nextRequestResult = PermanentlyDenied
            val viewModel = createVoiceAnsweringViewModel()

            viewModel.messages.test {
                viewModel.onStartSession()
                advanceUntilIdle()

                awaitItem() shouldBe PreviewStudySessionMessage.MicPermissionStillDenied
            }
            permissionGateway.launchedRequests shouldBe listOf(AppPermission.RecordAudio)
            viewModel.state.value.isMicPermissionRejected shouldBe true
            viewModel.events.test { expectNoEvents() }
        }

    @Test
    fun `Start while permanently denied navigates when the new request is granted`() =
        runTest(mainDispatcherRule.testDispatcher) {
            markVoiceAnsweringInfoSeen()
            setMicPermissionStatus(PermanentlyDenied)
            permissionGateway.nextRequestResult = Granted
            val viewModel = createVoiceAnsweringViewModel()

            viewModel.messages.test {
                viewModel.onStartSession()
                advanceUntilIdle()

                expectNoEvents()
            }
            viewModel.state.value.isMicPermissionRejected shouldBe false
            viewModel.events.test {
                awaitItem() as PreviewStudySessionDestination.RatedStudySession
            }
        }

    @Test
    fun `Start while permanently denied clears the empty state when the system prompts again and the user soft-denies`() =
        runTest(mainDispatcherRule.testDispatcher) {
            markVoiceAnsweringInfoSeen()
            setMicPermissionStatus(PermanentlyDenied)
            permissionGateway.nextRequestResult = Denied
            val viewModel = createVoiceAnsweringViewModel()

            viewModel.messages.test {
                viewModel.onStartSession()
                advanceUntilIdle()

                expectNoEvents()
            }
            viewModel.state.value.isMicPermissionRejected shouldBe false
            viewModel.events.test { expectNoEvents() }
        }

    @Test
    fun `an already granted microphone navigates without prompting`() =
        runTest(mainDispatcherRule.testDispatcher) {
            markVoiceAnsweringInfoSeen()
            setMicPermissionStatus(Granted)
            val viewModel = createVoiceAnsweringViewModel()

            viewModel.onStartSession()
            advanceUntilIdle()

            permissionGateway.launchedRequests shouldBe emptyList()
            viewModel.events.test {
                awaitItem() as PreviewStudySessionDestination.RatedStudySession
            }
        }

    @Test
    fun `Start without voice answering navigates with no info dialog and no request`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fastViewModel = createVoiceAnsweringViewModel()
            fastViewModel.onDialogEvent(Open(SessionMode(draftState = StudyMode.Fast)))
            fastViewModel.onDialogEvent(Confirm)
            advanceUntilIdle()
            fastViewModel.onStartSession()
            advanceUntilIdle()

            fastViewModel.state.value.activeDialog shouldBe null
            fastViewModel.events.test {
                awaitItem() as PreviewStudySessionDestination.FastStudySession
            }

            val manualViewModel = createVoiceAnsweringViewModel()
            manualViewModel.onDialogEvent(Open(RatedSessionVoiceAnswering(draftState = false)))
            manualViewModel.onDialogEvent(Confirm)
            advanceUntilIdle()
            manualViewModel.onStartSession()
            advanceUntilIdle()

            manualViewModel.state.value.activeDialog shouldBe null
            manualViewModel.events.test {
                awaitItem() as PreviewStudySessionDestination.RatedStudySession
            }
            permissionGateway.launchedRequests shouldBe emptyList()
        }

    @Test
    fun `Switch to manual turns voice answering off for this session only`() =
        runTest(mainDispatcherRule.testDispatcher) {
            setMicPermissionStatus(PermanentlyDenied)
            val viewModel = createVoiceAnsweringViewModel()

            viewModel.onSwitchToManualAnswering()
            advanceUntilIdle()
            viewModel.onStartSession()
            advanceUntilIdle()

            viewModel.state.value.config.voiceAnsweringEnabled shouldBe false
            studySessionPreferencesRepository.preferences.value.voiceAnsweringEnabled shouldBe true
            viewModel.events.test {
                val destination = awaitItem() as PreviewStudySessionDestination.RatedStudySession
                destination.route.voiceAnsweringEnabled shouldBe false
            }
        }

    @Test
    fun `keeping voice answering as the default persists it even with the microphone permanently denied`() =
        runTest(mainDispatcherRule.testDispatcher) {
            setMicPermissionStatus(PermanentlyDenied)
            stubRoute(singleSubcategoryRoute)
            flashcardRepository.flashcardsToReturn = Result.success(listOf(flashcard(id = "card-1")))
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onResume()

            viewModel.onDialogEvent(Open(RatedSessionVoiceAnswering(draftState = true, keepAsDefault = true)))
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()

            studySessionPreferencesRepository.preferences.value.voiceAnsweringEnabled shouldBe true
            viewModel.state.value.isMicPermissionRejected shouldBe true
        }

    private companion object {
        /**
         * Verified (by the reshuffle tests above passing deterministically) to advance
         * [SelectSessionFlashcardsUseCase]'s draw to a different subset on a second call — a
         * fixed seed instead of [Random.Default] so those `shouldNotBe` assertions never flake.
         */
        const val CARD_DRAW_RANDOM_SEED = 42L

        /** Same rationale as [CARD_DRAW_RANDOM_SEED], for the quick-session subcategory sample. */
        const val SUBCATEGORY_SAMPLE_RANDOM_SEED = 7L
    }
}
