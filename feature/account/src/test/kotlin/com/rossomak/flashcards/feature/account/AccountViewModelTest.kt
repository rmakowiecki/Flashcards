package com.rossomak.flashcards.feature.account

import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.AppVersion
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.model.DeviceInfo
import com.rossomak.flashcards.core.domain.model.InstallationInfo
import com.rossomak.flashcards.core.domain.repository.FakeAuthRepository
import com.rossomak.flashcards.core.domain.usecase.GetAppVersionUseCase
import com.rossomak.flashcards.core.domain.usecase.GetCurrentAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.GetInstallationInfoUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.SignOutUseCase
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.feature.account.AccountDestination.ContactSupport
import com.rossomak.flashcards.feature.account.AccountDialog.SignOut
import com.rossomak.flashcards.feature.account.AccountMessage.NoEmailApp
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
    private val signOut: SignOutUseCase = mockk()

    private val getInstallationInfo: GetInstallationInfoUseCase = mockk {
        coEvery { this@mockk() } returns INSTALLATION_INFO
    }

    private fun createViewModel(signOutOverride: SignOutUseCase = signOut): AccountViewModel = AccountViewModel(
        observeAuthUser = ObserveAuthUserUseCase(authRepository),
        getAppVersion = getAppVersion,
        getInstallationInfo = getInstallationInfo,
        getCurrentAuthUser = GetCurrentAuthUserUseCase(authRepository),
        signOut = signOutOverride,
    )

    private fun authUser(
        displayName: String? = USER_NAME,
        email: String? = USER_EMAIL,
        photoUrl: String? = USER_PHOTO_URL,
    ) = AuthUser(uid = USER_UID, email = email, displayName = displayName, photoUrl = photoUrl)

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
    fun `a user with no profile fields clears them but keeps the app version`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        authRepository.userToReturn = authUser()
        advanceUntilIdle()

        authRepository.userToReturn = authUser(displayName = null, email = null, photoUrl = null)
        advanceUntilIdle()

        viewModel.state.value shouldBe AccountScreenState(appVersion = APP_VERSION)
    }

    @Test
    fun `dismissing with no dialog open keeps the state unchanged`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val initialState = viewModel.state.value

        viewModel.onDialogEvent(Dismiss)

        viewModel.state.value shouldBe initialState
    }

    @Test
    fun `confirming with no dialog open keeps the state unchanged`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val initialState = viewModel.state.value

        viewModel.onDialogEvent(Confirm)

        viewModel.state.value shouldBe initialState
    }

    @Test
    fun `a failed link launch emits the failure message`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.messages.test {
            viewModel.onOpenLinkFailed()

            awaitItem() shouldBe OpenLinkFailed
        }
    }

    @Test
    fun `a Contact support click emits the installation info and the account id`() = runTest(mainDispatcherRule.testDispatcher) {
        authRepository.userToReturn = authUser()
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.onContactSupportClick()

            awaitItem() shouldBe ContactSupport(INSTALLATION_INFO, uid = USER_UID)
        }
    }

    @Test
    fun `a Contact support click with no signed in user emits a null account id`() = runTest(mainDispatcherRule.testDispatcher) {
        authRepository.userToReturn = null
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.onContactSupportClick()

            awaitItem() shouldBe ContactSupport(INSTALLATION_INFO, uid = null)
        }
    }

    @Test
    fun `a Contact support click uses the user signed in at click time`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        authRepository.userToReturn = authUser()

        viewModel.events.test {
            viewModel.onContactSupportClick()

            awaitItem() shouldBe ContactSupport(INSTALLATION_INFO, uid = USER_UID)
        }
    }

    @Test
    fun `a Contact support click before anyone collects is delivered once collection starts`() =
        runTest(mainDispatcherRule.testDispatcher) {
            authRepository.userToReturn = authUser()
            val viewModel = createViewModel()

            viewModel.onContactSupportClick()
            advanceUntilIdle()

            viewModel.events.test {
                awaitItem() shouldBe ContactSupport(INSTALLATION_INFO, uid = USER_UID)
            }
        }

    @Test
    fun `each Contact support click emits its own event with fresh installation info`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createViewModel()

            viewModel.events.test {
                viewModel.onContactSupportClick()
                viewModel.onContactSupportClick()

                awaitItem() shouldBe ContactSupport(INSTALLATION_INFO, uid = null)
                awaitItem() shouldBe ContactSupport(INSTALLATION_INFO, uid = null)
            }
            coVerify(exactly = 2) { getInstallationInfo() }
        }

    @Test
    fun `no email app found emits the no email app message`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.messages.test {
            viewModel.onNoEmailAppFound()

            awaitItem() shouldBe NoEmailApp
        }
    }

    @Test
    fun `opening the sign out dialog shows it`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onDialogEvent(Open(SignOut))

        viewModel.state.value.activeDialog shouldBe SignOut
    }

    @Test
    fun `dismissing the sign out dialog does not sign out`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onDialogEvent(Open(SignOut))
        viewModel.onDialogEvent(Dismiss)
        advanceUntilIdle()

        viewModel.state.value.activeDialog shouldBe null
        coVerify(exactly = 0) { signOut() }
    }

    @Test
    fun `confirming sign out signs out and emits Login`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { signOut() } returns Unit

        val viewModel = createViewModel()
        viewModel.onDialogEvent(Open(SignOut))
        viewModel.onDialogEvent(Confirm)

        viewModel.events.test {
            awaitItem() shouldBe AccountDestination.Login
        }
        viewModel.state.value.activeDialog shouldBe null
        coVerify(exactly = 1) { signOut() }
    }

    @Test
    fun `confirming sign out emits Login even when sign-out fails`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { signOut() } throws RuntimeException("sign-out failed")

        val viewModel = createViewModel()
        viewModel.onDialogEvent(Open(SignOut))
        viewModel.onDialogEvent(Confirm)

        viewModel.events.test {
            awaitItem() shouldBe AccountDestination.Login
        }
        coVerify(exactly = 1) { signOut() }
    }

    @Test
    fun `signing out keeps the last user in the state`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel(signOutOverride = SignOutUseCase(authRepository))
        authRepository.userToReturn = authUser()
        advanceUntilIdle()

        viewModel.onDialogEvent(Open(SignOut))
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()

        authRepository.userToReturn shouldBe null
        viewModel.state.assertValue {
            displayName shouldBe USER_NAME
            email shouldBe USER_EMAIL
            photoUrl shouldBe USER_PHOTO_URL
        }
    }

    private companion object {
        const val USER_UID = "uid-1"
        const val USER_NAME = "Alex Smith"
        const val USER_EMAIL = "alex@example.com"
        const val USER_PHOTO_URL = "https://host/photo=s256-c"
        const val OTHER_NAME = "Sam Jones"
        const val OTHER_EMAIL = "sam@example.com"
        val APP_VERSION = AppVersion(name = "1.4.0", code = 142L)
        val INSTALLATION_INFO = InstallationInfo(
            appVersion = APP_VERSION,
            deviceInfo = DeviceInfo(model = "Google Pixel 8", systemVersion = 35),
        )
    }
}
