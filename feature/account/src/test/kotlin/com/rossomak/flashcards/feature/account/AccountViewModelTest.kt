package com.rossomak.flashcards.feature.account

import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.AppVersion
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.repository.FakeAuthRepository
import com.rossomak.flashcards.core.domain.usecase.GetAppVersionUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveAuthUserUseCase
import com.rossomak.flashcards.feature.account.AccountMessage.OpenLinkFailed
import com.rossomak.flashcards.testutil.MainDispatcherRule
import com.rossomak.flashcards.testutil.assertValue
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository()
    private val getAppVersion: GetAppVersionUseCase = mockk {
        coEvery { this@mockk() } returns APP_VERSION
    }

    private fun createViewModel(): AccountViewModel = AccountViewModel(
        observeAuthUser = ObserveAuthUserUseCase(authRepository),
        getAppVersion = getAppVersion,
    )

    private fun authUser(
        displayName: String? = USER_NAME,
        email: String? = USER_EMAIL,
        photoUrl: String? = USER_PHOTO_URL,
    ) = AuthUser(uid = "uid-1", email = email, displayName = displayName, photoUrl = photoUrl)

    @Test
    fun `the state starts with no user data and no dialog`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value shouldBe AccountScreenState(appVersion = APP_VERSION)
    }

    @Test
    fun `loads the app version into state on creation`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.appVersion shouldBe APP_VERSION
        coVerify(exactly = 1) { getAppVersion() }
    }

    @Test
    fun `the signed in user reaches the state`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        authRepository.userToReturn = authUser()
        advanceUntilIdle()

        viewModel.state.value shouldBe AccountScreenState(
            displayName = USER_NAME,
            email = USER_EMAIL,
            photoUrl = USER_PHOTO_URL,
            appVersion = APP_VERSION,
        )
    }

    @Test
    fun `a null user emission keeps the last user`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        authRepository.userToReturn = authUser()
        advanceUntilIdle()

        authRepository.userToReturn = null
        advanceUntilIdle()

        viewModel.state.assertValue {
            displayName shouldBe USER_NAME
            email shouldBe USER_EMAIL
            photoUrl shouldBe USER_PHOTO_URL
        }
    }

    @Test
    fun `a different user replaces the last one`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        authRepository.userToReturn = authUser()
        advanceUntilIdle()

        authRepository.userToReturn = authUser(displayName = OTHER_NAME, email = OTHER_EMAIL, photoUrl = null)
        advanceUntilIdle()

        viewModel.state.assertValue {
            displayName shouldBe OTHER_NAME
            email shouldBe OTHER_EMAIL
            photoUrl shouldBe null
        }
    }

    @Test
    fun `a failed link launch emits the failure message`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.messages.test {
            viewModel.onOpenLinkFailed()

            awaitItem() shouldBe OpenLinkFailed
        }
    }

    private companion object {
        const val USER_NAME = "Alex Smith"
        const val USER_EMAIL = "alex@example.com"
        const val USER_PHOTO_URL = "https://host/photo=s256-c"
        const val OTHER_NAME = "Sam Jones"
        const val OTHER_EMAIL = "sam@example.com"
        val APP_VERSION = AppVersion(name = "1.4.0", code = 142L)
    }
}
