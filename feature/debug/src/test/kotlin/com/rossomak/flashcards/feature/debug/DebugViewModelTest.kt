package com.rossomak.flashcards.feature.debug

import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.AppVersion
import com.rossomak.flashcards.core.domain.repository.FakeUserPreferencesRepository
import com.rossomak.flashcards.core.domain.usecase.GetAppVersionUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveUserPreferenceUseCase
import com.rossomak.flashcards.testutil.MainDispatcherRule
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebugViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userPreferencesRepository = FakeUserPreferencesRepository()
    private val getAppVersion: GetAppVersionUseCase = mockk()

    @Before
    fun setUp() {
        coEvery { getAppVersion() } returns APP_VERSION
    }

    private fun createViewModel(): DebugViewModel =
        DebugViewModel(getAppVersion, SaveUserPreferenceUseCase(userPreferencesRepository))

    @Test
    fun `loads the app version into state on creation`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.appVersion shouldBe APP_VERSION
        coVerify(exactly = 1) { getAppVersion() }
    }

    @Test
    fun `onReplayOnboardingClick clears the seen flag and emits Onboarding`() =
        runTest(mainDispatcherRule.testDispatcher) {
            userPreferencesRepository.preferences.value =
                userPreferencesRepository.preferences.value.copy(hasSeenOnboarding = true)

            val viewModel = createViewModel()
            viewModel.onReplayOnboardingClick()

            viewModel.events.test {
                awaitItem() shouldBe DebugDestination.Onboarding
            }
            userPreferencesRepository.preferences.value.hasSeenOnboarding shouldBe false
            coVerify(exactly = 1) { getAppVersion() }
        }

    private companion object {
        val APP_VERSION = AppVersion(name = "1.4.142-debug", code = 142L)
    }
}
