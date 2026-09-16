package com.rossomak.flashcards.feature.browse

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressSummary
import com.rossomak.flashcards.core.domain.repository.FakeCardProgressRepository
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import com.rossomak.flashcards.core.domain.repository.FakeUserFavoritesRepository
import com.rossomak.flashcards.core.domain.usecase.GetProgressSummaryUseCase
import com.rossomak.flashcards.core.domain.usecase.GetSubcategoriesUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveCategoryFavoriteStateUseCase
import com.rossomak.flashcards.core.domain.usecase.SetCategoryFavoriteUseCase
import com.rossomak.flashcards.core.ui.navigation.RouteDecoder
import com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsContentState
import com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsDestination
import com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsMessage
import com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsRoute
import com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsViewModel
import com.rossomak.flashcards.feature.browse.details.category.SubcategoryProgress
import com.rossomak.flashcards.testutil.MainDispatcherRule
import com.rossomak.flashcards.testutil.assertValue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryDetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val savedStateHandle: SavedStateHandle = mockk()
    private val flashcardRepository = FakeFlashcardRepository()
    private val getSubcategories = GetSubcategoriesUseCase(flashcardRepository)
    private val cardProgressRepository = FakeCardProgressRepository()
    private val getProgressSummary = GetProgressSummaryUseCase(cardProgressRepository)
    private val userFavoritesRepository = FakeUserFavoritesRepository()
    private val observeCategoryFavoriteState = ObserveCategoryFavoriteStateUseCase(userFavoritesRepository)
    private val setCategoryFavorite = SetCategoryFavoriteUseCase(userFavoritesRepository)

    private val route = CategoryDetailsRoute(categoryId = "android", categoryName = "Android")

    @Before
    fun setUp() {
        mockkObject(RouteDecoder)
        every { RouteDecoder.decode(any<() -> CategoryDetailsRoute>()) } returns route
    }

    @After
    fun tearDown() {
        unmockkObject(RouteDecoder)
    }

    private fun createViewModel(): CategoryDetailsViewModel =
        CategoryDetailsViewModel(
            savedStateHandle,
            getSubcategories,
            getProgressSummary,
            observeCategoryFavoriteState,
            setCategoryFavorite,
        )

    private fun subcategory(id: String): Subcategory = Subcategory(
        id = id,
        name = "name-$id",
        categoryId = route.categoryId,
        categoryName = route.categoryName,
        order = 0,
        cardCount = 3,
    )

    @Test
    fun `init seeds state with category id and name from the route`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.state.value.categoryId shouldBe route.categoryId
        viewModel.state.value.categoryName shouldBe route.categoryName
    }

    @Test
    fun `init loads subcategories for the routed category`() = runTest(mainDispatcherRule.testDispatcher) {
        val subcategories = listOf(subcategory("sub-1"), subcategory("sub-2"))
        flashcardRepository.subcategoriesToReturn = Result.success(subcategories)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.assertValue {
            content shouldBe CategoryDetailsContentState.Subcategories(subcategories)
        }
    }

    @Test
    fun `failed subcategory load surfaces error`() = runTest(mainDispatcherRule.testDispatcher) {
        flashcardRepository.subcategoriesToReturn = Result.failure(IllegalStateException("boom"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.assertValue {
            content shouldBe CategoryDetailsContentState.Error(R.string.category_details_load_error)
        }
    }

    // --- fake favourite ---

    @Test
    fun `toggling the favourite flips the flag and emits a message without persisting anything`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createViewModel()

            viewModel.messages.test {
                viewModel.onFavoriteToggle()
                advanceUntilIdle()

                awaitItem() shouldBe CategoryDetailsMessage.AddedToFavorites
                viewModel.state.value.isFavorite shouldBe true
            }
        }

    @Test
    fun `undoing the favourite flips it back`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onFavoriteToggle()

        viewModel.onFavoriteUndo(restoreTo = false)

        viewModel.state.value.isFavorite shouldBe false
    }

    @Test
    fun `toggling an already favourited category removes it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createViewModel()
            viewModel.onFavoriteToggle()
            advanceUntilIdle()

            viewModel.messages.test {
                viewModel.onFavoriteToggle()
                advanceUntilIdle()

                awaitItem() shouldBe CategoryDetailsMessage.RemovedFromFavorites
                viewModel.state.value.isFavorite shouldBe false
            }
        }

    /**
     * A snackbar outlives the tap that raised it, so Undo restores the value the toggle moved away
     * from rather than flipping whatever is current.
     */
    @Test
    fun `a stale undo does not invert a later toggle`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onFavoriteToggle()
        viewModel.onFavoriteToggle()

        viewModel.onFavoriteUndo(restoreTo = false)

        viewModel.state.value.isFavorite shouldBe false
    }

    // --- Selection Mode ---

    @Test
    fun `long-pressing a subcategory enters Selection Mode with exactly that subcategory selected`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val subcategories = listOf(subcategory("sub-1"), subcategory("sub-2"))
            flashcardRepository.subcategoriesToReturn = Result.success(subcategories)
            val viewModel = createViewModel()
            advanceUntilIdle()

            val pressedId = subcategories[1].id

            viewModel.onSubcategoryLongPress(pressedId)

            viewModel.state.assertValue {
                isSelectionMode shouldBe true
                selectedSubcategoryIds shouldBe setOf(pressedId)
            }
        }

    @Test
    fun `toggling the mode control from default mode enters Selection Mode with an empty selection`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSelectionModeToggle()

            viewModel.state.assertValue {
                isSelectionMode shouldBe true
                selectedSubcategoryIds shouldBe emptySet()
            }
        }

    @Test
    fun `leaving Selection Mode returns the field to null, discarding the selection`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val subcategories = listOf(subcategory("sub-1"), subcategory("sub-2"))
            flashcardRepository.subcategoriesToReturn = Result.success(subcategories)
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onSubcategoryLongPress("sub-1")

            viewModel.onSelectionModeToggle()

            viewModel.state.assertValue {
                isSelectionMode shouldBe false
                selectedSubcategoryIds shouldBe null
            }
        }

    @Test
    fun `selecting and deselecting individual subcategories adds to and removes from the set`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val subcategories = listOf(subcategory("sub-1"), subcategory("sub-2"), subcategory("sub-3"))
            flashcardRepository.subcategoriesToReturn = Result.success(subcategories)
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onSelectionModeToggle()
            val (firstId, secondId) = subcategories[0].id to subcategories[1].id

            viewModel.onSubcategorySelectionChange(firstId, true)
            viewModel.onSubcategorySelectionChange(secondId, true)
            viewModel.state.value.selectedSubcategoryIds shouldBe setOf(firstId, secondId)

            viewModel.onSubcategorySelectionChange(firstId, false)
            viewModel.state.value.selectedSubcategoryIds shouldBe setOf(secondId)
        }

    @Test
    fun `select-all from a partial selection selects every subcategory`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val subcategories = listOf(subcategory("sub-1"), subcategory("sub-2"), subcategory("sub-3"))
            flashcardRepository.subcategoriesToReturn = Result.success(subcategories)
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onSubcategoryLongPress(subcategories[0].id)

            viewModel.onSelectAllToggle()

            viewModel.state.value.selectedSubcategoryIds shouldBe subcategories.map { it.id }.toSet()
        }

    @Test
    fun `select-all from a full selection clears to empty`() = runTest(mainDispatcherRule.testDispatcher) {
        val subcategories = listOf(subcategory("sub-1"), subcategory("sub-2"))
        flashcardRepository.subcategoriesToReturn = Result.success(subcategories)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onSelectionModeToggle()
        viewModel.onSelectAllToggle()

        viewModel.onSelectAllToggle()

        viewModel.state.value.selectedSubcategoryIds shouldBe emptySet()
    }

    @Test
    fun `an empty subcategory list from a successful load surfaces error, a Category always has one or more`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.subcategoriesToReturn = Result.success(emptyList())
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.value.content shouldBe CategoryDetailsContentState.Error(R.string.category_details_load_error)
        }

    @Test
    fun `select-all with nothing selected selects every subcategory`() = runTest(mainDispatcherRule.testDispatcher) {
        val subcategories = listOf(subcategory("sub-1"), subcategory("sub-2"), subcategory("sub-3"))
        flashcardRepository.subcategoriesToReturn = Result.success(subcategories)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onSelectionModeToggle()

        viewModel.onSelectAllToggle()

        viewModel.state.value.selectedSubcategoryIds shouldBe subcategories.map { it.id }.toSet()
    }

    /**
     * Clearing a full selection is a bulk deselect, not an exit: it must leave the user in
     * Selection Mode with an empty (not null) selection and a disabled session button.
     */
    @Test
    fun `clearing a full selection stays in Selection Mode with the session button disabled`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val subcategories = listOf(subcategory("sub-1"), subcategory("sub-2"))
            flashcardRepository.subcategoriesToReturn = Result.success(subcategories)
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onSelectionModeToggle()
            viewModel.onSelectAllToggle()

            viewModel.onSelectAllToggle()

            viewModel.state.assertValue {
                isSelectionMode shouldBe true
                selectedSubcategoryIds shouldBe emptySet()
                selectedCount shouldBe 0
            }
        }

    @Test
    fun `selecting everything via select-all and starting a Custom session covers every subcategory, unsampled`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val subcategories = listOf(subcategory("sub-1"), subcategory("sub-2"), subcategory("sub-3"))
            flashcardRepository.subcategoriesToReturn = Result.success(subcategories)
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onSelectionModeToggle()
            viewModel.onSelectAllToggle()

            viewModel.events.test {
                viewModel.onCustomSessionStart()

                val destination = awaitItem() as CategoryDetailsDestination.PreviewStudySession
                destination.subcategoryIds shouldBe subcategories.map { it.id }
                destination.subcategoryNames shouldBe subcategories.map { it.name }
                destination.isQuickSession shouldBe false
            }
        }

    @Test
    fun `the Quick CTA emits every Subcategory as a sampled session`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val subcategories = listOf(subcategory("sub-1"), subcategory("sub-2"))
            flashcardRepository.subcategoriesToReturn = Result.success(subcategories)
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.onQuickSessionStart()

                val destination = awaitItem() as CategoryDetailsDestination.PreviewStudySession
                destination.subcategoryIds shouldBe subcategories.map { it.id }
                destination.subcategoryNames shouldBe subcategories.map { it.name }
                destination.isQuickSession shouldBe true
            }
        }

    @Test
    fun `the Custom CTA emits only the selected subcategories, unsampled`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val subcategories = listOf(subcategory("sub-1"), subcategory("sub-2"), subcategory("sub-3"))
            flashcardRepository.subcategoriesToReturn = Result.success(subcategories)
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onSelectionModeToggle()
            viewModel.onSubcategorySelectionChange("sub-1", true)
            viewModel.onSubcategorySelectionChange("sub-3", true)
            val selected = listOf(subcategories[0], subcategories[2])

            viewModel.events.test {
                viewModel.onCustomSessionStart()

                val destination = awaitItem() as CategoryDetailsDestination.PreviewStudySession
                destination.subcategoryIds shouldBe selected.map { it.id }
                destination.subcategoryNames shouldBe selected.map { it.name }
                destination.isQuickSession shouldBe false
            }
        }

    /** Ids come out in list order, not the order the user happened to tap them in. */
    @Test
    fun `the Custom CTA's ids come out in list order when selected in a different order`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val subcategories = listOf(subcategory("sub-1"), subcategory("sub-2"), subcategory("sub-3"))
            flashcardRepository.subcategoriesToReturn = Result.success(subcategories)
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onSelectionModeToggle()
            viewModel.onSubcategorySelectionChange("sub-3", true)
            viewModel.onSubcategorySelectionChange("sub-1", true)

            viewModel.events.test {
                viewModel.onCustomSessionStart()

                val destination = awaitItem() as CategoryDetailsDestination.PreviewStudySession
                destination.subcategoryIds shouldBe listOf(subcategories[0], subcategories[2]).map { it.id }
            }
        }

    @Test
    fun `derived counts reflect the selection`() = runTest(mainDispatcherRule.testDispatcher) {
        val subcategories = listOf(subcategory("sub-1"), subcategory("sub-2"), subcategory("sub-3"))
        flashcardRepository.subcategoriesToReturn = Result.success(subcategories)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onSelectionModeToggle()
        viewModel.onSubcategorySelectionChange("sub-1", true)
        viewModel.onSubcategorySelectionChange("sub-2", true)

        viewModel.state.assertValue {
            selectedCount shouldBe 2
            // Each fake subcategory() carries cardCount = 3.
            selectedCardCount shouldBe 6
        }
    }

    // --- progress rings/subtitle (ADR-0016) ---

    @Test
    fun `a summary with counts resolves each subcategory's studied and mastered counts`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val subcategories = listOf(subcategory("sub-1"), subcategory("sub-2"))
            flashcardRepository.subcategoriesToReturn = Result.success(subcategories)
            cardProgressRepository.seedSummary(
                ProgressSummary(
                    subcategories = mapOf(
                        "sub-1" to SubcategoryProgressSummary(studiedCount = 2, masteredCount = 1),
                        "sub-2" to SubcategoryProgressSummary(studiedCount = 3, masteredCount = 3),
                    ),
                ),
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.assertValue {
                isProgressResolved shouldBe true
                progressFor("sub-1") shouldBe SubcategoryProgress.Resolved(studiedCount = 2, masteredCount = 1)
                progressFor("sub-2") shouldBe SubcategoryProgress.Resolved(studiedCount = 3, masteredCount = 3)
            }
        }

    @Test
    fun `a subcategory absent from the summary resolves to all-zero, not unknown`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val subcategories = listOf(subcategory("sub-1"), subcategory("sub-2"))
            flashcardRepository.subcategoriesToReturn = Result.success(subcategories)
            cardProgressRepository.seedSummary(
                ProgressSummary(subcategories = mapOf("sub-1" to SubcategoryProgressSummary(studiedCount = 2, masteredCount = 1))),
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.assertValue {
                progressFor("sub-2") shouldBe SubcategoryProgress.Resolved(studiedCount = 0, masteredCount = 0)
            }
        }

    @Test
    fun `a User with no summary document at all resolves every subcategory to all-zero`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val subcategories = listOf(subcategory("sub-1"), subcategory("sub-2"))
            flashcardRepository.subcategoriesToReturn = Result.success(subcategories)
            // No seedSummary call: the fake returns Result.success(null), mirroring a User who has
            // never finished a session.

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.assertValue {
                isProgressResolved shouldBe true
                subcategories.forEach { subcategory ->
                    progressFor(subcategory.id) shouldBe SubcategoryProgress.Resolved(studiedCount = 0, masteredCount = 0)
                }
            }
        }

    /** Structural per ADR-0016, but a fixture bug producing the reverse must still fail loudly. */
    @Test
    fun `mastered is never greater than studied for any subcategory`() = runTest(mainDispatcherRule.testDispatcher) {
        val subcategories = listOf(subcategory("sub-1"), subcategory("sub-2"))
        flashcardRepository.subcategoriesToReturn = Result.success(subcategories)
        cardProgressRepository.seedSummary(
            ProgressSummary(
                subcategories = mapOf(
                    "sub-1" to SubcategoryProgressSummary(studiedCount = 5, masteredCount = 2),
                    "sub-2" to SubcategoryProgressSummary(studiedCount = 1, masteredCount = 1),
                ),
            ),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        subcategories.forEach { subcategory ->
            val progress = viewModel.state.value.progressFor(subcategory.id) as SubcategoryProgress.Resolved
            (progress.masteredCount <= progress.studiedCount) shouldBe true
        }
    }

    @Test
    fun `a failed summary read leaves the subcategory list intact with every subcategory unresolved and surfaces no error`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val subcategories = listOf(subcategory("sub-1"), subcategory("sub-2"))
            flashcardRepository.subcategoriesToReturn = Result.success(subcategories)
            cardProgressRepository.summaryResultToReturn = Result.failure(IllegalStateException("boom"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.assertValue {
                content shouldBe CategoryDetailsContentState.Subcategories(subcategories)
                isProgressResolved shouldBe false
                progressFor("sub-1") shouldBe SubcategoryProgress.Unresolved
                progressFor("sub-2") shouldBe SubcategoryProgress.Unresolved
            }
        }

    @Test
    fun `every subcategory is unresolved before the summary read completes`() = runTest(mainDispatcherRule.testDispatcher) {
        val subcategories = listOf(subcategory("sub-1"))
        flashcardRepository.subcategoriesToReturn = Result.success(subcategories)
        cardProgressRepository.seedSummary(
            ProgressSummary(subcategories = mapOf("sub-1" to SubcategoryProgressSummary(studiedCount = 1, masteredCount = 0))),
        )
        // Parks the summary read so it genuinely stays in flight past the point the subcategory list
        // has already resolved, instead of relying on both never having been dispatched yet.
        val summaryGate = CompletableDeferred<Unit>()
        cardProgressRepository.summaryReadGate = summaryGate

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.content shouldBe CategoryDetailsContentState.Subcategories(subcategories)
        viewModel.state.value.isProgressResolved shouldBe false
        viewModel.state.value.progressFor("sub-1") shouldBe SubcategoryProgress.Unresolved

        summaryGate.complete(Unit)
        advanceUntilIdle()

        viewModel.state.value.isProgressResolved shouldBe true
        viewModel.state.value.progressFor("sub-1") shouldBe SubcategoryProgress.Resolved(studiedCount = 1, masteredCount = 0)
    }

    @Test
    fun `progress arriving after the list neither reorders it nor changes row identity`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val subcategories = listOf(subcategory("sub-1"), subcategory("sub-2"), subcategory("sub-3"))
            flashcardRepository.subcategoriesToReturn = Result.success(subcategories)
            cardProgressRepository.seedSummary(
                ProgressSummary(subcategories = mapOf("sub-2" to SubcategoryProgressSummary(studiedCount = 1, masteredCount = 0))),
            )
            val summaryGate = CompletableDeferred<Unit>()
            cardProgressRepository.summaryReadGate = summaryGate

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Subcategory list is in, summary is still parked: rows exist but every one is unresolved.
            viewModel.state.value.content shouldBe CategoryDetailsContentState.Subcategories(subcategories)
            subcategories.forEach { viewModel.state.value.progressFor(it.id) shouldBe SubcategoryProgress.Unresolved }

            summaryGate.complete(Unit)
            advanceUntilIdle()

            // Releasing the summary changes only the progress values, never the list itself.
            viewModel.state.value.content shouldBe CategoryDetailsContentState.Subcategories(subcategories)
        }

    @Test
    fun `progress is identical in and out of Selection Mode`() = runTest(mainDispatcherRule.testDispatcher) {
        val subcategories = listOf(subcategory("sub-1"))
        flashcardRepository.subcategoriesToReturn = Result.success(subcategories)
        cardProgressRepository.seedSummary(
            ProgressSummary(subcategories = mapOf("sub-1" to SubcategoryProgressSummary(studiedCount = 2, masteredCount = 1))),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        val progressBeforeSelection = viewModel.state.value.progressFor("sub-1")

        viewModel.onSelectionModeToggle()

        viewModel.state.value.progressFor("sub-1") shouldBe progressBeforeSelection
    }
}
