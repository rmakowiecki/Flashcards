package com.rossomak.flashcards.feature.home

import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.FavoriteItem.FavoriteSubcategory
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
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Content
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Hidden
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Loading
import com.rossomak.flashcards.feature.home.HomeRecentsState.Content as RecentsContent
import com.rossomak.flashcards.feature.home.HomeRecentsState.Hidden as RecentsHidden
import com.rossomak.flashcards.feature.home.HomeRecentsState.Loading as RecentsLoading
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
private const val COROUTINES_ID = "coroutines"
private const val DELETED_ID = "deleted"
private const val OLDER_SESSION_ID = "older-session"
private const val NEWER_SESSION_ID = "newer-session"
private const val NEWEST_SESSION_ID = "newest-session"
private const val CUSTOM_PARTIAL_SESSION_ID = "custom-partial-session"
private const val CUSTOM_EMPTY_SESSION_ID = "custom-empty-session"
private const val RENAMED_CATEGORY_NAME = "Android (renamed)"

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
        subcategoryIds = subcategoryIds,
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
        subcategoryIds = listOf(COMPOSE_ID),
        studiedCount = 10,
        xpTotal = 40,
        readAloudEnabled = readAloudEnabled,
    )

    private fun stubTaxonomyFetches(vararg subcategoryIds: String) {
        flashcardRepository.subcategoriesByIdsToReturn = Result.success(subcategoryIds.map { subcategory(it) })
        flashcardRepository.categoriesByIdsToReturn = Result.success(listOf(parentCategory))
    }

    private fun HomeRecentsState.items(): List<RecentItem> = shouldBeInstanceOf<RecentsContent>().items

    private fun HomeRecentsState.sessionIds(): List<String> = items().map { it.session.id }

    /** Every distinct Favorites state in order, so a transient state between two others shows up as its own item. */
    private fun HomeViewModel.favoritesStates(): Flow<HomeFavoritesState> = state.map { it.favorites }.distinctUntilChanged()

    @Test
    fun `favorites start Loading and become Content without passing through Hidden`() = runTest(mainDispatcherRule.testDispatcher) {
        favorite(COMPOSE_ID)
        val viewModel = createViewModel()

        viewModel.favoritesStates().test {
            awaitItem() shouldBe Loading
            advanceUntilIdle()

            awaitItem().subcategoryIds() shouldBe setOf(COMPOSE_ID)
        }
    }

    @Test
    fun `favorites become Hidden when the User has none`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.favorites shouldBe Hidden
    }

    @Test
    fun `favorites move from Hidden to Content when the first Favorite is added`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.state.value.favorites shouldBe Hidden

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
    fun `favorites move from Content to Hidden when the last Favorite is removed`() = runTest(mainDispatcherRule.testDispatcher) {
        favorite(COMPOSE_ID)
        val viewModel = createViewModel()
        advanceUntilIdle()

        unfavorite(COMPOSE_ID)
        advanceUntilIdle()

        viewModel.state.value.favorites shouldBe Hidden
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
    fun `a failing favorites flow turns Loading into Hidden`() = runTest(mainDispatcherRule.testDispatcher) {
        userFavoritesRepository.favoritesReadFailure = IllegalStateException("favorites listener failed")

        val viewModel = createViewModel()
        viewModel.state.value.favorites shouldBe Loading
        advanceUntilIdle()

        viewModel.state.value.favorites shouldBe Hidden
    }

    @Test
    fun `a favorites failure after the first emission leaves the Content alone`() = runTest(mainDispatcherRule.testDispatcher) {
        val favoriteItems = mockk<ObserveFavoriteItemsUseCase>()
        coEvery { favoriteItems() } returns flow {
            emit(listOf(FavoriteSubcategory(subcategory(COMPOSE_ID), parentCategory, favoritedAt = Instant.EPOCH)))
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
    fun `selecting a single-subcategory Rated Recent replays its Subcategory, mode and Voice Answering under the live Category name`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val renamedCategory = parentCategory.copy(name = RENAMED_CATEGORY_NAME)
            val compose = subcategory(COMPOSE_ID)
            val session = recentSession(OLDER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID), voiceAnsweringEnabled = true)
            val viewModel = createViewModel()

            viewModel.events.test {
                viewModel.onRecentSelect(RecentItem(session, renamedCategory, listOf(compose)))
                advanceUntilIdle()

                awaitItem() shouldBe HomeDestination.RecentPreviewStudySession(
                    categoryId = parentCategory.id,
                    categoryName = RENAMED_CATEGORY_NAME,
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
            viewModel.onRecentSelect(RecentItem(fastRecentSession(Quick, readAloudEnabled = true), parentCategory, emptyList()))
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
    fun `selecting a Custom Recent replays exactly the Subcategories that resolved, in stored order`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val session = recentSession(CUSTOM_PARTIAL_SESSION_ID, Custom, listOf(NAVIGATION_ID, DELETED_ID, COMPOSE_ID))
            val resolved = listOf(subcategory(NAVIGATION_ID), subcategory(COMPOSE_ID))
            val viewModel = createViewModel()

            viewModel.events.test {
                viewModel.onRecentSelect(RecentItem(session, parentCategory, resolved))
                advanceUntilIdle()

                val destination = awaitItem().shouldBeInstanceOf<HomeDestination.RecentPreviewStudySession>()
                destination.sourceType shouldBe Custom
                destination.subcategoryIds shouldBe listOf(NAVIGATION_ID, COMPOSE_ID)
                destination.subcategoryNames shouldBe resolved.map { it.name }
            }
        }

    @Test
    fun `selecting a Custom Recent with nothing resolved still opens Preview with no Subcategories`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val session = recentSession(CUSTOM_EMPTY_SESSION_ID, Custom, listOf(DELETED_ID))
            val viewModel = createViewModel()

            viewModel.events.test {
                viewModel.onRecentSelect(RecentItem(session, parentCategory, emptyList()))
                advanceUntilIdle()

                val destination = awaitItem().shouldBeInstanceOf<HomeDestination.RecentPreviewStudySession>()
                destination.sourceType shouldBe Custom
                destination.subcategoryIds shouldBe emptyList()
                destination.subcategoryNames shouldBe emptyList()
            }
        }

    @Test
    fun `recents become Hidden when the User has no sessions`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.recents shouldBe RecentsHidden
    }

    @Test
    fun `recents start Loading and become Content in the repository's newest-first order`() = runTest(mainDispatcherRule.testDispatcher) {
        stubTaxonomyFetches(COMPOSE_ID, NAVIGATION_ID)
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
    fun `a single-subcategory Recent carries its Category and its one Subcategory`() = runTest(mainDispatcherRule.testDispatcher) {
        stubTaxonomyFetches(COMPOSE_ID)
        val session = recentSession(OLDER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID))
        recentSessionsRepository.setRecentSessions(listOf(session))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.recents.items() shouldBe listOf(RecentItem(session, parentCategory, listOf(subcategory(COMPOSE_ID))))
    }

    @Test
    fun `a Recent whose Category does not resolve is dropped`() = runTest(mainDispatcherRule.testDispatcher) {
        stubTaxonomyFetches(COMPOSE_ID)
        recentSessionsRepository.setRecentSessions(
            listOf(
                recentSession(NEWER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID), categoryId = DELETED_ID),
                recentSession(OLDER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID)),
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.recents.sessionIds() shouldBe listOf(OLDER_SESSION_ID)
    }

    @Test
    fun `a single-subcategory Recent whose Subcategory does not resolve is dropped`() = runTest(mainDispatcherRule.testDispatcher) {
        stubTaxonomyFetches(COMPOSE_ID)
        recentSessionsRepository.setRecentSessions(
            listOf(
                recentSession(NEWER_SESSION_ID, SingleSubcategory, listOf(DELETED_ID)),
                recentSession(OLDER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID)),
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.recents.sessionIds() shouldBe listOf(OLDER_SESSION_ID)
    }

    @Test
    fun `a Custom Recent keeps only the Subcategories that resolve, in stored order, even when none do`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubTaxonomyFetches(COMPOSE_ID, NAVIGATION_ID)
            recentSessionsRepository.setRecentSessions(
                listOf(
                    recentSession(CUSTOM_PARTIAL_SESSION_ID, Custom, listOf(NAVIGATION_ID, DELETED_ID, COMPOSE_ID)),
                    recentSession(CUSTOM_EMPTY_SESSION_ID, Custom, listOf(DELETED_ID, COROUTINES_ID)),
                )
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.value.recents.items().associate { item -> item.session.id to item.subcategories.map { it.id } } shouldBe mapOf(
                CUSTOM_PARTIAL_SESSION_ID to listOf(NAVIGATION_ID, COMPOSE_ID),
                CUSTOM_EMPTY_SESSION_ID to emptyList(),
            )
        }

    @Test
    fun `a Quick Recent is kept with no Subcategories`() = runTest(mainDispatcherRule.testDispatcher) {
        stubTaxonomyFetches(COMPOSE_ID)
        recentSessionsRepository.setRecentSessions(listOf(recentSession(OLDER_SESSION_ID, Quick, listOf(COMPOSE_ID))))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.recents.items().single().subcategories shouldBe emptyList()
    }

    @Test
    fun `Recents whose taxonomy fetches keep failing are dropped instead of failing the section`() =
        runTest(mainDispatcherRule.testDispatcher) {
            flashcardRepository.categoriesByIdsToReturn = Result.failure(IllegalStateException("categories fetch failed"))
            recentSessionsRepository.setRecentSessions(listOf(recentSession(OLDER_SESSION_ID, Quick, listOf(COMPOSE_ID))))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.value.recents shouldBe RecentsHidden
        }

    @Test
    fun `a session that appears later joins the top of the Content`() = runTest(mainDispatcherRule.testDispatcher) {
        stubTaxonomyFetches(COMPOSE_ID)
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
        stubTaxonomyFetches(COMPOSE_ID)
        val olderSession = recentSession(OLDER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID))
        recentSessionsRepository.setRecentSessions(listOf(recentSession(NEWER_SESSION_ID, Quick, listOf(COMPOSE_ID)), olderSession))
        val viewModel = createViewModel()
        advanceUntilIdle()

        recentSessionsRepository.setRecentSessions(listOf(olderSession))
        advanceUntilIdle()

        viewModel.state.value.recents.sessionIds() shouldBe listOf(OLDER_SESSION_ID)
    }

    @Test
    fun `recents move from Content to Hidden when the last session goes`() = runTest(mainDispatcherRule.testDispatcher) {
        stubTaxonomyFetches(COMPOSE_ID)
        recentSessionsRepository.setRecentSessions(listOf(recentSession(OLDER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID))))
        val viewModel = createViewModel()
        advanceUntilIdle()

        recentSessionsRepository.setRecentSessions(emptyList())
        advanceUntilIdle()

        viewModel.state.value.recents shouldBe RecentsHidden
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
        stubTaxonomyFetches(COMPOSE_ID)
        recentSessionsRepository.setRecentSessions(listOf(recentSession(OLDER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID))))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.favorites shouldBe Loading
        viewModel.state.value.recents.sessionIds() shouldBe listOf(OLDER_SESSION_ID)
    }

    @Test
    fun `a failing Recents flow turns Loading into Hidden and leaves the Favorites alone`() = runTest(mainDispatcherRule.testDispatcher) {
        recentSessionsRepository.recentSessionsReadFailure = IllegalStateException("recents listener failed")
        favorite(COMPOSE_ID)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.recents shouldBe RecentsHidden
        viewModel.state.value.favorites.subcategoryIds() shouldBe setOf(COMPOSE_ID)
    }

    @Test
    fun `a Recents failure after the first emission leaves the Content alone`() = runTest(mainDispatcherRule.testDispatcher) {
        val recentItem = RecentItem(recentSession(OLDER_SESSION_ID, Quick, listOf(COMPOSE_ID)), parentCategory, emptyList())
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
        stubTaxonomyFetches(COMPOSE_ID)
        recentSessionsRepository.setRecentSessions(listOf(recentSession(OLDER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID))))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.favorites shouldBe Hidden
        viewModel.state.value.recents.sessionIds() shouldBe listOf(OLDER_SESSION_ID)
    }
}
