package com.rossomak.flashcards.presentation.splash

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import com.rossomak.flashcards.core.domain.repository.FakeUserPreferencesRepository
import com.rossomak.flashcards.core.domain.usecase.GetCurrentAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveUserPreferencesUseCase
import com.rossomak.flashcards.core.ui.navigation.RouteDecoder
import com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsRoute
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsRoute
import com.rossomak.flashcards.testutil.MainDispatcherRule
import com.rossomak.flashcards.ui.navigation.Splash
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val savedStateHandle: SavedStateHandle = mockk()
    private val getCurrentAuthUserUseCase: GetCurrentAuthUserUseCase = mockk()
    private val userPreferencesRepository = FakeUserPreferencesRepository()
    private val flashcardRepository = FakeFlashcardRepository()

    private val testUser = AuthUser("u1", "a@b.com", "Alex", null)

    @Before
    fun setUp() {
        mockkObject(RouteDecoder)
    }

    @After
    fun tearDown() {
        unmockkObject(RouteDecoder)
    }

    private fun createViewModel(hasSeenOnboarding: Boolean = true, pendingRoute: String? = null): SplashViewModel {
        userPreferencesRepository.preferences.value =
            userPreferencesRepository.preferences.value.copy(hasSeenOnboarding = hasSeenOnboarding)
        every { RouteDecoder.decode(any<() -> Splash>()) } returns Splash(pendingRoute = pendingRoute)
        return SplashViewModel(
            savedStateHandle,
            getCurrentAuthUserUseCase,
            ObserveUserPreferencesUseCase(userPreferencesRepository),
            flashcardRepository,
        )
    }

    @Test
    fun `authenticated user who has not seen onboarding emits Onboarding`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { getCurrentAuthUserUseCase() } returns testUser

            val viewModel = createViewModel(hasSeenOnboarding = false)
            viewModel.onAnimationCompleted()

            viewModel.events.test {
                awaitItem() shouldBe SplashDestination.Onboarding
            }
        }

    @Test
    fun `unauthenticated user who has not seen onboarding emits Onboarding before Login`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { getCurrentAuthUserUseCase() } returns null

            val viewModel = createViewModel(hasSeenOnboarding = false)
            viewModel.onAnimationCompleted()

            viewModel.events.test {
                awaitItem() shouldBe SplashDestination.Onboarding
            }
        }

    @Test
    fun `anonymous user who has seen onboarding still emits Login`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { getCurrentAuthUserUseCase() } returns testUser.copy(isAnonymous = true)

            val viewModel = createViewModel(hasSeenOnboarding = true)
            viewModel.onAnimationCompleted()

            viewModel.events.test {
                awaitItem() shouldBe SplashDestination.Login
            }
        }

    @Test
    fun `animation completed before timeout with user emits Main`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { getCurrentAuthUserUseCase() } returns testUser

        val viewModel = createViewModel()
        viewModel.onAnimationCompleted()

        viewModel.events.test {
            awaitItem() shouldBe SplashDestination.Main
        }
    }

    @Test
    fun `animation completed before timeout with null user emits Login`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { getCurrentAuthUserUseCase() } returns null

        val viewModel = createViewModel()
        viewModel.onAnimationCompleted()

        viewModel.events.test {
            awaitItem() shouldBe SplashDestination.Login
        }
    }

    @Test
    fun `auth timeout alone without animation completing emits no event`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { getCurrentAuthUserUseCase() } returns null

        val viewModel = createViewModel()
        // Do NOT call onAnimationCompleted - simulate animation hang.
        val scheduler = testScheduler

        viewModel.events.test {
            scheduler.advanceUntilIdle()
            // Auth resolves to false after 1000ms timeout, but animation hasn't completed,
            // so combine never produces a destination and nothing is emitted.
            expectNoEvents()
        }
        testScheduler.currentTime shouldBeLessThan 7_000L
    }

    @Test
    fun `navigation event resolves without extra delay when animation and auth both complete`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { getCurrentAuthUserUseCase() } returns testUser

        val viewModel = createViewModel()
        viewModel.onAnimationCompleted()

        viewModel.events.test {
            awaitItem() shouldBe SplashDestination.Main
        }
        // No post-animation delay in current implementation - destination emitted immediately.
        testScheduler.currentTime shouldBeLessThan 2_000L
    }

    @Test
    fun `onAnimationCompleted called multiple times still emits expected destination once`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { getCurrentAuthUserUseCase() } returns testUser

        val viewModel = createViewModel()
        viewModel.onAnimationCompleted()
        viewModel.onAnimationCompleted()

        viewModel.events.test {
            awaitItem() shouldBe SplashDestination.Main
            expectNoEvents()
        }
    }

    @Test
    fun `onAnimationCompleted called after timeout emits Login`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { getCurrentAuthUserUseCase() } returns null

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAnimationCompleted()

        viewModel.events.test {
            awaitItem() shouldBe SplashDestination.Login
        }
    }

    @Test
    fun `authenticated user with a category shortcut route emits ToCategoryDetails`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { getCurrentAuthUserUseCase() } returns testUser
            flashcardRepository.categoriesByIdsToReturn =
                Result.success(listOf(category(id = "android", name = "Android")))

            val viewModel = createViewModel(pendingRoute = "/study/category/android")
            viewModel.onAnimationCompleted()

            viewModel.events.test {
                awaitItem() shouldBe SplashDestination.ToCategoryDetails(
                    CategoryDetailsRoute(categoryId = "android", categoryName = "Android"),
                )
            }
        }

    @Test
    fun `authenticated user with a subcategory shortcut route emits ToSubcategoryDetails`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { getCurrentAuthUserUseCase() } returns testUser
            flashcardRepository.subcategoriesByIdsToReturn = Result.success(
                listOf(subcategory(id = "kotlin-coroutines", categoryId = "android", categoryName = "Android", name = "Coroutines")),
            )

            val viewModel = createViewModel(pendingRoute = "/study/category/android/subcategory/kotlin-coroutines")
            viewModel.onAnimationCompleted()

            viewModel.events.test {
                awaitItem() shouldBe SplashDestination.ToSubcategoryDetails(
                    SubcategoryDetailsRoute(
                        categoryId = "android",
                        categoryName = "Android",
                        subcategoryId = "kotlin-coroutines",
                        subcategoryName = "Coroutines",
                    ),
                )
            }
        }

    @Test
    fun `authenticated user with a stale shortcut route falls back to Main silently`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { getCurrentAuthUserUseCase() } returns testUser
            flashcardRepository.categoriesByIdsToReturn = Result.success(emptyList())

            val viewModel = createViewModel(pendingRoute = "/study/category/deleted-category")
            viewModel.onAnimationCompleted()

            viewModel.events.test {
                awaitItem() shouldBe SplashDestination.Main
            }
        }

    @Test
    fun `authenticated user with a subcategory route whose category id mismatches falls back to Main silently`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { getCurrentAuthUserUseCase() } returns testUser
            flashcardRepository.subcategoriesByIdsToReturn = Result.success(
                listOf(subcategory(id = "kotlin-coroutines", categoryId = "android", categoryName = "Android", name = "Coroutines")),
            )

            val viewModel = createViewModel(pendingRoute = "/study/category/wrong/subcategory/kotlin-coroutines")
            viewModel.onAnimationCompleted()

            viewModel.events.test {
                awaitItem() shouldBe SplashDestination.Main
            }
        }

    @Test
    fun `unauthenticated user with a shortcut route still emits plain Login, route dropped`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { getCurrentAuthUserUseCase() } returns null

            val viewModel = createViewModel(pendingRoute = "/study/category/android")
            viewModel.onAnimationCompleted()

            viewModel.events.test {
                awaitItem() shouldBe SplashDestination.Login
            }
        }

    private fun category(id: String, name: String) = Category(
        id = id,
        name = name,
        order = 0,
        subcategoryCount = 0,
        iconSvg = null,
        color = null,
        featuredSubcategoryNames = emptyList(),
    )

    private fun subcategory(id: String, categoryId: String, categoryName: String, name: String) = Subcategory(
        id = id,
        name = name,
        categoryId = categoryId,
        categoryName = categoryName,
        order = 0,
        cardCount = 0,
    )
}
