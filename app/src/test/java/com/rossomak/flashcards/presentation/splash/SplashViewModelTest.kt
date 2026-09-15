package com.rossomak.flashcards.presentation.splash

import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.repository.FakeUserPreferencesRepository
import com.rossomak.flashcards.core.domain.usecase.GetCurrentAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveUserPreferencesUseCase
import com.rossomak.flashcards.testutil.MainDispatcherRule
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getCurrentAuthUserUseCase: GetCurrentAuthUserUseCase = mockk()
    private val userPreferencesRepository = FakeUserPreferencesRepository()

    private val testUser = AuthUser("u1", "a@b.com", "Alex", null)

    private fun createViewModel(hasSeenOnboarding: Boolean = true): SplashViewModel {
        userPreferencesRepository.preferences.value =
            userPreferencesRepository.preferences.value.copy(hasSeenOnboarding = hasSeenOnboarding)
        return SplashViewModel(
            getCurrentAuthUserUseCase,
            ObserveUserPreferencesUseCase(userPreferencesRepository),
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
}
