package com.rossomak.flashcards.feature.auth

import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.repository.FakeAuthRepository
import com.rossomak.flashcards.core.domain.repository.FakeUserPreferencesRepository
import com.rossomak.flashcards.core.domain.usecase.ObserveUserPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.SignInWithGoogleUseCase
import com.rossomak.flashcards.testutil.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository()
    private val userPreferencesRepository = FakeUserPreferencesRepository()

    private val testUser = AuthUser("u1", "a@b.com", "Alex", null)

    private fun createViewModel(hasSeenOnboarding: Boolean = true): LoginViewModel {
        userPreferencesRepository.preferences.value =
            userPreferencesRepository.preferences.value.copy(hasSeenOnboarding = hasSeenOnboarding)
        return LoginViewModel(
            SignInWithGoogleUseCase(authRepository),
            ObserveUserPreferencesUseCase(userPreferencesRepository),
        )
    }

    @Test
    fun `onGoogleIdTokenReceived with successful sign-in emits Main when onboarding was already seen`() =
        runTest(mainDispatcherRule.testDispatcher) {
            authRepository.signInResult = Result.success(testUser)

            val viewModel = createViewModel()
            viewModel.onGoogleIdTokenReceived("token")

            viewModel.events.test {
                awaitItem() shouldBe LoginDestination.Main
            }
        }

    @Test
    fun `onGoogleIdTokenReceived with successful sign-in emits Onboarding when onboarding was never seen`() =
        runTest(mainDispatcherRule.testDispatcher) {
            authRepository.signInResult = Result.success(testUser)

            val viewModel = createViewModel(hasSeenOnboarding = false)
            viewModel.onGoogleIdTokenReceived("token")

            viewModel.events.test {
                awaitItem() shouldBe LoginDestination.Onboarding
            }
        }

    @Test
    fun `onGoogleIdTokenReceived with failed sign-in emits no navigation event`() = runTest(mainDispatcherRule.testDispatcher) {
        val cause = IllegalStateException("network down")
        authRepository.signInResult = Result.failure(cause)

        val viewModel = createViewModel()
        viewModel.onGoogleIdTokenReceived("token")
        advanceUntilIdle()

        viewModel.state.value.failureReason shouldBe LoginFailureReason.SignInFailed(cause)
        viewModel.events.test {
            expectNoEvents()
        }
    }

    @Test
    fun `onSignInFailed emits no navigation event`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onSignInFailed(LoginFailureReason.NoCredentialAvailable)
        advanceUntilIdle()

        viewModel.events.test {
            expectNoEvents()
        }
    }

    @Test
    fun `onSignInCancelled clears signing-in state without setting a failure reason`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createViewModel()
            viewModel.onSignInStarted()

            viewModel.onSignInCancelled()

            viewModel.state.value.isSigningIn shouldBe false
            viewModel.state.value.failureReason shouldBe null
        }
}
