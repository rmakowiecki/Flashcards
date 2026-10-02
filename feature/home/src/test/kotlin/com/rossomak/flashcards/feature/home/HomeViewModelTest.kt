package com.rossomak.flashcards.feature.home

import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.FavoriteItem.FavoriteSubcategory
import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressSummary
import com.rossomak.flashcards.core.domain.repository.FakeCardProgressRepository
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import com.rossomak.flashcards.core.domain.repository.FakeUserFavoritesRepository
import com.rossomak.flashcards.core.domain.usecase.ObserveFavoriteItemsUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveProgressSummaryUseCase
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Content
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Empty
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Loading
import com.rossomak.flashcards.testutil.MainDispatcherRule
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

private const val COMPOSE_ID = "compose"
private const val NAVIGATION_ID = "navigation"
private const val DENORMALIZED_CATEGORY_NAME = "Android (denormalized)"

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val flashcardRepository = FakeFlashcardRepository()
    private val userFavoritesRepository = FakeUserFavoritesRepository()
    private val cardProgressRepository = FakeCardProgressRepository()

    private val parentCategory = Category(
        id = "android",
        name = "Android",
        order = 0,
        subcategoryCount = 3,
        iconSvg = null,
        color = null,
        featuredSubcategoryNames = emptyList(),
    )
    private val composeSummary = ProgressSummary(
        subcategories = mapOf(COMPOSE_ID to SubcategoryProgressSummary(masteredCount = 2, studiedCount = 5)),
    )
    private val resolvableSubcategories = mutableListOf<Subcategory>()

    private fun createViewModel(): HomeViewModel = HomeViewModel(
        observeFavoriteItems = ObserveFavoriteItemsUseCase(userFavoritesRepository, flashcardRepository),
        observeProgressSummary = ObserveProgressSummaryUseCase(cardProgressRepository),
    )

    private fun subcategory(id: String, categoryName: String = parentCategory.name) = Subcategory(
        id = id,
        name = "Subcategory $id",
        categoryId = parentCategory.id,
        categoryName = categoryName,
        order = 0,
        cardCount = 10,
    )

    /** The favorites use case reads a favorite timestamp for every Subcategory it resolves, so the two fakes move together. */
    private suspend fun favorite(subcategoryId: String) {
        resolvableSubcategories += subcategory(subcategoryId)
        syncResolvableSubcategories()
        userFavoritesRepository.setSubcategoryFavorite(subcategoryId, isFavorite = true)
    }

    private suspend fun unfavorite(subcategoryId: String) {
        resolvableSubcategories.removeAll { it.id == subcategoryId }
        syncResolvableSubcategories()
        userFavoritesRepository.setSubcategoryFavorite(subcategoryId, isFavorite = false)
    }

    private fun syncResolvableSubcategories() {
        flashcardRepository.subcategoriesByIdsToReturn = Result.success(resolvableSubcategories.toList())
        flashcardRepository.categoriesByIdsToReturn = Result.success(listOf(parentCategory))
    }

    private fun HomeFavoritesState.subcategoryIds(): Set<String> =
        shouldBeInstanceOf<Content>().items.filterIsInstance<FavoriteSubcategory>().map { it.subcategory.id }.toSet()

    /** Every distinct Favorites state in order, so a transient state between two others shows up as its own item. */
    private fun HomeViewModel.favoritesStates(): Flow<HomeFavoritesState> = state.map { it.favorites }.distinctUntilChanged()

    @Test
    fun `favorites start Loading and become Content without passing through Empty`() = runTest(mainDispatcherRule.testDispatcher) {
        favorite(COMPOSE_ID)
        val viewModel = createViewModel()

        viewModel.favoritesStates().test {
            awaitItem() shouldBe Loading
            advanceUntilIdle()

            awaitItem().subcategoryIds() shouldBe setOf(COMPOSE_ID)
        }
    }

    @Test
    fun `favorites become Empty when the User has none`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.favorites shouldBe Empty
    }

    @Test
    fun `favorites move from Empty to Content when the first Favorite is added`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.state.value.favorites shouldBe Empty

        favorite(COMPOSE_ID)
        advanceUntilIdle()

        viewModel.state.value.favorites.subcategoryIds() shouldBe setOf(COMPOSE_ID)
    }

    @Test
    fun `a Favorite that appears joins the existing Content`() = runTest(mainDispatcherRule.testDispatcher) {
        favorite(COMPOSE_ID)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.favoritesStates().test {
            awaitItem().subcategoryIds() shouldBe setOf(COMPOSE_ID)

            favorite(NAVIGATION_ID)
            advanceUntilIdle()

            awaitItem().subcategoryIds() shouldBe setOf(COMPOSE_ID, NAVIGATION_ID)
        }
    }

    @Test
    fun `a Favorite that disappears leaves the others in Content`() = runTest(mainDispatcherRule.testDispatcher) {
        favorite(COMPOSE_ID)
        favorite(NAVIGATION_ID)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.favoritesStates().test {
            awaitItem().subcategoryIds() shouldBe setOf(COMPOSE_ID, NAVIGATION_ID)

            unfavorite(COMPOSE_ID)
            advanceUntilIdle()

            awaitItem().subcategoryIds() shouldBe setOf(NAVIGATION_ID)
        }
    }

    @Test
    fun `favorites move from Content to Empty when the last Favorite is removed`() = runTest(mainDispatcherRule.testDispatcher) {
        favorite(COMPOSE_ID)
        val viewModel = createViewModel()
        advanceUntilIdle()

        unfavorite(COMPOSE_ID)
        advanceUntilIdle()

        viewModel.state.value.favorites shouldBe Empty
    }

    @Test
    fun `progress resolves the flag and carries the summary`() = runTest(mainDispatcherRule.testDispatcher) {
        favorite(COMPOSE_ID)
        cardProgressRepository.seedSummary(composeSummary)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.progressSummary shouldBe composeSummary
        viewModel.state.value.isProgressResolved shouldBe true
    }

    @Test
    fun `a null progress emission resolves the flag with an absent summary`() = runTest(mainDispatcherRule.testDispatcher) {
        favorite(COMPOSE_ID)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.progressSummary shouldBe null
        viewModel.state.value.isProgressResolved shouldBe true
    }

    @Test
    fun `a parked progress read neither delays the cards nor resolves the flag until it arrives`() = runTest(mainDispatcherRule.testDispatcher) {
        val summaryGate = CompletableDeferred<Unit>()
        cardProgressRepository.summaryReadGate = summaryGate
        cardProgressRepository.seedSummary(composeSummary)
        favorite(COMPOSE_ID)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.favorites.subcategoryIds() shouldBe setOf(COMPOSE_ID)
        viewModel.state.value.isProgressResolved shouldBe false
        viewModel.state.value.progressSummary shouldBe null

        summaryGate.complete(Unit)
        advanceUntilIdle()

        viewModel.state.value.isProgressResolved shouldBe true
        viewModel.state.value.progressSummary shouldBe composeSummary
    }

    @Test
    fun `a failing progress flow leaves the cards alone and the flag unresolved`() = runTest(mainDispatcherRule.testDispatcher) {
        cardProgressRepository.summaryReadFailure = IllegalStateException("progress listener failed")
        favorite(COMPOSE_ID)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.favorites.subcategoryIds() shouldBe setOf(COMPOSE_ID)
        viewModel.state.value.isProgressResolved shouldBe false
    }

    @Test
    fun `a failing favorites flow turns Loading into Empty`() = runTest(mainDispatcherRule.testDispatcher) {
        userFavoritesRepository.favoritesReadFailure = IllegalStateException("favorites listener failed")

        val viewModel = createViewModel()
        viewModel.state.value.favorites shouldBe Loading
        advanceUntilIdle()

        viewModel.state.value.favorites shouldBe Empty
    }

    @Test
    fun `a favorites failure after the first emission leaves the Content alone`() = runTest(mainDispatcherRule.testDispatcher) {
        val favoriteItems = mockk<ObserveFavoriteItemsUseCase>()
        coEvery { favoriteItems() } returns flow {
            emit(listOf(FavoriteSubcategory(subcategory(COMPOSE_ID), parentCategory, favoritedAt = Instant.EPOCH)))
            error("favorites listener failed")
        }

        val viewModel = HomeViewModel(
            observeFavoriteItems = favoriteItems,
            observeProgressSummary = ObserveProgressSummaryUseCase(cardProgressRepository),
        )
        advanceUntilIdle()

        viewModel.state.value.favorites.subcategoryIds() shouldBe setOf(COMPOSE_ID)
    }

    @Test
    fun `selecting a Favorite Category emits its Category Details destination`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.onFavoriteCategorySelect(parentCategory)
            advanceUntilIdle()

            awaitItem() shouldBe HomeDestination.CategoryDetails(categoryId = parentCategory.id, categoryName = parentCategory.name)
            expectNoEvents()
        }
    }

    @Test
    fun `selecting a Favorite Subcategory emits its Subcategory Details destination with the Subcategory's own category name`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val subcategory = subcategory(COMPOSE_ID, categoryName = DENORMALIZED_CATEGORY_NAME)
            val viewModel = createViewModel()

            viewModel.events.test {
                viewModel.onFavoriteSubcategorySelect(subcategory)
                advanceUntilIdle()

                awaitItem() shouldBe HomeDestination.SubcategoryDetails(
                    categoryId = parentCategory.id,
                    categoryName = DENORMALIZED_CATEGORY_NAME,
                    subcategoryId = COMPOSE_ID,
                    subcategoryName = subcategory.name,
                )
                expectNoEvents()
            }
        }

    @Test
    fun `starting a Quick session from a Favorite Category emits a destination carrying only the Category`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createViewModel()

            viewModel.events.test {
                viewModel.onFavoriteCategoryQuickSessionStart(parentCategory)
                advanceUntilIdle()

                awaitItem() shouldBe HomeDestination.QuickSessionPreviewStudySession(
                    categoryId = parentCategory.id,
                    categoryName = parentCategory.name,
                )
                expectNoEvents()
            }
        }

    @Test
    fun `starting a session from a Favorite Subcategory emits a single-subcategory Preview destination`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val subcategory = subcategory(COMPOSE_ID, categoryName = DENORMALIZED_CATEGORY_NAME)
            val viewModel = createViewModel()

            viewModel.events.test {
                viewModel.onFavoriteSubcategorySessionStart(subcategory)
                advanceUntilIdle()

                awaitItem() shouldBe HomeDestination.SubcategoryPreviewStudySession(
                    categoryId = parentCategory.id,
                    categoryName = DENORMALIZED_CATEGORY_NAME,
                    subcategoryId = COMPOSE_ID,
                    subcategoryName = subcategory.name,
                )
                expectNoEvents()
            }
        }
}
