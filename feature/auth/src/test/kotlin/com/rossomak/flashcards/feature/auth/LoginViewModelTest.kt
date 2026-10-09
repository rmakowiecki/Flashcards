package com.rossomak.flashcards.feature.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.repository.FakeAuthRepository
import com.rossomak.flashcards.core.domain.repository.FakeUserPreferencesRepository
import com.rossomak.flashcards.core.domain.usecase.ObserveUserPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.SignInWithGoogleUseCase
import com.rossomak.flashcards.feature.auth.LoginDestination.Main
import com.rossomak.flashcards.feature.auth.LoginDestination.Onboarding
import com.rossomak.flashcards.feature.auth.LoginFailureReason.NoCredentialAvailable
import com.rossomak.flashcards.feature.auth.LoginFailureReason.Unknown
import com.rossomak.flashcards.feature.auth.LoginMessage.SignInFailed
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
    fun `a token result with successful sign-in emits Main when onboarding was already seen`() =
        runTest(mainDispatcherRule.testDispatcher) {
            authRepository.signInResult = Result.success(testUser)
            val viewModel = createViewModel()

            viewModel.onGoogleSignInResult(Result.success(ID_TOKEN))

            viewModel.events.test {
                awaitItem() shouldBe Main
            }
        }

    @Test
    fun `a token result with successful sign-in emits Onboarding when onboarding was never seen`() =
        runTest(mainDispatcherRule.testDispatcher) {
            authRepository.signInResult = Result.success(testUser)
            val viewModel = createViewModel(hasSeenOnboarding = false)

            viewModel.onGoogleSignInResult(Result.success(ID_TOKEN))

            viewModel.events.test {
                awaitItem() shouldBe Onboarding
            }
        }

    @Test
    fun `a token result with failed sign-in emits an Unknown failure message and stops signing in`() =
        runTest(mainDispatcherRule.testDispatcher) {
            authRepository.signInResult = Result.failure(IllegalStateException(NETWORK_DOWN_MESSAGE))
            val viewModel = createViewModel()
            viewModel.onGoogleSignInStarted()

            viewModel.messages.test {
                viewModel.onGoogleSignInResult(Result.success(ID_TOKEN))

                awaitItem() shouldBe SignInFailed(Unknown)
            }
            viewModel.state.value.isSigningIn shouldBe false
            viewModel.events.test { expectNoEvents() }
        }

    @Test
    fun `a no-credential failure result emits a NoCredentialAvailable message and stops signing in`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createViewModel()
            viewModel.onGoogleSignInStarted()

            viewModel.messages.test {
                viewModel.onGoogleSignInResult(Result.failure(NoCredentialException()))

                awaitItem() shouldBe SignInFailed(NoCredentialAvailable)
            }
            viewModel.state.value.isSigningIn shouldBe false
            viewModel.events.test { expectNoEvents() }
        }

    @Test
    fun `any other failure result emits an Unknown failure message`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.messages.test {
            viewModel.onGoogleSignInResult(Result.failure(IllegalStateException(NETWORK_DOWN_MESSAGE)))

            awaitItem() shouldBe SignInFailed(Unknown)
        }
    }

    @Test
    fun `a dismissed account picker emits nothing and stops signing in`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onGoogleSignInStarted()

        viewModel.messages.test {
            viewModel.onGoogleSignInResult(Result.failure(GetCredentialCancellationException()))
            advanceUntilIdle()

            expectNoEvents()
        }
        viewModel.state.value.isSigningIn shouldBe false
        viewModel.events.test { expectNoEvents() }
    }

    @Test
    fun `an interrupted picker emits nothing and stops signing in`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onGoogleSignInStarted()

        viewModel.messages.test {
            viewModel.onGoogleSignInInterrupted()
            advanceUntilIdle()

            expectNoEvents()
        }
        viewModel.state.value.isSigningIn shouldBe false
        viewModel.events.test { expectNoEvents() }
    }

    @Test
    fun `starting Google sign-in sets signing in`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onGoogleSignInStarted()

        viewModel.state.value.isSigningIn shouldBe true
    }

    @Test
    fun `a delivered failure message is not replayed to a late collector`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.messages.test {
            viewModel.onGoogleSignInResult(Result.failure(NoCredentialException()))
            awaitItem() shouldBe SignInFailed(NoCredentialAvailable)
        }

        viewModel.messages.test {
            expectNoEvents()
        }
    }

    private companion object {
        const val ID_TOKEN = "token"
        const val NETWORK_DOWN_MESSAGE = "network down"
    }
}
