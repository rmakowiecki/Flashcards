package com.rossomak.flashcards.feature.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.model.SignInFailureReason
import com.rossomak.flashcards.core.domain.model.SignInResult
import com.rossomak.flashcards.core.domain.model.SignInResult.Cancelled
import com.rossomak.flashcards.core.domain.model.SignInResult.Failed
import com.rossomak.flashcards.core.domain.repository.FakeAuthRepository
import com.rossomak.flashcards.core.domain.repository.FakeUserPreferencesRepository
import com.rossomak.flashcards.core.domain.repository.NetworkAvailabilityGateway
import com.rossomak.flashcards.core.domain.usecase.CheckInternetAvailabilityUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveUserPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.SignInWithGoogleUseCase
import com.rossomak.flashcards.feature.auth.LoginDestination.Main
import com.rossomak.flashcards.feature.auth.LoginDestination.Onboarding
import com.rossomak.flashcards.feature.auth.LoginFailureReason.NoConnection
import com.rossomak.flashcards.feature.auth.LoginFailureReason.NoCredentialAvailable
import com.rossomak.flashcards.feature.auth.LoginFailureReason.Unknown
import com.rossomak.flashcards.feature.auth.LoginMessage.SignInFailed
import com.rossomak.flashcards.feature.auth.LoginPhase.Idle
import com.rossomak.flashcards.feature.auth.LoginPhase.SignedIn
import com.rossomak.flashcards.feature.auth.LoginPhase.SigningIn
import com.rossomak.flashcards.testutil.MainDispatcherRule
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
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
    private val networkAvailabilityGateway = mockk<NetworkAvailabilityGateway> {
        every { isInternetAvailable() } returns true
    }

    private val testUser = AuthUser("u1", "a@b.com", "Alex", null)

    private fun createViewModel(hasSeenOnboarding: Boolean = true): LoginViewModel {
        userPreferencesRepository.preferences.value =
            userPreferencesRepository.preferences.value.copy(hasSeenOnboarding = hasSeenOnboarding)
        return LoginViewModel(
            SignInWithGoogleUseCase(authRepository),
            ObserveUserPreferencesUseCase(userPreferencesRepository),
            CheckInternetAvailabilityUseCase(networkAvailabilityGateway),
        )
    }

    @Test
    fun `a token result with successful sign-in emits Main when onboarding was already seen`() =
        runTest(mainDispatcherRule.testDispatcher) {
            authRepository.signInResult = SignInResult.SignedIn(testUser)
            val viewModel = createViewModel()

            viewModel.onGoogleSignInResult(Result.success(ID_TOKEN))

            viewModel.events.test {
                awaitItem() shouldBe Main
            }
        }

    @Test
    fun `a token result with successful sign-in emits Onboarding when onboarding was never seen`() =
        runTest(mainDispatcherRule.testDispatcher) {
            authRepository.signInResult = SignInResult.SignedIn(testUser)
            val viewModel = createViewModel(hasSeenOnboarding = false)

            viewModel.onGoogleSignInResult(Result.success(ID_TOKEN))

            viewModel.events.test {
                awaitItem() shouldBe Onboarding
            }
        }

    @Test
    fun `a token result with successful sign-in ends signed in rather than idle`() =
        runTest(mainDispatcherRule.testDispatcher) {
            authRepository.signInResult = SignInResult.SignedIn(testUser)
            val viewModel = createViewModel()
            viewModel.onGoogleSignInStarted()

            viewModel.events.test {
                viewModel.onGoogleSignInResult(Result.success(ID_TOKEN))

                awaitItem() shouldBe Main
            }
            viewModel.state.value.phase shouldBe SignedIn
        }

    @Test
    fun `a token result with failed sign-in emits an Unknown failure message and stops signing in`() =
        runTest(mainDispatcherRule.testDispatcher) {
            authRepository.signInResult = Failed(SignInFailureReason.Unknown)
            val viewModel = createViewModel()
            viewModel.onGoogleSignInStarted()

            viewModel.messages.test {
                viewModel.onGoogleSignInResult(Result.success(ID_TOKEN))

                awaitItem() shouldBe SignInFailed(Unknown)
            }
            viewModel.state.value.phase shouldBe Idle
            viewModel.events.test { expectNoEvents() }

            verify(exactly = 1) { networkAvailabilityGateway.isInternetAvailable() }
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
            viewModel.state.value.phase shouldBe Idle
            viewModel.events.test { expectNoEvents() }

            verify(exactly = 1) { networkAvailabilityGateway.isInternetAvailable() }
        }

    @Test
    fun `any other failure result emits an Unknown failure message`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.messages.test {
            viewModel.onGoogleSignInResult(Result.failure(IllegalStateException(NETWORK_DOWN_MESSAGE)))

            awaitItem() shouldBe SignInFailed(Unknown)
        }

        verify(exactly = 1) { networkAvailabilityGateway.isInternetAvailable() }
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
        viewModel.state.value.phase shouldBe Idle
        viewModel.events.test { expectNoEvents() }

        verify(exactly = 0) { networkAvailabilityGateway.isInternetAvailable() }
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
        viewModel.state.value.phase shouldBe Idle
        viewModel.events.test { expectNoEvents() }

        verify(exactly = 0) { networkAvailabilityGateway.isInternetAvailable() }
    }

    @Test
    fun `starting Google sign-in sets signing in`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onGoogleSignInStarted()

        viewModel.state.value.phase shouldBe SigningIn
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

        verify(exactly = 1) { networkAvailabilityGateway.isInternetAvailable() }
    }

    @Test
    fun `an offline no-credential failure emits NoConnection rather than NoCredentialAvailable`() =
        runTest(mainDispatcherRule.testDispatcher) {
            every { networkAvailabilityGateway.isInternetAvailable() } returns false
            val viewModel = createViewModel()

            viewModel.messages.test {
                viewModel.onGoogleSignInResult(Result.failure(NoCredentialException()))

                awaitItem() shouldBe SignInFailed(NoConnection)
            }

            verify(exactly = 1) { networkAvailabilityGateway.isInternetAvailable() }
        }

    @Test
    fun `an offline unrelated failure emits NoConnection`() = runTest(mainDispatcherRule.testDispatcher) {
        every { networkAvailabilityGateway.isInternetAvailable() } returns false
        val viewModel = createViewModel()

        viewModel.messages.test {
            viewModel.onGoogleSignInResult(Result.failure(IllegalStateException(NETWORK_DOWN_MESSAGE)))

            awaitItem() shouldBe SignInFailed(NoConnection)
        }

        verify(exactly = 1) { networkAvailabilityGateway.isInternetAvailable() }
    }

    @Test
    fun `an online IOException failure emits NoConnection`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.messages.test {
            viewModel.onGoogleSignInResult(Result.failure(IOException(NETWORK_DOWN_MESSAGE)))

            awaitItem() shouldBe SignInFailed(NoConnection)
        }

        verify(exactly = 1) { networkAvailabilityGateway.isInternetAvailable() }
    }

    @Test
    fun `an account picker failure caused by an IOException emits NoConnection`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createViewModel()

            viewModel.messages.test {
                viewModel.onGoogleSignInResult(Result.failure(IllegalStateException(IOException(NETWORK_DOWN_MESSAGE))))

                awaitItem() shouldBe SignInFailed(NoConnection)
            }

            verify(exactly = 1) { networkAvailabilityGateway.isInternetAvailable() }
        }

    @Test
    fun `a token exchange that failed for no connection emits NoConnection`() = runTest(mainDispatcherRule.testDispatcher) {
        authRepository.signInResult = Failed(SignInFailureReason.NoConnection)
        val viewModel = createViewModel()

        viewModel.messages.test {
            viewModel.onGoogleSignInResult(Result.success(ID_TOKEN))

            awaitItem() shouldBe SignInFailed(NoConnection)
        }

        verify(exactly = 1) { networkAvailabilityGateway.isInternetAvailable() }
    }

    @Test
    fun `an offline dismissed account picker emits nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        every { networkAvailabilityGateway.isInternetAvailable() } returns false
        val viewModel = createViewModel()

        viewModel.messages.test {
            viewModel.onGoogleSignInResult(Result.failure(GetCredentialCancellationException()))
            advanceUntilIdle()

            expectNoEvents()
        }

        verify(exactly = 0) { networkAvailabilityGateway.isInternetAvailable() }
    }

    @Test
    fun `a failure stops signing in before connectivity is checked`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        var phaseAtCheck: LoginPhase? = null
        every { networkAvailabilityGateway.isInternetAvailable() } answers {
            phaseAtCheck = viewModel.state.value.phase
            true
        }
        viewModel.onGoogleSignInStarted()

        viewModel.messages.test {
            viewModel.onGoogleSignInResult(Result.failure(NoCredentialException()))

            awaitItem() shouldBe SignInFailed(NoCredentialAvailable)
        }
        phaseAtCheck shouldBe Idle
        verify(exactly = 1) { networkAvailabilityGateway.isInternetAvailable() }
    }

    @Test
    fun `an offline token exchange that failed for an unknown reason emits NoConnection`() =
        runTest(mainDispatcherRule.testDispatcher) {
            every { networkAvailabilityGateway.isInternetAvailable() } returns false
            authRepository.signInResult = Failed(SignInFailureReason.Unknown)
            val viewModel = createViewModel()

            viewModel.messages.test {
                viewModel.onGoogleSignInResult(Result.success(ID_TOKEN))

                awaitItem() shouldBe SignInFailed(NoConnection)
            }

            verify(exactly = 1) { networkAvailabilityGateway.isInternetAvailable() }
        }

    @Test
    fun `a cancelled token exchange emits nothing and stops signing in`() = runTest(mainDispatcherRule.testDispatcher) {
        authRepository.signInResult = Cancelled
        val viewModel = createViewModel()
        viewModel.onGoogleSignInStarted()

        viewModel.messages.test {
            viewModel.onGoogleSignInResult(Result.success(ID_TOKEN))
            advanceUntilIdle()

            expectNoEvents()
        }
        viewModel.state.value.phase shouldBe Idle
        viewModel.events.test { expectNoEvents() }

        verify(exactly = 0) { networkAvailabilityGateway.isInternetAvailable() }
    }

    @Test
    fun `a token exchange failure stops signing in before connectivity is checked`() =
        runTest(mainDispatcherRule.testDispatcher) {
            authRepository.signInResult = Failed(SignInFailureReason.Unknown)
            val viewModel = createViewModel()
            var phaseAtCheck: LoginPhase? = null
            every { networkAvailabilityGateway.isInternetAvailable() } answers {
                phaseAtCheck = viewModel.state.value.phase
                true
            }
            viewModel.onGoogleSignInStarted()

            viewModel.messages.test {
                viewModel.onGoogleSignInResult(Result.success(ID_TOKEN))

                awaitItem() shouldBe SignInFailed(Unknown)
            }
            phaseAtCheck shouldBe Idle
            verify(exactly = 1) { networkAvailabilityGateway.isInternetAvailable() }
        }

    private companion object {
        const val ID_TOKEN = "token"
        const val NETWORK_DOWN_MESSAGE = "network down"
    }
}
