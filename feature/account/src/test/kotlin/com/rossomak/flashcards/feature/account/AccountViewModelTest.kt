package com.rossomak.flashcards.feature.account

import com.rossomak.flashcards.core.domain.model.AppVersion
import com.rossomak.flashcards.core.domain.usecase.GetAppVersionUseCase
import com.rossomak.flashcards.testutil.MainDispatcherRule
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

    private val getAppVersion: GetAppVersionUseCase = mockk()

    private fun createViewModel(): AccountViewModel = AccountViewModel(getAppVersion)

    @Test
    fun `loads the app version into otherwise empty state on creation`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { getAppVersion() } returns APP_VERSION

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value shouldBe AccountScreenState(appVersion = APP_VERSION)
        coVerify(exactly = 1) { getAppVersion() }
    }

    private companion object {
        val APP_VERSION = AppVersion(name = "1.4.0", code = 142L)
    }
}
