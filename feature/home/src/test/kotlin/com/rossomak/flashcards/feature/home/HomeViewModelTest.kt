package com.rossomak.flashcards.feature.home

import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.FavoriteItem.FavoriteSubcategory
import com.rossomak.flashcards.core.domain.model.FavoriteItemsResult
import com.rossomak.flashcards.core.domain.model.FavoriteItemsResult.Resolved
import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.RecentItem
import com.rossomak.flashcards.core.domain.model.RecentSession
import com.rossomak.flashcards.core.domain.model.SessionSourceType
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Custom
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Quick
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressSummary
import com.rossomak.flashcards.core.domain.repository.FakeCardProgressRepository
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import com.rossomak.flashcards.core.domain.repository.FakeRecentSessionsRepository
import com.rossomak.flashcards.core.domain.repository.FakeUserFavoritesRepository
import com.rossomak.flashcards.core.domain.usecase.ObserveFavoriteItemsUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveProgressSummaryUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveRecentSessionsUseCase
import com.rossomak.flashcards.feature.home.HomeBody.FirstSession
import com.rossomak.flashcards.feature.home.HomeBody.LoadError
import com.rossomak.flashcards.feature.home.HomeBody.Resolving
import com.rossomak.flashcards.feature.home.HomeBody.Sections
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Content
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Empty
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Failed
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Loading
import com.rossomak.flashcards.feature.home.HomeRecentsState.Content as RecentsContent
import com.rossomak.flashcards.feature.home.HomeRecentsState.Empty as RecentsEmpty
import com.rossomak.flashcards.feature.home.HomeRecentsState.Failed as RecentsFailed
import com.rossomak.flashcards.feature.home.HomeRecentsState.Loading as RecentsLoading
import com.rossomak.flashcards.feature.home.HomeViewModel.Companion.REVEAL_CEILING
import com.rossomak.flashcards.testutil.MainDispatcherRule
import com.rossomak.flashcards.testutil.assertValue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

private const val COMPOSE_ID = "compose"
private const val NAVIGATION_ID = "navigation"
private const val DENORMALIZED_CATEGORY_NAME = "Android (denormalized)"
private const val COROUTINES_ID = "coroutines"
private const val OLDER_SESSION_ID = "older-session"
private const val NEWER_SESSION_ID = "newer-session"
private const val NEWEST_SESSION_ID = "newest-session"
private const val CUSTOM_SESSION_ID = "custom-session"
private const val UNREAD_CATEGORY_ID = "unread-category"

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val flashcardRepository = FakeFlashcardRepository()
    private val userFavoritesRepository = FakeUserFavoritesRepository()
    private val cardProgressRepository = FakeCardProgressRepository()
    private val recentSessionsRepository = FakeRecentSessionsRepository()

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

    private fun createViewModel(
        observeFavoriteItems: ObserveFavoriteItemsUseCase = ObserveFavoriteItemsUseCase(userFavoritesRepository, flashcardRepository),
        observeRecentSessions: ObserveRecentSessionsUseCase = ObserveRecentSessionsUseCase(recentSessionsRepository, flashcardRepository),
    ): HomeViewModel = HomeViewModel(
        observeFavoriteItems = observeFavoriteItems,
        observeProgressSummary = ObserveProgressSummaryUseCase(cardProgressRepository),
        observeRecentSessions = observeRecentSessions,
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

    private fun composeFavorite() = FavoriteSubcategory(subcategory(COMPOSE_ID), parentCategory, favoritedAt = Instant.EPOCH)

    private fun recentItem() = RecentItem(recentSession(OLDER_SESSION_ID, Quick, listOf(COMPOSE_ID)), parentCategory)

    private fun favoriteItemsReturning(results: Flow<FavoriteItemsResult>): ObserveFavoriteItemsUseCase = mockk {
        coEvery { this@mockk() } returns results
    }

    private fun recentSessionsReturning(results: Flow<List<RecentItem>>): ObserveRecentSessionsUseCase = mockk {
        coEvery { this@mockk() } returns results
    }

    private fun HomeFavoritesState.subcategoryIds(): Set<String> =
        shouldBeInstanceOf<Content>().items.filterIsInstance<FavoriteSubcategory>().map { it.subcategory.id }.toSet()

    private fun recentSession(
        id: String,
        sourceType: SessionSourceType,
        subcategoryIds: List<String>,
        categoryId: String = parentCategory.id,
        voiceAnsweringEnabled: Boolean = false,
    ) = RecentSession.Rated(
        id = id,
        startedAt = Instant.EPOCH,
        durationSeconds = 300,
        sourceType = sourceType,
        categoryId = categoryId,
        categoryName = parentCategory.name,
        subcategoryIds = subcategoryIds,
        subcategoryNames = subcategoryIds.map { subcategory(it).name },
        studiedCount = 10,
        xpTotal = 120,
        voiceAnsweringEnabled = voiceAnsweringEnabled,
    )

    private fun fastRecentSession(sourceType: SessionSourceType, readAloudEnabled: Boolean) = RecentSession.Fast(
        id = OLDER_SESSION_ID,
        startedAt = Instant.EPOCH,
        durationSeconds = 300,
        sourceType = sourceType,
        categoryId = parentCategory.id,
        categoryName = parentCategory.name,
        subcategoryIds = listOf(COMPOSE_ID),
        subcategoryNames = listOf(subcategory(COMPOSE_ID).name),
        studiedCount = 10,
        xpTotal = 40,
        readAloudEnabled = readAloudEnabled,
    )

    private fun stubCategoryFetch() {
        flashcardRepository.categoriesByIdsToReturn = Result.success(listOf(parentCategory))
    }

    private fun HomeRecentsState.items(): List<RecentItem> = shouldBeInstanceOf<RecentsContent>().items

    private fun HomeRecentsState.sessionIds(): List<String> = items().map { it.session.id }

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
    fun `a failing favorites flow turns Loading into Failed`() = runTest(mainDispatcherRule.testDispatcher) {
        userFavoritesRepository.favoritesReadFailure = IllegalStateException("favorites listener failed")

        val viewModel = createViewModel()
        viewModel.state.value.favorites shouldBe Loading
        advanceUntilIdle()

        viewModel.state.value.favorites shouldBe Failed
    }

    @Test
    fun `a favorites failure after the first emission leaves the Content alone`() = runTest(mainDispatcherRule.testDispatcher) {
        val favoriteItems = mockk<ObserveFavoriteItemsUseCase>()
        coEvery { favoriteItems() } returns flow {
            emit(Resolved(listOf(composeFavorite())))
            error("favorites listener failed")
        }

        val viewModel = createViewModel(observeFavoriteItems = favoriteItems)
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

    @Test
    fun `selecting a single-subcategory Rated Recent replays its Subcategory, mode and Voice Answering under its stored names`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val compose = subcategory(COMPOSE_ID)
            val session = recentSession(OLDER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID), voiceAnsweringEnabled = true)
            val viewModel = createViewModel()

            viewModel.events.test {
                viewModel.onRecentSelect(RecentItem(session, parentCategory))
                advanceUntilIdle()

                awaitItem() shouldBe HomeDestination.RecentPreviewStudySession(
                    categoryId = parentCategory.id,
                    categoryName = parentCategory.name,
                    sourceType = SingleSubcategory,
                    subcategoryIds = listOf(compose.id),
                    subcategoryNames = listOf(compose.name),
                    studyMode = StudyMode.Rated,
                    voiceAnsweringEnabled = true,
                    readAloudEnabled = null,
                )
                expectNoEvents()
            }
        }

    @Test
    fun `selecting a Quick Fast Recent replays the whole Category with read-aloud`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.onRecentSelect(RecentItem(fastRecentSession(Quick, readAloudEnabled = true), parentCategory))
            advanceUntilIdle()

            awaitItem() shouldBe HomeDestination.RecentPreviewStudySession(
                categoryId = parentCategory.id,
                categoryName = parentCategory.name,
                sourceType = Quick,
                subcategoryIds = emptyList(),
                subcategoryNames = emptyList(),
                studyMode = StudyMode.Fast,
                voiceAnsweringEnabled = null,
                readAloudEnabled = true,
            )
            expectNoEvents()
        }
    }

    @Test
    fun `selecting a Custom Recent replays every stored Subcategory in stored order`() = runTest(mainDispatcherRule.testDispatcher) {
        val storedIds = listOf(NAVIGATION_ID, COROUTINES_ID, COMPOSE_ID)
        val session = recentSession(CUSTOM_SESSION_ID, Custom, storedIds)
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.onRecentSelect(RecentItem(session, parentCategory))
            advanceUntilIdle()

            val destination = awaitItem().shouldBeInstanceOf<HomeDestination.RecentPreviewStudySession>()
            destination.sourceType shouldBe Custom
            destination.subcategoryIds shouldBe storedIds
            destination.subcategoryNames shouldBe storedIds.map { subcategory(it).name }
        }
    }

    @Test
    fun `selecting a Recent whose Category could not be read still replays it under its stored names`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val session = recentSession(OLDER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID))
            val viewModel = createViewModel()

            viewModel.events.test {
                viewModel.onRecentSelect(RecentItem(session, category = null))
                advanceUntilIdle()

                val destination = awaitItem().shouldBeInstanceOf<HomeDestination.RecentPreviewStudySession>()
                destination.categoryName shouldBe parentCategory.name
                destination.subcategoryNames shouldBe listOf(subcategory(COMPOSE_ID).name)
            }
        }

    @Test
    fun `recents become Empty when the User has no sessions`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.recents shouldBe RecentsEmpty
    }

    @Test
    fun `recents start Loading and become Content in the repository's newest-first order`() = runTest(mainDispatcherRule.testDispatcher) {
        stubCategoryFetch()
        recentSessionsRepository.setRecentSessions(
            listOf(
                recentSession(NEWER_SESSION_ID, SingleSubcategory, listOf(NAVIGATION_ID)),
                recentSession(OLDER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID)),
            )
        )
        val viewModel = createViewModel()

        viewModel.state.map { it.recents }.distinctUntilChanged().test {
            awaitItem() shouldBe RecentsLoading
            advanceUntilIdle()

            awaitItem().sessionIds() shouldBe listOf(NEWER_SESSION_ID, OLDER_SESSION_ID)
        }
    }

    @Test
    fun `a Recent carries its looked-up Category`() = runTest(mainDispatcherRule.testDispatcher) {
        stubCategoryFetch()
        val session = recentSession(OLDER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID))
        recentSessionsRepository.setRecentSessions(listOf(session))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.recents.items() shouldBe listOf(RecentItem(session, parentCategory))
    }

    @Test
    fun `a Recent whose Category is not found is kept without a Category`() = runTest(mainDispatcherRule.testDispatcher) {
        stubCategoryFetch()
        val unreadSession = recentSession(NEWER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID), categoryId = UNREAD_CATEGORY_ID)
        val readSession = recentSession(OLDER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID))
        recentSessionsRepository.setRecentSessions(listOf(unreadSession, readSession))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.recents.items() shouldBe listOf(RecentItem(unreadSession, category = null), RecentItem(readSession, parentCategory))
    }

    @Test
    fun `Recents whose Category fetch keeps failing still show, without a Category`() = runTest(mainDispatcherRule.testDispatcher) {
        flashcardRepository.categoriesByIdsToReturn = Result.failure(IllegalStateException("categories fetch failed"))
        val session = recentSession(OLDER_SESSION_ID, Quick, listOf(COMPOSE_ID))
        recentSessionsRepository.setRecentSessions(listOf(session))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.recents.items() shouldBe listOf(RecentItem(session, category = null))
    }

    @Test
    fun `a session that appears later joins the top of the Content`() = runTest(mainDispatcherRule.testDispatcher) {
        stubCategoryFetch()
        val olderSession = recentSession(OLDER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID))
        recentSessionsRepository.setRecentSessions(listOf(olderSession))
        val viewModel = createViewModel()
        advanceUntilIdle()

        recentSessionsRepository.setRecentSessions(listOf(recentSession(NEWEST_SESSION_ID, Quick, listOf(COMPOSE_ID)), olderSession))
        advanceUntilIdle()

        viewModel.state.value.recents.sessionIds() shouldBe listOf(NEWEST_SESSION_ID, OLDER_SESSION_ID)
    }

    @Test
    fun `a session that goes leaves the others in Content`() = runTest(mainDispatcherRule.testDispatcher) {
        stubCategoryFetch()
        val olderSession = recentSession(OLDER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID))
        recentSessionsRepository.setRecentSessions(listOf(recentSession(NEWER_SESSION_ID, Quick, listOf(COMPOSE_ID)), olderSession))
        val viewModel = createViewModel()
        advanceUntilIdle()

        recentSessionsRepository.setRecentSessions(listOf(olderSession))
        advanceUntilIdle()

        viewModel.state.value.recents.sessionIds() shouldBe listOf(OLDER_SESSION_ID)
    }

    @Test
    fun `recents move from Content to Empty when the last session goes`() = runTest(mainDispatcherRule.testDispatcher) {
        stubCategoryFetch()
        recentSessionsRepository.setRecentSessions(listOf(recentSession(OLDER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID))))
        val viewModel = createViewModel()
        advanceUntilIdle()

        recentSessionsRepository.setRecentSessions(emptyList())
        advanceUntilIdle()

        viewModel.state.value.recents shouldBe RecentsEmpty
    }

    @Test
    fun `a parked Recents read leaves Recents Loading and does not delay the Favorites`() = runTest(mainDispatcherRule.testDispatcher) {
        recentSessionsRepository.recentSessionsReadGate = CompletableDeferred()
        favorite(COMPOSE_ID)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.favorites.subcategoryIds() shouldBe setOf(COMPOSE_ID)
        viewModel.state.value.recents shouldBe RecentsLoading
    }

    @Test
    fun `a parked favorites read leaves Favorites Loading and does not delay the Recents`() = runTest(mainDispatcherRule.testDispatcher) {
        userFavoritesRepository.favoritesReadGate = CompletableDeferred()
        stubCategoryFetch()
        recentSessionsRepository.setRecentSessions(listOf(recentSession(OLDER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID))))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.favorites shouldBe Loading
        viewModel.state.value.recents.sessionIds() shouldBe listOf(OLDER_SESSION_ID)
    }

    @Test
    fun `a failing Recents flow turns Loading into Failed and leaves the Favorites alone`() = runTest(mainDispatcherRule.testDispatcher) {
        recentSessionsRepository.recentSessionsReadFailure = IllegalStateException("recents listener failed")
        favorite(COMPOSE_ID)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.recents shouldBe RecentsFailed
        viewModel.state.value.favorites.subcategoryIds() shouldBe setOf(COMPOSE_ID)
    }

    @Test
    fun `a Recents failure after the first emission leaves the Content alone`() = runTest(mainDispatcherRule.testDispatcher) {
        val recentItem = RecentItem(recentSession(OLDER_SESSION_ID, Quick, listOf(COMPOSE_ID)), parentCategory)
        val recentSessions = mockk<ObserveRecentSessionsUseCase>()
        coEvery { recentSessions() } returns flow {
            emit(listOf(recentItem))
            error("recents listener failed")
        }

        val viewModel = createViewModel(observeRecentSessions = recentSessions)
        advanceUntilIdle()

        viewModel.state.value.recents.items() shouldBe listOf(recentItem)
    }

    @Test
    fun `a failing favorites flow leaves the Recents alone`() = runTest(mainDispatcherRule.testDispatcher) {
        userFavoritesRepository.favoritesReadFailure = IllegalStateException("favorites listener failed")
        stubCategoryFetch()
        recentSessionsRepository.setRecentSessions(listOf(recentSession(OLDER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID))))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.favorites shouldBe Failed
        viewModel.state.value.recents.sessionIds() shouldBe listOf(OLDER_SESSION_ID)
    }

    @Test
    fun `a favorites flow that completes before its first emission turns Loading into Failed`() = runTest(mainDispatcherRule.testDispatcher) {
        userFavoritesRepository.favoritesEmissionLimit = 0

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.favorites shouldBe Failed
    }

    @Test
    fun `a Recents flow that completes before its first emission turns Loading into Failed`() = runTest(mainDispatcherRule.testDispatcher) {
        recentSessionsRepository.recentSessionsEmissionLimit = 0

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.recents shouldBe RecentsFailed
    }

    @Test
    fun `a favorites flow that completes after the first emission leaves the Content alone`() = runTest(mainDispatcherRule.testDispatcher) {
        favorite(COMPOSE_ID)
        userFavoritesRepository.favoritesEmissionLimit = 1

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.favorites.subcategoryIds() shouldBe setOf(COMPOSE_ID)
    }

    @Test
    fun `a Recents flow that completes after the first emission leaves the Content alone`() = runTest(mainDispatcherRule.testDispatcher) {
        recentSessionsRepository.setRecentSessions(listOf(recentItem().session))
        flashcardRepository.categoriesByIdsToReturn = Result.success(listOf(parentCategory))
        recentSessionsRepository.recentSessionsEmissionLimit = 1

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.recents.items() shouldBe listOf(recentItem())
    }

    @Test
    fun `a favorites use case that fails to build its flow turns Loading into Failed`() = runTest(mainDispatcherRule.testDispatcher) {
        val favoriteItems = mockk<ObserveFavoriteItemsUseCase>()
        coEvery { favoriteItems() } throws IllegalStateException("favorites use case failed")

        val viewModel = createViewModel(observeFavoriteItems = favoriteItems)
        advanceUntilIdle()

        viewModel.state.value.favorites shouldBe Failed
    }

    @Test
    fun `Favorites that cannot be fully fetched before the first emission are Failed, not Empty`() =
        runTest(mainDispatcherRule.testDispatcher) {
            favorite(COMPOSE_ID)
            flashcardRepository.subcategoriesByIdsToReturn = Result.failure(IllegalStateException("subcategories fetch failed"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.assertValue {
                favorites shouldBe Failed
                body shouldBe LoadError
            }
        }

    @Test
    fun `Favorites that cannot be fully fetched after the first emission leave the Content alone`() =
        runTest(mainDispatcherRule.testDispatcher) {
            favorite(COMPOSE_ID)
            val viewModel = createViewModel()
            advanceUntilIdle()

            flashcardRepository.subcategoriesByIdsToReturn = Result.failure(IllegalStateException("subcategories fetch failed"))
            userFavoritesRepository.setSubcategoryFavorite(NAVIGATION_ID, isFavorite = true)
            advanceUntilIdle()

            viewModel.state.value.favorites.subcategoryIds() shouldBe setOf(COMPOSE_ID)
        }

    @Test
    fun `a Resolved emission after an Unresolved one recovers Failed Favorites`() = runTest(mainDispatcherRule.testDispatcher) {
        favorite(COMPOSE_ID)
        flashcardRepository.subcategoriesByIdsToReturn = Result.failure(IllegalStateException("subcategories fetch failed"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.state.value.favorites shouldBe Failed

        favorite(NAVIGATION_ID)
        advanceUntilIdle()

        viewModel.state.value.favorites.subcategoryIds() shouldBe setOf(COMPOSE_ID, NAVIGATION_ID)
    }

    @Test
    fun `the body is Resolving while both sections load and the ceiling has not elapsed`() = runTest(mainDispatcherRule.testDispatcher) {
        userFavoritesRepository.favoritesReadGate = CompletableDeferred()
        recentSessionsRepository.recentSessionsReadGate = CompletableDeferred()

        val viewModel = createViewModel()
        runCurrent()

        viewModel.state.value.body shouldBe Resolving
    }

    @Test
    fun `the body stays Resolving while one section loads, until the ceiling elapses`() = runTest(mainDispatcherRule.testDispatcher) {
        recentSessionsRepository.recentSessionsReadGate = CompletableDeferred()
        favorite(COMPOSE_ID)

        val viewModel = createViewModel()
        runCurrent()
        viewModel.state.assertValue {
            favorites.subcategoryIds() shouldBe setOf(COMPOSE_ID)
            body shouldBe Resolving
        }

        advanceTimeBy(REVEAL_CEILING)
        runCurrent()

        viewModel.state.value.body.shouldBeInstanceOf<Sections>().favoriteItems.size shouldBe 1
    }

    @Test
    fun `a section that arrives after the ceiling joins the Sections in place`() = runTest(mainDispatcherRule.testDispatcher) {
        val recentsGate = CompletableDeferred<Unit>()
        recentSessionsRepository.recentSessionsReadGate = recentsGate
        stubCategoryFetch()
        recentSessionsRepository.setRecentSessions(listOf(recentSession(OLDER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID))))
        favorite(COMPOSE_ID)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.state.value.body.shouldBeInstanceOf<Sections>().recentItems shouldBe emptyList()

        recentsGate.complete(Unit)
        advanceUntilIdle()

        val sections = viewModel.state.value.body.shouldBeInstanceOf<Sections>()
        sections.favoriteItems.size shouldBe 1
        sections.recentItems.map { it.session.id } shouldBe listOf(OLDER_SESSION_ID)
    }

    @Test
    fun `both sections Empty give the first-session body`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.body shouldBe FirstSession
    }

    @Test
    fun `both sections Failed give the error body`() = runTest(mainDispatcherRule.testDispatcher) {
        userFavoritesRepository.favoritesReadFailure = IllegalStateException("favorites listener failed")
        recentSessionsRepository.recentSessionsReadFailure = IllegalStateException("recents listener failed")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.body shouldBe LoadError
    }

    @Test
    fun `Failed Favorites next to Empty Recents give the error body, never the first-session one`() =
        runTest(mainDispatcherRule.testDispatcher) {
            userFavoritesRepository.favoritesReadFailure = IllegalStateException("favorites listener failed")

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.assertValue {
                recents shouldBe RecentsEmpty
                body shouldBe LoadError
            }
        }

    @Test
    fun `a Failed section next to one still loading after the ceiling gives the error body`() = runTest(mainDispatcherRule.testDispatcher) {
        userFavoritesRepository.favoritesReadFailure = IllegalStateException("favorites listener failed")
        recentSessionsRepository.recentSessionsReadGate = CompletableDeferred()

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.assertValue {
            recents shouldBe RecentsLoading
            body shouldBe LoadError
        }
    }

    @Test
    fun `Failed Favorites next to Recents Content show the Recents only`() = runTest(mainDispatcherRule.testDispatcher) {
        userFavoritesRepository.favoritesReadFailure = IllegalStateException("favorites listener failed")
        stubCategoryFetch()
        recentSessionsRepository.setRecentSessions(listOf(recentSession(OLDER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID))))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val sections = viewModel.state.value.body.shouldBeInstanceOf<Sections>()
        sections.favoriteItems shouldBe emptyList()
        sections.recentItems.map { it.session.id } shouldBe listOf(OLDER_SESSION_ID)
    }

    @Test
    fun `Favorites Content next to Failed Recents show the Favorites only`() = runTest(mainDispatcherRule.testDispatcher) {
        recentSessionsRepository.recentSessionsReadFailure = IllegalStateException("recents listener failed")
        favorite(COMPOSE_ID)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val sections = viewModel.state.value.body.shouldBeInstanceOf<Sections>()
        sections.favoriteItems.size shouldBe 1
        sections.recentItems shouldBe emptyList()
    }

    @Test
    fun `Favorites and Recents Content show both`() = runTest(mainDispatcherRule.testDispatcher) {
        favorite(COMPOSE_ID)
        recentSessionsRepository.setRecentSessions(listOf(recentSession(OLDER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID))))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val sections = viewModel.state.value.body.shouldBeInstanceOf<Sections>()
        sections.favoriteItems.size shouldBe 1
        sections.recentItems.map { it.session.id } shouldBe listOf(OLDER_SESSION_ID)
    }

    @Test
    fun `Empty Favorites next to Recents still loading after the ceiling give an empty Sections body`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recentSessionsRepository.recentSessionsReadGate = CompletableDeferred()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.assertValue {
                favorites shouldBe Empty
                body shouldBe Sections(favoriteItems = emptyList(), recentItems = emptyList())
            }
        }

    @Test
    fun `both sections still loading after the ceiling give an empty Sections body`() = runTest(mainDispatcherRule.testDispatcher) {
        userFavoritesRepository.favoritesReadGate = CompletableDeferred()
        recentSessionsRepository.recentSessionsReadGate = CompletableDeferred()

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.body shouldBe Sections(favoriteItems = emptyList(), recentItems = emptyList())
    }

    @Test
    fun `Retry relaunches only the Failed sections, setting them back to Loading`() = runTest(mainDispatcherRule.testDispatcher) {
        userFavoritesRepository.favoritesReadFailure = IllegalStateException("favorites listener failed")
        val recentSessions = recentSessionsReturning(flow { emit(emptyList()) })
        val viewModel = createViewModel(observeRecentSessions = recentSessions)
        advanceUntilIdle()
        viewModel.state.value.body shouldBe LoadError

        userFavoritesRepository.favoritesReadGate = CompletableDeferred()
        viewModel.onRetry()

        viewModel.state.assertValue {
            favorites shouldBe Loading
            recents shouldBe RecentsEmpty
        }
        advanceUntilIdle()
        coVerify(exactly = 1) { recentSessions() }
    }

    @Test
    fun `a second Retry tap while the first is loading relaunches nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        val favoriteItems = favoriteItemsReturning(flow { error("favorites listener failed") })
        val viewModel = createViewModel(observeFavoriteItems = favoriteItems)
        advanceUntilIdle()

        viewModel.onRetry()
        viewModel.onRetry()
        advanceUntilIdle()

        coVerify(exactly = 2) { favoriteItems() }
    }

    @Test
    fun `a section recovered by Retry replaces Failed`() = runTest(mainDispatcherRule.testDispatcher) {
        userFavoritesRepository.favoritesReadFailure = IllegalStateException("favorites listener failed")
        favorite(COMPOSE_ID)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.state.value.favorites shouldBe Failed

        userFavoritesRepository.favoritesReadFailure = null
        viewModel.onRetry()
        advanceUntilIdle()

        viewModel.state.value.favorites.subcategoryIds() shouldBe setOf(COMPOSE_ID)
    }

    @Test
    fun `Retry restarts the reveal ceiling`() = runTest(mainDispatcherRule.testDispatcher) {
        userFavoritesRepository.favoritesReadFailure = IllegalStateException("favorites listener failed")
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.state.value.hasRevealCeilingElapsed shouldBe true

        userFavoritesRepository.favoritesReadFailure = null
        userFavoritesRepository.favoritesReadGate = CompletableDeferred()
        viewModel.onRetry()
        runCurrent()
        viewModel.state.value.body shouldBe Resolving

        advanceTimeBy(REVEAL_CEILING)
        runCurrent()
        viewModel.state.value.hasRevealCeilingElapsed shouldBe true
    }

    @Test
    fun `Retry does nothing when no section Failed`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onRetry()

        viewModel.state.value.body shouldBe FirstSession
    }
}
