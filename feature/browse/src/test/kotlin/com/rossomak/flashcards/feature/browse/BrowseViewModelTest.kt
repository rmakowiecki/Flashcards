package com.rossomak.flashcards.feature.browse

import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressSummary
import com.rossomak.flashcards.core.domain.repository.FakeCardProgressRepository
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import com.rossomak.flashcards.core.domain.usecase.GetCategoriesUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveProgressSummaryUseCase
import com.rossomak.flashcards.core.domain.usecase.SearchCategoriesUseCase
import com.rossomak.flashcards.feature.browse.details.category.SubcategoryProgress
import com.rossomak.flashcards.testutil.MainDispatcherRule
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val flashcardRepository = FakeFlashcardRepository()
    private val getCategories = GetCategoriesUseCase(flashcardRepository)
    private val searchCategories = SearchCategoriesUseCase(flashcardRepository)
    private val cardProgressRepository = FakeCardProgressRepository()
    private val observeProgressSummary = ObserveProgressSummaryUseCase(cardProgressRepository)

    private val categoryId = "cat-1"
    private val categoryName = "Android"

    private val android = Category(
        id = categoryId,
        name = categoryName,
        order = 0,
        subcategoryCount = 2,
        iconSvg = null,
        color = null,
        featuredSubcategoryNames = listOf("Compose", "Coroutines"),
    )

    private val compose = Subcategory(
        id = "android-compose",
        name = "Compose",
        categoryId = categoryId,
        categoryName = categoryName,
        order = 0,
        cardCount = 12,
    )

    private val coroutines = Subcategory(
        id = "android-coroutines",
        name = "Coroutines",
        categoryId = categoryId,
        categoryName = categoryName,
        order = 1,
        cardCount = 8,
    )

    private fun createViewModel(): BrowseViewModel =
        BrowseViewModel(getCategories, searchCategories, observeProgressSummary)

    @Test
    fun `onCategorySelected emits CategoryDetails with id and name`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onCategorySelected(categoryId, categoryName)

        viewModel.events.test {
            awaitItem() shouldBe BrowseNavigationDestination.CategoryDetails(categoryId, categoryName)
        }
    }

    @Test
    fun `onSubcategorySelect emits SubcategoryDetails carrying the parent from the matched subcategory`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createViewModel()
            viewModel.onSubcategorySelect(compose)

            viewModel.events.test {
                awaitItem() shouldBe BrowseNavigationDestination.SubcategoryDetails(
                    categoryId = categoryId,
                    categoryName = categoryName,
                    subcategoryId = compose.id,
                    subcategoryName = compose.name,
                )
            }
        }

    @Test
    fun `onSubcategorySessionStart emits PreviewStudySession`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onSubcategorySessionStart(compose)

        viewModel.events.test {
            awaitItem() shouldBe BrowseNavigationDestination.PreviewStudySession(
                categoryId = categoryId,
                categoryName = categoryName,
                subcategoryId = compose.id,
                subcategoryName = compose.name,
            )
        }
    }

    @Test
    fun `a typed query is not searched until the debounce elapses`() = runTest(mainDispatcherRule.testDispatcher) {
        flashcardRepository.categoriesToReturn = Result.success(listOf(android))
        flashcardRepository.searchResultsByPrefix["compose"] = Result.success(listOf(compose))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onSearchQueryChange("compose")

        advanceTimeBy(DEBOUNCE_MILLIS - 1)
        flashcardRepository.searchedPrefixes shouldContainExactly emptyList()

        advanceUntilIdle()
        flashcardRepository.searchedPrefixes shouldContainExactly listOf("compose")
        val status = viewModel.state.value.searchStatus
        status.shouldBeInstanceOf<SearchStatus.Results>()
        status.results.subcategories shouldContainExactly listOf(compose)
    }

    @Test
    fun `a query still below the debounce reports loading rather than the too-short prompt once it is long enough`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.categoriesToReturn = Result.success(listOf(android))
            flashcardRepository.searchResultsByPrefix["ap"] = Result.success(emptyList())

            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onSearchQueryChange("app")
            advanceUntilIdle()
            viewModel.onSearchQueryChange("ap")

            viewModel.state.value.searchStatus shouldBe SearchStatus.Loading

            advanceUntilIdle()
            flashcardRepository.searchedPrefixes shouldContainExactly listOf("app", "ap")
        }

    @Test
    fun `replaying the same query preserves populated search results`() = runTest(mainDispatcherRule.testDispatcher) {
        // Regresses the back-navigation bug: SyncSearchBarState re-collects the text field's
        // current value into a fresh LaunchedEffect on re-composition (e.g. returning from a
        // result), replaying the unchanged query. onSearchQueryChange must no-op on that replay
        // rather than reset searchStatus, since distinctUntilChanged downstream never reruns
        // the search for a query that didn't actually change.
        flashcardRepository.categoriesToReturn = Result.success(listOf(android))
        flashcardRepository.searchResultsByPrefix["compose"] = Result.success(listOf(compose))

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onSearchQueryChange("compose")
        advanceUntilIdle()

        viewModel.onSearchQueryChange("compose")

        val status = viewModel.state.value.searchStatus
        status.shouldBeInstanceOf<SearchStatus.Results>()
        status.results.subcategories shouldContainExactly listOf(compose)
    }

    @Test
    fun `keystrokes inside the debounce window only search the final query`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.categoriesToReturn = Result.success(listOf(android))
            flashcardRepository.searchResultsByPrefix["compose"] = Result.success(listOf(compose))

            val viewModel = createViewModel()
            advanceUntilIdle()

            listOf("co", "com", "compose").forEach { query ->
                viewModel.onSearchQueryChange(query)
                advanceTimeBy(DEBOUNCE_MILLIS / 2)
            }
            advanceUntilIdle()

            flashcardRepository.searchedPrefixes shouldContainExactly listOf("compose")
        }

    @Test
    fun `a query below the minimum length reports the prompt status rather than no matches`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSearchQueryChange("c")

            viewModel.state.value.searchStatus shouldBe SearchStatus.Prompt

            advanceUntilIdle()
            flashcardRepository.searchedPrefixes shouldContainExactly emptyList()
        }

    @Test
    fun `a query at or above the minimum length reports loading until the debounce elapses`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.categoriesToReturn = Result.success(listOf(android))
            flashcardRepository.searchResultsByPrefix["compose"] = Result.success(listOf(compose))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSearchQueryChange("compose")

            viewModel.state.value.searchStatus shouldBe SearchStatus.Loading
        }

    @Test
    fun `a search matching nothing reports NoMatch rather than Results`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.searchResultsByPrefix["xyz"] = Result.success(emptyList())

            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onSearchQueryChange("xyz")
            advanceUntilIdle()

            viewModel.state.value.searchStatus shouldBe SearchStatus.NoMatch
        }

    @Test
    fun `a failed search reports Error instead of claiming nothing matched`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.searchResultsByPrefix["compose"] = Result.failure(IllegalStateException("offline"))

            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onSearchQueryChange("compose")
            advanceUntilIdle()

            viewModel.state.value.searchStatus shouldBe SearchStatus.Error
        }

    @Test
    fun `onSearchDismiss empties the query and leaves search`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onSearchActivate()
        viewModel.onSearchQueryChange("compose")

        viewModel.onSearchDismiss()

        viewModel.state.value.searchQuery shouldBe ""
        viewModel.state.value.isSearchActive shouldBe false
        viewModel.state.value.searchStatus shouldBe SearchStatus.Prompt
    }

    // --- progress rings/subtitle on matched subcategories (ADR-0016) ---

    @Test
    fun `a summary with counts resolves a matched subcategory's studied and mastered counts`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.searchResultsByPrefix["compose"] = Result.success(listOf(compose))
            cardProgressRepository.seedSummary(
                ProgressSummary(
                    subcategories = mapOf(compose.id to SubcategoryProgressSummary(studiedCount = 4, masteredCount = 2)),
                ),
            )

            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onSearchQueryChange("compose")
            advanceUntilIdle()

            viewModel.state.value.isProgressResolved shouldBe true
            viewModel.state.value.progressFor(compose.id) shouldBe SubcategoryProgress.Resolved(studiedCount = 4, masteredCount = 2)
        }

    @Test
    fun `a matched subcategory absent from the summary resolves to all-zero, not unknown`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.searchResultsByPrefix["compose"] = Result.success(listOf(compose))
            // No seedSummary call: the fake returns Result.success(null), mirroring a User who has
            // never finished a session.

            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onSearchQueryChange("compose")
            advanceUntilIdle()

            viewModel.state.value.progressFor(compose.id) shouldBe SubcategoryProgress.Resolved(studiedCount = 0, masteredCount = 0)
        }

    @Test
    fun `a summary held behind a gate leaves search results intact with the matched subcategory unresolved until it arrives`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.searchResultsByPrefix["compose"] = Result.success(listOf(compose))
            cardProgressRepository.seedSummary(
                ProgressSummary(
                    subcategories = mapOf(compose.id to SubcategoryProgressSummary(studiedCount = 4, masteredCount = 2)),
                ),
            )
            // Parks init's own collectProgressSummary() so the search below genuinely runs while the
            // listener's first emission is still in flight, rather than the fake resolving it up front.
            val summaryGate = CompletableDeferred<Unit>()
            cardProgressRepository.summaryReadGate = summaryGate

            val viewModel = createViewModel()
            viewModel.onSearchQueryChange("compose")
            advanceUntilIdle()

            val status = viewModel.state.value.searchStatus
            status.shouldBeInstanceOf<SearchStatus.Results>()
            status.results.subcategories shouldContainExactly listOf(compose)
            viewModel.state.value.isProgressResolved shouldBe false
            viewModel.state.value.progressFor(compose.id) shouldBe SubcategoryProgress.Unresolved

            summaryGate.complete(Unit)
            advanceUntilIdle()

            viewModel.state.value.isProgressResolved shouldBe true
            viewModel.state.value.progressFor(compose.id) shouldBe SubcategoryProgress.Resolved(studiedCount = 4, masteredCount = 2)
        }

    @Test
    fun `a later summary update after search results are shown replaces the resolved values`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.searchResultsByPrefix["compose"] = Result.success(listOf(compose))
            cardProgressRepository.seedSummary(
                ProgressSummary(subcategories = mapOf(compose.id to SubcategoryProgressSummary(studiedCount = 1, masteredCount = 0))),
            )

            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onSearchQueryChange("compose")
            advanceUntilIdle()
            viewModel.state.value.progressFor(compose.id) shouldBe SubcategoryProgress.Resolved(studiedCount = 1, masteredCount = 0)

            // Mirrors a session finishing (or connectivity returning) after the screen is already
            // showing search results — the listener re-fires and the ring updates in place.
            cardProgressRepository.seedSummary(
                ProgressSummary(subcategories = mapOf(compose.id to SubcategoryProgressSummary(studiedCount = 4, masteredCount = 2))),
            )
            advanceUntilIdle()

            viewModel.state.value.progressFor(compose.id) shouldBe SubcategoryProgress.Resolved(studiedCount = 4, masteredCount = 2)
        }

    @Test
    fun `progress arriving after results neither reorders them nor changes their identity`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.searchResultsByPrefix["co"] = Result.success(listOf(compose, coroutines))
            cardProgressRepository.seedSummary(
                ProgressSummary(
                    subcategories = mapOf(
                        compose.id to SubcategoryProgressSummary(studiedCount = 1, masteredCount = 0),
                        coroutines.id to SubcategoryProgressSummary(studiedCount = 3, masteredCount = 1),
                    ),
                ),
            )

            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onSearchQueryChange("co")
            advanceUntilIdle()

            val status = viewModel.state.value.searchStatus
            status.shouldBeInstanceOf<SearchStatus.Results>()
            status.results.subcategories shouldContainExactly listOf(compose, coroutines)
        }

    /** Structural per ADR-0016, but a fixture bug producing the reverse must still fail loudly. */
    @Test
    fun `mastered is never greater than studied for a matched subcategory`() = runTest(mainDispatcherRule.testDispatcher) {
        flashcardRepository.searchResultsByPrefix["compose"] = Result.success(listOf(compose))
        cardProgressRepository.seedSummary(
            ProgressSummary(subcategories = mapOf(compose.id to SubcategoryProgressSummary(studiedCount = 5, masteredCount = 2))),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onSearchQueryChange("compose")
        advanceUntilIdle()

        val progress = viewModel.state.value.progressFor(compose.id) as SubcategoryProgress.Resolved
        (progress.masteredCount <= progress.studiedCount) shouldBe true
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 500L
    }
}
