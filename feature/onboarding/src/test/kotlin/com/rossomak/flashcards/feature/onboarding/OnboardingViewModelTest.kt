package com.rossomak.flashcards.feature.onboarding

import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.model.DailyGoal
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.repository.FakeAuthRepository
import com.rossomak.flashcards.core.domain.repository.FakeStudySessionPreferencesRepository
import com.rossomak.flashcards.core.domain.repository.FakeUserPreferencesRepository
import com.rossomak.flashcards.core.domain.usecase.GetCurrentAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveOnboardingPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveStudySessionPreferenceUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveUserPreferenceUseCase
import com.rossomak.flashcards.testutil.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository()
    private val userPreferencesRepository = FakeUserPreferencesRepository()
    private val studySessionPreferencesRepository = FakeStudySessionPreferencesRepository()

    private fun createViewModel(): OnboardingViewModel = OnboardingViewModel(
        getCurrentAuthUser = GetCurrentAuthUserUseCase(authRepository),
        saveOnboardingPreferences = SaveOnboardingPreferencesUseCase(
            saveStudySessionPreference = SaveStudySessionPreferenceUseCase(studySessionPreferencesRepository),
            saveUserPreference = SaveUserPreferenceUseCase(userPreferencesRepository),
        ),
    )

    private fun authUser(displayName: String?, email: String?) = AuthUser(
        uid = "uid-1",
        email = email,
        displayName = displayName,
        photoUrl = null,
    )

    @Test
    fun `user name falls back to email when display name is blank`() = runTest(mainDispatcherRule.testDispatcher) {
        authRepository.userToReturn = authUser(displayName = " ", email = "radek@example.com")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.userName shouldBe "radek@example.com"
    }

    @Test
    fun `user name prefers display name when present`() = runTest(mainDispatcherRule.testDispatcher) {
        authRepository.userToReturn = authUser(displayName = "Radek", email = "radek@example.com")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.userName shouldBe "Radek"
    }

    @Test
    fun `incrementing the daily goal past the maximum clamps it`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        val incrementsPastMaximum = (DailyGoal.MAX_MINUTES / DailyGoal.STEP_MINUTES) + 5

        repeat(incrementsPastMaximum) { viewModel.onDailyGoalIncrement() }

        viewModel.state.value.dailyGoalMinutes shouldBe DailyGoal.MAX_MINUTES
        viewModel.state.value.canIncrementDailyGoal shouldBe false
    }

    @Test
    fun `decrementing the daily goal below the minimum clamps it`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        val decrementsPastMinimum = (DailyGoal.DEFAULT_MINUTES / DailyGoal.STEP_MINUTES) + 5

        repeat(decrementsPastMinimum) { viewModel.onDailyGoalDecrement() }

        viewModel.state.value.dailyGoalMinutes shouldBe DailyGoal.MIN_MINUTES
        viewModel.state.value.canDecrementDailyGoal shouldBe false
    }

    @Test
    fun `toggling a favorite topic twice deselects it`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        val topicId = viewModel.state.value.favoriteTopicOptions.first().id

        viewModel.onFavoriteTopicToggle(topicId)
        viewModel.state.value.selectedFavoriteTopicIds shouldBe setOf(topicId)

        viewModel.onFavoriteTopicToggle(topicId)
        viewModel.state.value.selectedFavoriteTopicIds shouldBe emptySet()
    }

    @Test
    fun `finish writes the chosen preferences before flipping the seen flag`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createViewModel()
            viewModel.onStudyModeSelect(StudyMode.Fast)
            viewModel.onDailyGoalIncrement()

            viewModel.onFinish()
            advanceUntilIdle()

            studySessionPreferencesRepository.preferences.value.defaultStudyMode shouldBe StudyMode.Fast
            userPreferencesRepository.preferences.value.dailyGoalMinutes shouldBe
                DailyGoal.DEFAULT_MINUTES + DailyGoal.STEP_MINUTES
            userPreferencesRepository.preferences.value.hasSeenOnboarding shouldBe true
        }

    @Test
    fun `finish leaves the seen flag unset when the study session preference write fails`() =
        runTest(mainDispatcherRule.testDispatcher) {
            studySessionPreferencesRepository.saveError = IllegalStateException("disk full")
            val viewModel = createViewModel()

            viewModel.onFinish()
            advanceUntilIdle()

            userPreferencesRepository.preferences.value.hasSeenOnboarding shouldBe false
            userPreferencesRepository.preferences.value.dailyGoalMinutes shouldBe DailyGoal.DEFAULT_MINUTES
        }

    @Test
    fun `finish leaves the seen flag unset when the daily goal write fails`() =
        runTest(mainDispatcherRule.testDispatcher) {
            userPreferencesRepository.saveError = IllegalStateException("disk full")
            val viewModel = createViewModel()

            viewModel.onFinish()
            advanceUntilIdle()

            userPreferencesRepository.preferences.value.hasSeenOnboarding shouldBe false
        }

    @Test
    fun `finish navigates onward even when the preferences write fails`() =
        runTest(mainDispatcherRule.testDispatcher) {
            studySessionPreferencesRepository.saveError = IllegalStateException("disk full")
            val viewModel = createViewModel()

            viewModel.events.test {
                viewModel.onFinish()
                advanceUntilIdle()

                // No user configured, same as a genuine first-time run: Login, not Main.
                awaitItem() shouldBe OnboardingDestination.Login
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `finish navigates to Main for an already authenticated user`() =
        runTest(mainDispatcherRule.testDispatcher) {
            authRepository.userToReturn = authUser(displayName = "Radek", email = "radek@example.com")
            val viewModel = createViewModel()

            viewModel.events.test {
                viewModel.onFinish()
                advanceUntilIdle()

                awaitItem() shouldBe OnboardingDestination.Main
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `finish navigates to Login for an anonymous user`() =
        runTest(mainDispatcherRule.testDispatcher) {
            authRepository.userToReturn =
                authUser(displayName = "Radek", email = "radek@example.com").copy(isAnonymous = true)
            val viewModel = createViewModel()

            viewModel.events.test {
                viewModel.onFinish()
                advanceUntilIdle()

                awaitItem() shouldBe OnboardingDestination.Login
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `finish ignores repeat taps while a commit is in flight`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onFinish()
        viewModel.onFinish()
        advanceUntilIdle()

        userPreferencesRepository.preferences.value.hasSeenOnboarding shouldBe true
    }
}
