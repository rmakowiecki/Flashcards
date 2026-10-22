package com.rossomak.flashcards.feature.home

import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.DailyGoal
import com.rossomak.flashcards.core.domain.model.FavoriteItem.FavoriteSubcategory
import com.rossomak.flashcards.core.domain.model.FavoriteItemsResult
import com.rossomak.flashcards.core.domain.model.FavoriteItemsResult.Resolved
import com.rossomak.flashcards.core.domain.model.LevelProgress
import com.rossomak.flashcards.core.domain.model.ObservedScoringState
import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.RecentItem
import com.rossomak.flashcards.core.domain.model.RecentSession
import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.SessionSourceType
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Custom
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Quick
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import com.rossomak.flashcards.core.domain.model.Streak
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressSummary
import com.rossomak.flashcards.core.domain.model.UserPreference.HasHiddenFavoritesHint
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.model.levelThreshold
import com.rossomak.flashcards.core.domain.repository.FakeAuthRepository
import com.rossomak.flashcards.core.domain.repository.FakeCardProgressRepository
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import com.rossomak.flashcards.core.domain.repository.FakeRecentSessionsRepository
import com.rossomak.flashcards.core.domain.repository.FakeScoringStateRepository
import com.rossomak.flashcards.core.domain.repository.FakeTodayProvider
import com.rossomak.flashcards.core.domain.repository.FakeUserFavoritesRepository
import com.rossomak.flashcards.core.domain.repository.FakeUserPreferencesRepository
import com.rossomak.flashcards.core.domain.repository.FakeXpConfigRepository
import com.rossomak.flashcards.core.domain.usecase.ObserveAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveFavoriteItemsUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveProgressSummaryUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveRecentSessionsUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveScoringStateUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveUserPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveUserPreferenceUseCase
import com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCardState.Content as LevelCardContent
import com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCardState.Loading as LevelCardLoading
import com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCardState.Unavailable as LevelCardUnavailable
import com.rossomak.flashcards.feature.home.HomeBody.FirstSession
import com.rossomak.flashcards.feature.home.HomeBody.LoadError
import com.rossomak.flashcards.feature.home.HomeBody.Resolving
import com.rossomak.flashcards.feature.home.HomeBody.Sections
import com.rossomak.flashcards.feature.home.HomeFavoritesArea.Carousel
import com.rossomak.flashcards.feature.home.HomeFavoritesArea.Hint
import com.rossomak.flashcards.feature.home.HomeFavoritesArea.Omitted as FavoritesOmitted
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Content
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Empty
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Failed
import com.rossomak.flashcards.feature.home.HomeFavoritesState.Loading
import com.rossomak.flashcards.feature.home.HomeRecentsArea.Omitted as RecentsOmitted
import com.rossomak.flashcards.feature.home.HomeRecentsArea.Placeholder
import com.rossomak.flashcards.feature.home.HomeRecentsArea.Rows
import com.rossomak.flashcards.feature.home.HomeRecentsState.Content as RecentsContent
import com.rossomak.flashcards.feature.home.HomeRecentsState.Empty as RecentsEmpty
import com.rossomak.flashcards.feature.home.HomeRecentsState.Failed as RecentsFailed
import com.rossomak.flashcards.feature.home.HomeRecentsState.Loading as RecentsLoading
import com.rossomak.flashcards.feature.home.HomeStreakArea.Omitted as StreakOmitted
import com.rossomak.flashcards.feature.home.HomeStreakArea.Row as StreakRow
import com.rossomak.flashcards.feature.home.HomeStreakState.Content as StreakContent
import com.rossomak.flashcards.feature.home.HomeStreakState.Empty as StreakEmpty
import com.rossomak.flashcards.feature.home.HomeStreakState.Failed as StreakFailed
import com.rossomak.flashcards.feature.home.HomeStreakState.Loading as StreakLoading
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
private const val AVATAR_URL = "https://example.com/jane.png"
private const val DISPLAY_NAME = "Jane Doe"
private const val RENAMED_DISPLAY_NAME = "Jane Smith"
private const val STREAK_DAYS = 8
private const val BEST_STREAK_DAYS = 14
private const val LONGER_GOAL_MINUTES = 40
private const val STUDIED_MINUTES = 10
private const val STUDIED_SECONDS = STUDIED_MINUTES * 60L

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val flashcardRepository = FakeFlashcardRepository()
    private val userFavoritesRepository = FakeUserFavoritesRepository()
    private val cardProgressRepository = FakeCardProgressRepository()
    private val recentSessionsRepository = FakeRecentSessionsRepository()
    private val userPreferencesRepository = FakeUserPreferencesRepository()
    private val scoringStateRepository = FakeScoringStateRepository()
    private val authRepository = FakeAuthRepository()

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
        observeAuthUser: ObserveAuthUserUseCase = ObserveAuthUserUseCase(authRepository),
        observeScoringState: ObserveScoringStateUseCase = ObserveScoringStateUseCase(scoringStateRepository, FakeXpConfigRepository(), FakeTodayProvider()),
        observeFavoriteItems: ObserveFavoriteItemsUseCase = ObserveFavoriteItemsUseCase(userFavoritesRepository, flashcardRepository),
        observeRecentSessions: ObserveRecentSessionsUseCase = ObserveRecentSessionsUseCase(recentSessionsRepository, flashcardRepository),
        observeUserPreferences: ObserveUserPreferencesUseCase = ObserveUserPreferencesUseCase(userPreferencesRepository),
    ): HomeViewModel = HomeViewModel(
        observeAuthUser = observeAuthUser,
        observeFavoriteItems = observeFavoriteItems,
        observeScoringState = observeScoringState,
        observeProgressSummary = ObserveProgressSummaryUseCase(cardProgressRepository),
        observeRecentSessions = observeRecentSessions,
        observeUserPreferences = observeUserPreferences,
        saveUserPreference = SaveUserPreferenceUseCase(userPreferencesRepository),
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

    private fun levelProgressReturning(levelProgress: Flow<LevelProgress>): ObserveScoringStateUseCase = mockk {
        coEvery { this@mockk() } returns levelProgress.map { progress ->
            ObservedScoringState(scoringState = progress.toScoringState(), levelProgress = progress, streak = null, todayStudiedSeconds = 0)
        }
    }

    /** The scoring state the default Level curve measures as [this]; its [LevelProgress.xpForNextLevel] must be that curve's. */
    private fun LevelProgress.toScoringState() = ScoringState(level = level, xpIntoCurrentLevel = xpIntoCurrentLevel)

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

    private fun givenOneRecentSession() {
        stubCategoryFetch()
        recentSessionsRepository.setRecentSessions(listOf(recentSession(OLDER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID))))
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

        viewModel.state.value.body.shouldBeInstanceOf<Sections>().favorites.shouldBeInstanceOf<Carousel>().items.size shouldBe 1
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
        viewModel.state.value.body.shouldBeInstanceOf<Sections>().recents shouldBe RecentsOmitted

        recentsGate.complete(Unit)
        advanceUntilIdle()

        val sections = viewModel.state.value.body.shouldBeInstanceOf<Sections>()
        sections.favorites.shouldBeInstanceOf<Carousel>().items.size shouldBe 1
        sections.recents.shouldBeInstanceOf<Rows>().items.map { it.session.id } shouldBe listOf(OLDER_SESSION_ID)
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
        sections.favorites shouldBe FavoritesOmitted
        sections.recents.shouldBeInstanceOf<Rows>().items.map { it.session.id } shouldBe listOf(OLDER_SESSION_ID)
    }

    @Test
    fun `Favorites Content next to Failed Recents show the Favorites only`() = runTest(mainDispatcherRule.testDispatcher) {
        recentSessionsRepository.recentSessionsReadFailure = IllegalStateException("recents listener failed")
        favorite(COMPOSE_ID)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val sections = viewModel.state.value.body.shouldBeInstanceOf<Sections>()
        sections.favorites.shouldBeInstanceOf<Carousel>().items.size shouldBe 1
        sections.recents shouldBe RecentsOmitted
    }

    @Test
    fun `Favorites and Recents Content show both`() = runTest(mainDispatcherRule.testDispatcher) {
        favorite(COMPOSE_ID)
        recentSessionsRepository.setRecentSessions(listOf(recentSession(OLDER_SESSION_ID, SingleSubcategory, listOf(COMPOSE_ID))))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val sections = viewModel.state.value.body.shouldBeInstanceOf<Sections>()
        sections.favorites.shouldBeInstanceOf<Carousel>().items.size shouldBe 1
        sections.recents.shouldBeInstanceOf<Rows>().items.map { it.session.id } shouldBe listOf(OLDER_SESSION_ID)
    }

    @Test
    fun `Empty Favorites next to Recents still loading after the ceiling give an empty Sections body`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recentSessionsRepository.recentSessionsReadGate = CompletableDeferred()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.assertValue {
                favorites shouldBe Empty
                body shouldBe Sections(streak = StreakOmitted, favorites = FavoritesOmitted, recents = RecentsOmitted)
            }
        }

    @Test
    fun `both sections still loading after the ceiling give an empty Sections body`() = runTest(mainDispatcherRule.testDispatcher) {
        userFavoritesRepository.favoritesReadGate = CompletableDeferred()
        recentSessionsRepository.recentSessionsReadGate = CompletableDeferred()

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.body shouldBe Sections(streak = StreakOmitted, favorites = FavoritesOmitted, recents = RecentsOmitted)
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

    @Test
    fun `Empty Favorites next to Recents Content show the hint above the Recents`() = runTest(mainDispatcherRule.testDispatcher) {
        givenOneRecentSession()

        val viewModel = createViewModel()
        advanceUntilIdle()

        val sections = viewModel.state.value.body.shouldBeInstanceOf<Sections>()
        sections.favorites shouldBe Hint
        sections.recents.shouldBeInstanceOf<Rows>().items.map { it.session.id } shouldBe listOf(OLDER_SESSION_ID)
    }

    @Test
    fun `a hidden hint leaves the Recents only`() = runTest(mainDispatcherRule.testDispatcher) {
        userPreferencesRepository.save(HasHiddenFavoritesHint(true))
        givenOneRecentSession()

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.body.shouldBeInstanceOf<Sections>().favorites shouldBe FavoritesOmitted
    }

    @Test
    fun `an unresolved hint preference holds the body Resolving when the hint could show`() = runTest(mainDispatcherRule.testDispatcher) {
        userPreferencesRepository.userPreferencesReadGate = CompletableDeferred()
        givenOneRecentSession()

        val viewModel = createViewModel()
        runCurrent()

        viewModel.state.assertValue {
            favorites shouldBe Empty
            recents.sessionIds() shouldBe listOf(OLDER_SESSION_ID)
            body shouldBe Resolving
        }
    }

    @Test
    fun `an unresolved hint preference after the ceiling omits the hint until a late not-hidden read adds it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val preferencesGate = CompletableDeferred<Unit>()
            userPreferencesRepository.userPreferencesReadGate = preferencesGate
            givenOneRecentSession()
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.state.value.body.shouldBeInstanceOf<Sections>().favorites shouldBe FavoritesOmitted

            preferencesGate.complete(Unit)
            advanceUntilIdle()

            viewModel.state.value.body.shouldBeInstanceOf<Sections>().favorites shouldBe Hint
        }

    @Test
    fun `an unresolved hint preference never delays Favorites Content`() = runTest(mainDispatcherRule.testDispatcher) {
        // Never studied: the row needs no Daily Goal, so the stalled read holds nothing back.
        scoringStateRepository.emit(ScoringState())
        userPreferencesRepository.userPreferencesReadGate = CompletableDeferred()
        favorite(COMPOSE_ID)

        val viewModel = createViewModel()
        runCurrent()

        viewModel.state.value.body.shouldBeInstanceOf<Sections>().favorites.shouldBeInstanceOf<Carousel>()
    }

    @Test
    fun `both sections Empty give the first-session body even with an unresolved hint preference`() =
        runTest(mainDispatcherRule.testDispatcher) {
            scoringStateRepository.emit(ScoringState())
            userPreferencesRepository.userPreferencesReadGate = CompletableDeferred()

            val viewModel = createViewModel()
            runCurrent()

            viewModel.state.value.body shouldBe FirstSession
        }

    @Test
    fun `both sections Empty give the first-session body even with the hint hidden`() = runTest(mainDispatcherRule.testDispatcher) {
        userPreferencesRepository.save(HasHiddenFavoritesHint(true))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.body shouldBe FirstSession
    }

    @Test
    fun `a failed hint preference read counts as not hidden`() = runTest(mainDispatcherRule.testDispatcher) {
        userPreferencesRepository.userPreferencesReadFailure = IllegalStateException("preferences read failed")
        givenOneRecentSession()

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.body.shouldBeInstanceOf<Sections>().favorites shouldBe Hint
    }

    @Test
    fun `a hint preference read that ends without a value counts as not hidden`() = runTest(mainDispatcherRule.testDispatcher) {
        scoringStateRepository.emit(ScoringState())
        userPreferencesRepository.userPreferencesEmissionLimit = 0
        givenOneRecentSession()

        val viewModel = createViewModel()
        runCurrent()

        viewModel.state.value.body.shouldBeInstanceOf<Sections>().favorites shouldBe Hint
    }

    @Test
    fun `an unresolved hint preference never delays Failed Favorites next to Recents Content`() =
        runTest(mainDispatcherRule.testDispatcher) {
            scoringStateRepository.emit(ScoringState())
            userPreferencesRepository.userPreferencesReadGate = CompletableDeferred()
            userFavoritesRepository.favoritesReadFailure = IllegalStateException("favorites listener failed")
            givenOneRecentSession()

            val viewModel = createViewModel()
            runCurrent()

            viewModel.state.value.body.shouldBeInstanceOf<Sections>().favorites shouldBe FavoritesOmitted
        }

    @Test
    fun `an unresolved hint preference never delays the Recents placeholder`() = runTest(mainDispatcherRule.testDispatcher) {
        scoringStateRepository.emit(ScoringState())
        userPreferencesRepository.userPreferencesReadGate = CompletableDeferred()
        favorite(COMPOSE_ID)

        val viewModel = createViewModel()
        runCurrent()

        viewModel.state.value.body.shouldBeInstanceOf<Sections>().recents shouldBe Placeholder
    }

    @Test
    fun `Hide removes the hint at once and persists it`() = runTest(mainDispatcherRule.testDispatcher) {
        givenOneRecentSession()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onFavoritesHintHide()

        viewModel.state.value.body.shouldBeInstanceOf<Sections>().favorites shouldBe FavoritesOmitted
        advanceUntilIdle()
        userPreferencesRepository.preferences.value.hasHiddenFavoritesHint shouldBe true
    }

    @Test
    fun `a failed Hide write keeps the hint hidden for this session`() = runTest(mainDispatcherRule.testDispatcher) {
        userPreferencesRepository.saveError = IllegalStateException("preferences write failed")
        givenOneRecentSession()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onFavoritesHintHide()
        advanceUntilIdle()
        userPreferencesRepository.preferences.value = userPreferencesRepository.preferences.value.copy(dailyGoalMinutes = 45)
        advanceUntilIdle()

        viewModel.state.assertValue {
            hasHiddenFavoritesHint shouldBe true
            body.shouldBeInstanceOf<Sections>().favorites shouldBe FavoritesOmitted
        }
    }

    @Test
    fun `a hidden hint is ignored once a Favorite exists and stays hidden after un-favoriting it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            userPreferencesRepository.save(HasHiddenFavoritesHint(true))
            givenOneRecentSession()
            favorite(COMPOSE_ID)
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.state.value.body.shouldBeInstanceOf<Sections>().favorites.shouldBeInstanceOf<Carousel>()

            unfavorite(COMPOSE_ID)
            advanceUntilIdle()

            viewModel.state.value.body.shouldBeInstanceOf<Sections>().favorites shouldBe FavoritesOmitted
            userPreferencesRepository.preferences.value.hasHiddenFavoritesHint shouldBe true
        }

    @Test
    fun `Favorites Content next to Empty Recents show the Recents placeholder`() = runTest(mainDispatcherRule.testDispatcher) {
        favorite(COMPOSE_ID)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.body.shouldBeInstanceOf<Sections>().recents shouldBe Placeholder
    }

    private val startingLevelProgress = LevelProgress(level = 1, xpIntoCurrentLevel = 0, xpForNextLevel = XpConfig().levelThreshold(1))
    private val levelThreeProgress = LevelProgress(level = 3, xpIntoCurrentLevel = 250, xpForNextLevel = XpConfig().levelThreshold(3))

    private fun levelCardContent(levelProgress: LevelProgress, photoUrl: String?, displayName: String?) = LevelCardContent(
        level = levelProgress.level,
        xpIntoCurrentLevel = levelProgress.xpIntoCurrentLevel,
        xpForNextLevel = levelProgress.xpForNextLevel,
        photoUrl = photoUrl,
        displayName = displayName,
    )

    private fun LevelCardContent.toLevelProgress() = LevelProgress(level, xpIntoCurrentLevel, xpForNextLevel)
    private val janeDoe = AuthUser(uid = "uid", email = null, displayName = DISPLAY_NAME, photoUrl = AVATAR_URL)

    @Test
    fun `the Level card is Loading until the Level stream emits`() = runTest(mainDispatcherRule.testDispatcher) {
        authRepository.userToReturn = janeDoe

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.state.value.levelCard shouldBe LevelCardLoading

        scoringStateRepository.emit(levelThreeProgress.toScoringState())
        advanceUntilIdle()

        viewModel.state.value.levelCard shouldBe levelCardContent(levelThreeProgress, AVATAR_URL, DISPLAY_NAME)
    }

    @Test
    fun `a new account shows its real starting Level, not Loading`() = runTest(mainDispatcherRule.testDispatcher) {
        scoringStateRepository.emit(startingLevelProgress.toScoringState())

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.levelCard.shouldBeInstanceOf<LevelCardContent>().toLevelProgress() shouldBe startingLevelProgress
    }

    @Test
    fun `a null auth user still gives Content, without a name or photo`() = runTest(mainDispatcherRule.testDispatcher) {
        scoringStateRepository.emit(levelThreeProgress.toScoringState())

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.levelCard shouldBe levelCardContent(levelThreeProgress, photoUrl = null, displayName = null)
    }

    @Test
    fun `a failed auth user still gives Content, without a name or photo`() = runTest(mainDispatcherRule.testDispatcher) {
        val authUser = mockk<ObserveAuthUserUseCase>()
        coEvery { authUser() } returns flow { error("auth listener failed") }
        scoringStateRepository.emit(levelThreeProgress.toScoringState())

        val viewModel = createViewModel(observeAuthUser = authUser)
        advanceUntilIdle()

        viewModel.state.value.levelCard shouldBe levelCardContent(levelThreeProgress, photoUrl = null, displayName = null)
    }

    @Test
    fun `an auth update changes the Level card in place`() = runTest(mainDispatcherRule.testDispatcher) {
        authRepository.userToReturn = janeDoe
        scoringStateRepository.emit(levelThreeProgress.toScoringState())
        val viewModel = createViewModel()
        advanceUntilIdle()

        authRepository.userToReturn = janeDoe.copy(displayName = RENAMED_DISPLAY_NAME, photoUrl = null)
        advanceUntilIdle()

        viewModel.state.value.levelCard shouldBe levelCardContent(levelThreeProgress, photoUrl = null, displayName = RENAMED_DISPLAY_NAME)
    }

    @Test
    fun `a new Level emission updates the Level card in place`() = runTest(mainDispatcherRule.testDispatcher) {
        authRepository.userToReturn = janeDoe
        scoringStateRepository.emit(startingLevelProgress.toScoringState())
        val viewModel = createViewModel()
        advanceUntilIdle()

        scoringStateRepository.emit(levelThreeProgress.toScoringState())
        advanceUntilIdle()

        viewModel.state.value.levelCard shouldBe levelCardContent(levelThreeProgress, AVATAR_URL, DISPLAY_NAME)
    }

    @Test
    fun `a Level stream that fails before its first emission makes the card Unavailable`() = runTest(mainDispatcherRule.testDispatcher) {
        scoringStateRepository.scoringStateReadFailure = IllegalStateException("level listener failed")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.levelCard shouldBe LevelCardUnavailable
    }

    @Test
    fun `a Level stream that completes before its first emission makes the card Unavailable`() = runTest(mainDispatcherRule.testDispatcher) {
        scoringStateRepository.completesWithoutEmitting = true

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.levelCard shouldBe LevelCardUnavailable
    }

    @Test
    fun `a Level use case that fails to build its flow makes the card Unavailable`() = runTest(mainDispatcherRule.testDispatcher) {
        val levelProgress = mockk<ObserveScoringStateUseCase>()
        coEvery { levelProgress() } throws IllegalStateException("level use case failed")

        val viewModel = createViewModel(observeScoringState = levelProgress)
        advanceUntilIdle()

        viewModel.state.value.levelCard shouldBe LevelCardUnavailable
        coVerify(exactly = 1) { levelProgress() }
    }

    @Test
    fun `a Level stream failure after the first emission keeps the last card`() = runTest(mainDispatcherRule.testDispatcher) {
        val levelProgress = levelProgressReturning(
            flow {
                emit(levelThreeProgress)
                error("level listener failed")
            },
        )

        val viewModel = createViewModel(observeScoringState = levelProgress)
        advanceUntilIdle()

        viewModel.state.value.levelCard shouldBe levelCardContent(levelThreeProgress, photoUrl = null, displayName = null)
        coVerify(exactly = 1) { levelProgress() }
    }

    @Test
    fun `a Level stream completion after the first emission keeps the last card`() = runTest(mainDispatcherRule.testDispatcher) {
        val levelProgress = levelProgressReturning(flow { emit(levelThreeProgress) })

        val viewModel = createViewModel(observeScoringState = levelProgress)
        advanceUntilIdle()

        viewModel.state.value.levelCard shouldBe levelCardContent(levelThreeProgress, photoUrl = null, displayName = null)
        coVerify(exactly = 1) { levelProgress() }
    }

    @Test
    fun `the Level card stays Loading until the auth user emits, even after the Level stream ended`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val authUserGate = CompletableDeferred<AuthUser?>()
            val authUser = mockk<ObserveAuthUserUseCase>()
            coEvery { authUser() } returns flow { emit(authUserGate.await()) }
            val levelProgress = levelProgressReturning(flow { emit(levelThreeProgress) })

            val viewModel = createViewModel(observeScoringState = levelProgress, observeAuthUser = authUser)
            advanceUntilIdle()
            viewModel.state.value.levelCard shouldBe LevelCardLoading

            authUserGate.complete(janeDoe)
            advanceUntilIdle()

            viewModel.state.value.levelCard shouldBe levelCardContent(levelThreeProgress, AVATAR_URL, DISPLAY_NAME)
            coVerify(exactly = 1) { levelProgress() }
            coVerify(exactly = 1) { authUser() }
        }

    @Test
    fun `a Level stream that ends after its first emission but before the auth user does not make the card Unavailable`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val authUserGate = CompletableDeferred<AuthUser?>()
            val authUser = mockk<ObserveAuthUserUseCase>()
            coEvery { authUser() } returns flow { emit(authUserGate.await()) }
            val levelProgress = levelProgressReturning(
                flow {
                    emit(levelThreeProgress)
                    error("level listener failed")
                },
            )

            val viewModel = createViewModel(observeScoringState = levelProgress, observeAuthUser = authUser)
            advanceUntilIdle()

            viewModel.state.value.levelCard shouldBe LevelCardLoading
            coVerify(exactly = 1) { authUser() }
        }

    @Test
    fun `the Level card never holds the sections back`() = runTest(mainDispatcherRule.testDispatcher) {
        favorite(COMPOSE_ID)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.assertValue {
            levelCard shouldBe LevelCardLoading
            body.shouldBeInstanceOf<Sections>().favorites.shouldBeInstanceOf<Carousel>()
        }
    }

    @Test
    fun `the sections never hold the Level card back`() = runTest(mainDispatcherRule.testDispatcher) {
        userFavoritesRepository.favoritesReadGate = CompletableDeferred()
        scoringStateRepository.emit(levelThreeProgress.toScoringState())

        val viewModel = createViewModel()
        advanceUntilIdle()
        advanceTimeBy(REVEAL_CEILING)

        viewModel.state.assertValue {
            levelCard.shouldBeInstanceOf<LevelCardContent>()
            favorites shouldBe Loading
        }
    }

    @Test
    fun `the body is Resolving while the Level card is already Content`() = runTest(mainDispatcherRule.testDispatcher) {
        userFavoritesRepository.favoritesReadGate = CompletableDeferred()
        scoringStateRepository.emit(levelThreeProgress.toScoringState())

        val viewModel = createViewModel()
        runCurrent()

        viewModel.state.assertValue {
            levelCard.shouldBeInstanceOf<LevelCardContent>()
            body shouldBe Resolving
        }
    }

    @Test
    fun `Retry relaunches the Level collector when the card is Unavailable`() = runTest(mainDispatcherRule.testDispatcher) {
        scoringStateRepository.scoringStateReadFailure = IllegalStateException("level listener failed")
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.state.value.levelCard shouldBe LevelCardUnavailable

        scoringStateRepository.scoringStateReadFailure = null
        scoringStateRepository.emit(levelThreeProgress.toScoringState())
        viewModel.onRetry()
        viewModel.state.value.levelCard shouldBe LevelCardLoading
        advanceUntilIdle()

        viewModel.state.value.levelCard.shouldBeInstanceOf<LevelCardContent>().toLevelProgress() shouldBe levelThreeProgress
    }

    @Test
    fun `Retry relaunches the Level collector while the card is still Loading`() = runTest(mainDispatcherRule.testDispatcher) {
        val levelProgress = levelProgressReturning(flow { awaitCancellation() })
        val viewModel = createViewModel(observeScoringState = levelProgress)
        advanceUntilIdle()

        viewModel.onRetry()
        advanceUntilIdle()

        coVerify(exactly = 2) { levelProgress() }
        viewModel.state.value.levelCard shouldBe LevelCardLoading
    }

    @Test
    fun `Retry leaves a Level card with content alone`() = runTest(mainDispatcherRule.testDispatcher) {
        val levelProgress = levelProgressReturning(flow { emit(levelThreeProgress) })
        val viewModel = createViewModel(observeScoringState = levelProgress)
        advanceUntilIdle()

        viewModel.onRetry()
        advanceUntilIdle()

        coVerify(exactly = 1) { levelProgress() }
        viewModel.state.value.levelCard.shouldBeInstanceOf<LevelCardContent>()
    }

    @Test
    fun `Retry on a failed section leaves the Level card with content alone and keeps the ceiling rules`() =
        runTest(mainDispatcherRule.testDispatcher) {
            userFavoritesRepository.favoritesReadFailure = IllegalStateException("favorites listener failed")
            scoringStateRepository.emit(levelThreeProgress.toScoringState())
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onRetry()

            viewModel.state.assertValue {
                levelCard.shouldBeInstanceOf<LevelCardContent>()
                favorites shouldBe Loading
            }
        }

    private val today = FakeTodayProvider().today

    private fun studiedToday(seconds: Long) = ScoringState(
        currentStreak = STREAK_DAYS,
        bestStreak = BEST_STREAK_DAYS,
        lastStudyDate = today.toString(),
        studiedSecondsOnLastStudyDate = seconds,
    )

    private fun HomeStreakState.content(): StreakContent = shouldBeInstanceOf<StreakContent>()

    private fun HomeBody.streakArea(): HomeStreakArea = shouldBeInstanceOf<Sections>().streak

    @Test
    fun `today's seconds round up to whole minutes once`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        val minutesBySeconds = listOf(0L to 0, 1L to 1, 59L to 1, 60L to 1, 61L to 2)

        minutesBySeconds.forEach { (seconds, expectedMinutes) ->
            scoringStateRepository.emit(studiedToday(seconds))
            advanceUntilIdle()

            viewModel.state.value.streak.content().studiedMinutes shouldBe expectedMinutes
        }
    }

    @Test
    fun `the row carries the Streak, Best Streak, at-risk flag and today's progress against the Daily Goal`() =
        runTest(mainDispatcherRule.testDispatcher) {
            scoringStateRepository.emit(studiedToday(seconds = STUDIED_SECONDS))

            val viewModel = createViewModel()
            advanceUntilIdle()

            val streak = viewModel.state.value.streak.content()
            streak shouldBe StreakContent(
                days = STREAK_DAYS,
                bestDays = BEST_STREAK_DAYS,
                isAtRisk = false,
                studiedMinutes = STUDIED_MINUTES,
                goalMinutes = DailyGoal.DEFAULT_MINUTES,
            )
            streak.progress shouldBe STUDIED_MINUTES.toFloat() / DailyGoal.DEFAULT_MINUTES
        }

    @Test
    fun `a Streak last extended yesterday is at risk with no minutes today`() = runTest(mainDispatcherRule.testDispatcher) {
        scoringStateRepository.emit(studiedToday(seconds = STUDIED_SECONDS).copy(lastStudyDate = today.minusDays(1).toString()))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.streak.content().run {
            isAtRisk shouldBe true
            studiedMinutes shouldBe 0
        }
    }

    @Test
    fun `progress past the Daily Goal is capped at 1 while the minutes stay real`() = runTest(mainDispatcherRule.testDispatcher) {
        val overGoalMinutes = DailyGoal.DEFAULT_MINUTES + STUDIED_MINUTES
        scoringStateRepository.emit(studiedToday(seconds = overGoalMinutes * 60L))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.streak.content().run {
            studiedMinutes shouldBe overGoalMinutes
            progress shouldBe 1f
        }
    }

    @Test
    fun `a Daily Goal that is not positive gives no progress`() = runTest(mainDispatcherRule.testDispatcher) {
        userPreferencesRepository.preferences.value = userPreferencesRepository.preferences.value.copy(dailyGoalMinutes = 0)
        scoringStateRepository.emit(studiedToday(seconds = STUDIED_SECONDS))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.streak.content().progress shouldBe 0f
    }

    @Test
    fun `a Daily Goal change updates the row in place`() = runTest(mainDispatcherRule.testDispatcher) {
        scoringStateRepository.emit(studiedToday(seconds = STUDIED_SECONDS))
        val viewModel = createViewModel()
        advanceUntilIdle()

        userPreferencesRepository.preferences.value = userPreferencesRepository.preferences.value.copy(dailyGoalMinutes = LONGER_GOAL_MINUTES)
        advanceUntilIdle()

        viewModel.state.value.streak.content().run {
            goalMinutes shouldBe LONGER_GOAL_MINUTES
            progress shouldBe STUDIED_MINUTES.toFloat() / LONGER_GOAL_MINUTES
        }
    }

    @Test
    fun `a failed Daily Goal read uses the default goal`() = runTest(mainDispatcherRule.testDispatcher) {
        userPreferencesRepository.userPreferencesReadFailure = IllegalStateException("preferences read failed")
        scoringStateRepository.emit(studiedToday(seconds = STUDIED_SECONDS))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.streak.content().goalMinutes shouldBe DailyGoal.DEFAULT_MINUTES
    }

    @Test
    fun `a Daily Goal read that fails after a value keeps that goal`() = runTest(mainDispatcherRule.testDispatcher) {
        val longerGoalPreferences = userPreferencesRepository.preferences.value.copy(dailyGoalMinutes = LONGER_GOAL_MINUTES)
        val failingAfterOneValue = mockk<ObserveUserPreferencesUseCase> {
            coEvery { this@mockk() } returns flow {
                emit(longerGoalPreferences)
                error("preferences listener failed")
            }
        }
        scoringStateRepository.emit(studiedToday(seconds = STUDIED_SECONDS))

        val viewModel = createViewModel(observeUserPreferences = failingAfterOneValue)
        advanceUntilIdle()

        viewModel.state.value.streak.content().goalMinutes shouldBe LONGER_GOAL_MINUTES
    }

    @Test
    fun `a stalled Daily Goal read holds back the row of a User who studied`() = runTest(mainDispatcherRule.testDispatcher) {
        userPreferencesRepository.userPreferencesReadGate = CompletableDeferred()
        scoringStateRepository.emit(studiedToday(seconds = STUDIED_SECONDS))

        val viewModel = createViewModel()
        runCurrent()

        viewModel.state.assertValue {
            levelCard.shouldBeInstanceOf<LevelCardContent>()
            streak shouldBe StreakLoading
        }
    }

    @Test
    fun `a Daily Goal read that ends without a value uses the default goal`() = runTest(mainDispatcherRule.testDispatcher) {
        userPreferencesRepository.userPreferencesEmissionLimit = 0
        scoringStateRepository.emit(studiedToday(seconds = STUDIED_SECONDS))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.streak.content().goalMinutes shouldBe DailyGoal.DEFAULT_MINUTES
    }

    @Test
    fun `the Level card and the row share one scoring state stream and update together`() = runTest(mainDispatcherRule.testDispatcher) {
        val domainStreak = Streak(days = STREAK_DAYS, best = BEST_STREAK_DAYS, isAtRisk = false)
        val observedScoringStates = MutableStateFlow(
            ObservedScoringState(studiedToday(seconds = 0), startingLevelProgress, domainStreak, todayStudiedSeconds = 0),
        )
        val scoringState = mockk<ObserveScoringStateUseCase> { coEvery { this@mockk() } returns observedScoringStates }
        val viewModel = createViewModel(observeScoringState = scoringState)
        advanceUntilIdle()

        observedScoringStates.value = ObservedScoringState(studiedToday(STUDIED_SECONDS), levelThreeProgress, domainStreak, todayStudiedSeconds = STUDIED_SECONDS)
        advanceUntilIdle()

        viewModel.state.assertValue {
            levelCard.shouldBeInstanceOf<LevelCardContent>().toLevelProgress() shouldBe levelThreeProgress
            streak.content().studiedMinutes shouldBe STUDIED_MINUTES
        }
        coVerify(exactly = 1) { scoringState() }
    }

    @Test
    fun `a User who never studied gets an Empty row that is not shown`() = runTest(mainDispatcherRule.testDispatcher) {
        favorite(COMPOSE_ID)
        scoringStateRepository.emit(ScoringState())

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.assertValue {
            streak shouldBe StreakEmpty
            body.streakArea() shouldBe StreakOmitted
        }
    }

    @Test
    fun `the body stays Resolving while the row loads, until the ceiling elapses`() = runTest(mainDispatcherRule.testDispatcher) {
        favorite(COMPOSE_ID)
        givenOneRecentSession()

        val viewModel = createViewModel()
        runCurrent()
        viewModel.state.assertValue {
            favorites.shouldBeInstanceOf<Content>()
            recents.shouldBeInstanceOf<RecentsContent>()
            body shouldBe Resolving
        }

        scoringStateRepository.emit(studiedToday(seconds = STUDIED_SECONDS))
        runCurrent()

        viewModel.state.value.body.streakArea().shouldBeInstanceOf<StreakRow>().content.studiedMinutes shouldBe STUDIED_MINUTES
    }

    @Test
    fun `a row that arrives after the ceiling joins the Sections in place`() = runTest(mainDispatcherRule.testDispatcher) {
        favorite(COMPOSE_ID)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.state.assertValue {
            streak shouldBe StreakLoading
            body.streakArea() shouldBe StreakOmitted
        }

        scoringStateRepository.emit(studiedToday(seconds = STUDIED_SECONDS))
        advanceUntilIdle()

        viewModel.state.value.body.streakArea().shouldBeInstanceOf<StreakRow>()
    }

    @Test
    fun `a failing scoring state stream fails the row and the Level card together`() = runTest(mainDispatcherRule.testDispatcher) {
        scoringStateRepository.scoringStateReadFailure = IllegalStateException("scoring state listener failed")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.assertValue {
            streak shouldBe StreakFailed
            levelCard shouldBe LevelCardUnavailable
        }
    }

    @Test
    fun `a scoring state stream that completes before its first emission fails the row`() = runTest(mainDispatcherRule.testDispatcher) {
        scoringStateRepository.completesWithoutEmitting = true

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.streak shouldBe StreakFailed
    }

    @Test
    fun `a scoring state failure after the first emission keeps the row`() = runTest(mainDispatcherRule.testDispatcher) {
        val scoringState = mockk<ObserveScoringStateUseCase>()
        coEvery { scoringState() } returns flow {
            val streak = Streak(days = STREAK_DAYS, best = BEST_STREAK_DAYS, isAtRisk = false)
            emit(ObservedScoringState(studiedToday(STUDIED_SECONDS), startingLevelProgress, streak, todayStudiedSeconds = STUDIED_SECONDS))
            error("scoring state listener failed")
        }

        val viewModel = createViewModel(observeScoringState = scoringState)
        advanceUntilIdle()

        viewModel.state.value.streak.content().days shouldBe STREAK_DAYS
    }

    @Test
    fun `a Failed row next to content is not shown`() = runTest(mainDispatcherRule.testDispatcher) {
        scoringStateRepository.scoringStateReadFailure = IllegalStateException("scoring state listener failed")
        favorite(COMPOSE_ID)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val sections = viewModel.state.value.body.shouldBeInstanceOf<Sections>()
        sections.streak shouldBe StreakOmitted
        sections.favorites.shouldBeInstanceOf<Carousel>()
    }

    @Test
    fun `a Failed row next to Empty Favorites and Recents still loading after the ceiling gives the error body`() =
        runTest(mainDispatcherRule.testDispatcher) {
            scoringStateRepository.scoringStateReadFailure = IllegalStateException("scoring state listener failed")
            recentSessionsRepository.recentSessionsReadGate = CompletableDeferred()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.assertValue {
                favorites shouldBe Empty
                body shouldBe LoadError
            }
        }

    @Test
    fun `a row with content next to two Failed sections shows the row only`() = runTest(mainDispatcherRule.testDispatcher) {
        userFavoritesRepository.favoritesReadFailure = IllegalStateException("favorites listener failed")
        recentSessionsRepository.recentSessionsReadFailure = IllegalStateException("recents listener failed")
        scoringStateRepository.emit(studiedToday(seconds = STUDIED_SECONDS))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val sections = viewModel.state.value.body.shouldBeInstanceOf<Sections>()
        sections.streak.shouldBeInstanceOf<StreakRow>()
        sections.favorites shouldBe FavoritesOmitted
        sections.recents shouldBe RecentsOmitted
    }

    @Test
    fun `both sections Empty give the first-session body even when the row has content`() = runTest(mainDispatcherRule.testDispatcher) {
        scoringStateRepository.emit(studiedToday(seconds = STUDIED_SECONDS))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.assertValue {
            streak.shouldBeInstanceOf<StreakContent>()
            body shouldBe FirstSession
        }
    }

    @Test
    fun `Empty Favorites and Recents give the first-session body without waiting for the row`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        runCurrent()

        viewModel.state.assertValue {
            streak shouldBe StreakLoading
            hasRevealCeilingElapsed shouldBe false
            body shouldBe FirstSession
        }
    }

    @Test
    fun `Retry on a Failed row sets it back to Loading and restarts the scoring state stream`() = runTest(mainDispatcherRule.testDispatcher) {
        scoringStateRepository.scoringStateReadFailure = IllegalStateException("scoring state listener failed")
        userFavoritesRepository.favoritesReadFailure = IllegalStateException("favorites listener failed")
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.state.value.body shouldBe LoadError

        scoringStateRepository.scoringStateReadFailure = null
        scoringStateRepository.emit(studiedToday(seconds = STUDIED_SECONDS))
        viewModel.onRetry()
        viewModel.state.assertValue {
            streak shouldBe StreakLoading
            hasRevealCeilingElapsed shouldBe false
        }
        advanceUntilIdle()

        viewModel.state.assertValue {
            streak.content().studiedMinutes shouldBe STUDIED_MINUTES
            levelCard.shouldBeInstanceOf<LevelCardContent>()
        }
    }
}
