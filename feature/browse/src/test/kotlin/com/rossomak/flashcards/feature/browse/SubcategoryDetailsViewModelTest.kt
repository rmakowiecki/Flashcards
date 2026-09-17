package com.rossomak.flashcards.feature.browse

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FlashcardSortOrder
import com.rossomak.flashcards.core.domain.model.StudySessionPreferences
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.repository.FakeAppShortcutsRepository
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import com.rossomak.flashcards.core.domain.repository.FakeStudySessionPreferencesRepository
import com.rossomak.flashcards.core.domain.repository.FakeUserFavoritesRepository
import com.rossomak.flashcards.core.domain.usecase.FilterFlashcardsUseCase
import com.rossomak.flashcards.core.domain.usecase.GetFlashcardsUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveStudySessionPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveSubcategoryFavoriteStateUseCase
import com.rossomak.flashcards.core.domain.usecase.PinSubcategoryShortcutUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveStudySessionPreferenceUseCase
import com.rossomak.flashcards.core.domain.usecase.SetSubcategoryFavoriteUseCase
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardFilters
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.DraftChange
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.navigation.RouteDecoder
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsContentState
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsDestination
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsDialog
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsMessage
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsRoute
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsScreenState
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsViewModel
import com.rossomak.flashcards.testutil.MainDispatcherRule
import com.rossomak.flashcards.testutil.assertValue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubcategoryDetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val savedStateHandle: SavedStateHandle = mockk()
    private val flashcardRepository = FakeFlashcardRepository()
    private val preferencesRepository = FakeStudySessionPreferencesRepository()
    private val userFavoritesRepository = FakeUserFavoritesRepository()
    private val observeSubcategoryFavoriteState = ObserveSubcategoryFavoriteStateUseCase(userFavoritesRepository)
    private val setSubcategoryFavorite = SetSubcategoryFavoriteUseCase(userFavoritesRepository)
    private val appShortcutsRepository = FakeAppShortcutsRepository()
    private val pinSubcategoryShortcut = PinSubcategoryShortcutUseCase(flashcardRepository, appShortcutsRepository)

    private val route = SubcategoryDetailsRoute(
        categoryId = "cat-1",
        categoryName = "Android",
        subcategoryId = "sub-1",
        subcategoryName = "Compose",
    )

    @Before
    fun setUp() {
        mockkObject(RouteDecoder)
        every { RouteDecoder.decode(any<() -> SubcategoryDetailsRoute>()) } returns route
        flashcardRepository.flashcardsToReturn = Result.success(pool)
    }

    @After
    fun tearDown() {
        unmockkObject(RouteDecoder)
    }

    /**
     * The ViewModel seeds sort from preferences and loads in `init`, and MainDispatcherRule uses a
     * StandardTestDispatcher, so that work is queued rather than run — every test wants it settled.
     */
    private fun TestScope.startedViewModel(): SubcategoryDetailsViewModel =
        createViewModel().also { advanceUntilIdle() }

    private fun createViewModel(): SubcategoryDetailsViewModel = SubcategoryDetailsViewModel(
        savedStateHandle = savedStateHandle,
        getFlashcards = GetFlashcardsUseCase(flashcardRepository),
        filterFlashcards = FilterFlashcardsUseCase(),
        observeStudySessionPreferences = ObserveStudySessionPreferencesUseCase(preferencesRepository),
        saveStudySessionPreference = SaveStudySessionPreferenceUseCase(preferencesRepository),
        observeSubcategoryFavoriteState = observeSubcategoryFavoriteState,
        setSubcategoryFavorite = setSubcategoryFavorite,
        pinSubcategoryShortcut = pinSubcategoryShortcut,
    )

    private fun flashcard(
        id: String,
        difficulty: Int,
        tags: List<String>,
    ): Flashcard = Flashcard(
        id = id,
        subcategoryId = route.subcategoryId,
        tags = tags,
        question = "q-$id",
        answer = "a-$id",
        difficulty = difficulty,
        questionCode = null,
        answerCode = null,
        questionSpoken = null,
        answerSpoken = null,
        extendedContext = null,
    )

    private val pool = listOf(
        flashcard(id = "1", difficulty = 2, tags = listOf("State")),
        flashcard(id = "2", difficulty = 5, tags = listOf("Modifiers", "State")),
        flashcard(id = "3", difficulty = 9, tags = listOf("Theming")),
    )

    private fun cards(state: SubcategoryDetailsScreenState): List<String> =
        (state.content as SubcategoryDetailsContentState.FlashcardsList).flashcards.map { it.id }

    /** Open → edit → confirm → settle, the sequence every filter test repeats. */
    private fun TestScope.applyFilters(
        viewModel: SubcategoryDetailsViewModel,
        tags: Set<String> = emptySet(),
        difficultyRange: IntRange = 1..10,
    ) {
        viewModel.onDialogEvent(
            Open(
                SubcategoryDetailsDialog.Filters(
                    FlashcardFilters(selectedTags = emptySet(), difficultyRange = 1..10),
                    emptyList(),
                )
            )
        )
        viewModel.onDialogEvent(
            DraftChange(
                SubcategoryDetailsDialog.Filters(
                    FlashcardFilters(selectedTags = tags, difficultyRange = difficultyRange),
                    emptyList(),
                )
            )
        )
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()
    }

    // --- loading ---

    @Test
    fun `a successful load shows the pool`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = startedViewModel()

        viewModel.state.assertValue {
            cards(this) shouldBe listOf("1", "2", "3")
            totalCount shouldBe 3
            availableTags shouldBe listOf("Modifiers", "State", "Theming")
        }
    }

    @Test
    fun `a failed load shows the error state`() = runTest(mainDispatcherRule.testDispatcher) {
        flashcardRepository.flashcardsToReturn = Result.failure(IllegalStateException("offline"))

        val content = startedViewModel().state.value.content

        (content is SubcategoryDetailsContentState.Error) shouldBe true
    }

    // --- sorting ---

    @Test
    fun `the list is already ordered by the saved preference on first load`() =
        runTest(mainDispatcherRule.testDispatcher) {
            preferencesRepository.preferences.value =
                StudySessionPreferences(sortOrder = FlashcardSortOrder.HardestFirst)

            val viewModel = startedViewModel()

            viewModel.state.assertValue {
                sortOrder shouldBe FlashcardSortOrder.HardestFirst
                cards(this) shouldBe listOf("3", "2", "1")
            }
        }

    @Test
    fun `a preference change after load does not re-sort the list`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = startedViewModel()

            preferencesRepository.preferences.value =
                StudySessionPreferences(sortOrder = FlashcardSortOrder.HardestFirst)

            viewModel.state.assertValue {
                sortOrder shouldBe FlashcardSortOrder.Default
                cards(this) shouldBe listOf("1", "2", "3")
            }
        }

    @Test
    fun `confirming the sort dialog reorders the list`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = startedViewModel()

        viewModel.onDialogEvent(Open(SubcategoryDetailsDialog.CardsSortingOrder(FlashcardSortOrder.Default)))
        viewModel.onDialogEvent(DraftChange(SubcategoryDetailsDialog.CardsSortingOrder(FlashcardSortOrder.EasiestFirst)))
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()

        viewModel.state.assertValue {
            cards(this) shouldBe listOf("1", "2", "3")
            sortOrder shouldBe FlashcardSortOrder.EasiestFirst
            activeDialog shouldBe null
        }
    }

    @Test
    fun `dismissing the sort dialog discards the draft`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = startedViewModel()

        viewModel.onDialogEvent(Open(SubcategoryDetailsDialog.CardsSortingOrder(FlashcardSortOrder.Default)))
        viewModel.onDialogEvent(DraftChange(SubcategoryDetailsDialog.CardsSortingOrder(FlashcardSortOrder.HardestFirst)))
        viewModel.onDialogEvent(Dismiss)

        viewModel.state.assertValue {
            sortOrder shouldBe FlashcardSortOrder.Default
            activeDialog shouldBe null
        }
    }

    @Test
    fun `confirming sort with keep as default writes the preference`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = startedViewModel()

            viewModel.onDialogEvent(
                Open(SubcategoryDetailsDialog.CardsSortingOrder(FlashcardSortOrder.HardestFirst, keepAsDefault = true))
            )
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()

            preferencesRepository.preferences.value.sortOrder shouldBe FlashcardSortOrder.HardestFirst
        }

    @Test
    fun `confirming sort without keep as default leaves the preference alone`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = startedViewModel()

            viewModel.onDialogEvent(
                Open(SubcategoryDetailsDialog.CardsSortingOrder(FlashcardSortOrder.HardestFirst, keepAsDefault = false))
            )
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()

            viewModel.state.value.sortOrder shouldBe FlashcardSortOrder.HardestFirst
            preferencesRepository.preferences.value.sortOrder shouldBe FlashcardSortOrder.Default
        }

    // --- filtering ---

    @Test
    fun `a successful load selects every tag by default`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = startedViewModel()

        viewModel.state.assertValue {
            filters.selectedTags shouldBe setOf("Modifiers", "State", "Theming")
            hasActiveFilters shouldBe false
        }
    }

    @Test
    fun `confirming the filters dialog narrows the list and updates the counts`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = startedViewModel()

            applyFilters(viewModel, tags = setOf("State"), difficultyRange = 1..10)

            viewModel.state.assertValue {
                cards(this) shouldBe listOf("1", "2")
                totalCount shouldBe 3
                hasActiveFilters shouldBe true
            }
        }

    @Test
    fun `tag chips keep offering a tag that the active filter excludes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = startedViewModel()

            applyFilters(viewModel, tags = setOf("Theming"), difficultyRange = 1..10)

            viewModel.state.value.availableTags shouldBe listOf("Modifiers", "State", "Theming")
        }

    @Test
    fun `filtering everything out shows the no-matches state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = startedViewModel()

            applyFilters(viewModel, tags = setOf("Theming"), difficultyRange = 1..3)

            viewModel.state.value.content shouldBe SubcategoryDetailsContentState.NoMatches
        }

    @Test
    fun `resetting filters restores the list and leaves the sort order untouched`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = startedViewModel()
            viewModel.onDialogEvent(
                Open(SubcategoryDetailsDialog.CardsSortingOrder(FlashcardSortOrder.HardestFirst))
            )
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()
            applyFilters(viewModel, tags = setOf("Theming"), difficultyRange = 1..3)

            viewModel.onResetFilters()

            advanceUntilIdle()

            viewModel.state.assertValue {
                cards(this) shouldBe listOf("3", "2", "1")
                sortOrder shouldBe FlashcardSortOrder.HardestFirst
                hasActiveFilters shouldBe false
            }
        }

    // --- fake favourite ---

    @Test
    fun `toggling the favourite flips the flag and emits a message without persisting anything`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = startedViewModel()

            viewModel.messages.test {
                viewModel.onFavoriteToggle()
                advanceUntilIdle()

                awaitItem() shouldBe SubcategoryDetailsMessage.AddedToFavorites
                viewModel.state.value.isFavorite shouldBe true
                preferencesRepository.preferences.value shouldBe StudySessionPreferences()
            }
        }

    @Test
    fun `undoing the favourite flips it back`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = startedViewModel()
        viewModel.onFavoriteToggle()

        viewModel.onFavoriteUndo(restoreTo = false)

        viewModel.state.value.isFavorite shouldBe false
    }

    @Test
    fun `toggling an already favourited subcategory removes it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = startedViewModel()
            viewModel.onFavoriteToggle()
            advanceUntilIdle()

            viewModel.messages.test {
                viewModel.onFavoriteToggle()
                advanceUntilIdle()

                awaitItem() shouldBe SubcategoryDetailsMessage.RemovedFromFavorites
                viewModel.state.value.isFavorite shouldBe false
            }
        }

    // --- shortcut pinning ---

    @Test
    fun `add-shortcut click pins the routed subcategory`() = runTest(mainDispatcherRule.testDispatcher) {
        flashcardRepository.subcategoriesByIdsToReturn = Result.success(listOf(subcategoryModel()))
        flashcardRepository.categoriesByIdsToReturn = Result.success(listOf(categoryModel()))
        val viewModel = startedViewModel()

        viewModel.onAddShortcutClick()
        advanceUntilIdle()

        appShortcutsRepository.pinnedTargets.single().id shouldBe "subcategory:${route.subcategoryId}"
    }

    @Test
    fun `add-shortcut click on an unresolvable subcategory emits the failed message`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.subcategoriesByIdsToReturn = Result.success(emptyList())
            val viewModel = startedViewModel()

            viewModel.messages.test {
                viewModel.onAddShortcutClick()
                advanceUntilIdle()

                awaitItem() shouldBe SubcategoryDetailsMessage.ShortcutPinFailed
            }
        }

    @Test
    fun `add-shortcut click on an unsupported launcher emits the unsupported message`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.subcategoriesByIdsToReturn = Result.success(listOf(subcategoryModel()))
            flashcardRepository.categoriesByIdsToReturn = Result.success(listOf(categoryModel()))
            appShortcutsRepository.pinShortcutResult = false
            val viewModel = startedViewModel()

            viewModel.messages.test {
                viewModel.onAddShortcutClick()
                advanceUntilIdle()

                awaitItem() shouldBe SubcategoryDetailsMessage.ShortcutPinUnsupported
            }
        }

    private fun subcategoryModel() = Subcategory(
        id = route.subcategoryId,
        name = route.subcategoryName,
        categoryId = route.categoryId,
        categoryName = route.categoryName,
        order = 0,
        cardCount = pool.size,
    )

    private fun categoryModel() = Category(
        id = route.categoryId,
        name = route.categoryName,
        order = 0,
        subcategoryCount = 0,
        iconSvg = null,
        color = null,
        featuredSubcategoryNames = emptyList(),
    )

    // --- starting a session ---

    @Test
    fun `onStartSession carries the active filters and sort order into the destination`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = startedViewModel()
            applyFilters(viewModel, tags = setOf("State"), difficultyRange = 2..7)
            viewModel.onDialogEvent(Open(SubcategoryDetailsDialog.CardsSortingOrder(FlashcardSortOrder.EasiestFirst)))
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()

            viewModel.onStartSession()

            viewModel.events.test {
                awaitItem() shouldBe SubcategoryDetailsDestination.PreviewStudySession(
                    categoryId = route.categoryId,
                    categoryName = route.categoryName,
                    subcategoryId = route.subcategoryId,
                    subcategoryName = route.subcategoryName,
                    filterTagIds = listOf("State"),
                    difficultyRange = 2..7,
                    sortOrder = FlashcardSortOrder.EasiestFirst,
                )
            }
        }

    @Test
    fun `onStartSession with no filters touched carries every tag and the seeded order`() =
        runTest(mainDispatcherRule.testDispatcher) {
            preferencesRepository.preferences.value =
                StudySessionPreferences(sortOrder = FlashcardSortOrder.HardestFirst)
            val viewModel = startedViewModel()

            viewModel.onStartSession()

            viewModel.events.test {
                awaitItem() shouldBe SubcategoryDetailsDestination.PreviewStudySession(
                    categoryId = route.categoryId,
                    categoryName = route.categoryName,
                    subcategoryId = route.subcategoryId,
                    subcategoryName = route.subcategoryName,
                    filterTagIds = listOf("Modifiers", "State", "Theming"),
                    difficultyRange = SubcategoryDetailsScreenState.DIFFICULTY_BOUNDS,
                    sortOrder = FlashcardSortOrder.HardestFirst,
                )
            }
        }

    @Test
    fun `the session card count follows the filtered set`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = startedViewModel()

        viewModel.state.value.sessionCardCount shouldBe 3

        applyFilters(viewModel, tags = setOf("State"), difficultyRange = 1..10)

        viewModel.state.value.sessionCardCount shouldBe 2
    }

    @Test
    fun `the session card count is zero when nothing matches`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = startedViewModel()

        applyFilters(viewModel, tags = setOf("Theming"), difficultyRange = 1..3)

        viewModel.state.assertValue {
            content shouldBe SubcategoryDetailsContentState.NoMatches
            sessionCardCount shouldBe 0
        }
    }

    // --- regressions ---

    /**
     * A dialog confirm used to re-render an empty pool as NoMatches, so a load failure silently
     * turned into "No cards match your filters" with a Clear-filters button.
     */
    @Test
    fun `confirming a dialog after a failed load leaves the error state intact`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.flashcardsToReturn = Result.failure(IllegalStateException("offline"))
            val viewModel = startedViewModel()

            applyFilters(viewModel, tags = setOf("State"))

            (viewModel.state.value.content is SubcategoryDetailsContentState.Error) shouldBe true
        }

    @Test
    fun `resetting filters after a failed load leaves the error state intact`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.flashcardsToReturn = Result.failure(IllegalStateException("offline"))
            val viewModel = startedViewModel()

            viewModel.onResetFilters()
            advanceUntilIdle()

            (viewModel.state.value.content is SubcategoryDetailsContentState.Error) shouldBe true
        }

    /**
     * A snackbar outlives the tap that raised it, so Undo restores the value the toggle moved away
     * from rather than flipping whatever is current.
     */
    @Test
    fun `a stale undo does not invert a later toggle`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = startedViewModel()

        viewModel.onFavoriteToggle()
        viewModel.onFavoriteToggle()

        viewModel.onFavoriteUndo(restoreTo = false)

        viewModel.state.value.isFavorite shouldBe false
    }
}
