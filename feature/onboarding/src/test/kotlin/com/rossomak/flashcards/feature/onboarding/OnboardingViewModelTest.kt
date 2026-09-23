package com.rossomak.flashcards.feature.onboarding

import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.model.DailyGoal
import com.rossomak.flashcards.core.domain.model.OnboardingSubcategory
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.VoiceDemoFailureReason
import com.rossomak.flashcards.core.domain.model.VoiceDemoState
import com.rossomak.flashcards.core.domain.repository.FakeAuthRepository
import com.rossomak.flashcards.core.domain.repository.FakeOnboardingSubcategoriesRepository
import com.rossomak.flashcards.core.domain.repository.FakeStudySessionPreferencesRepository
import com.rossomak.flashcards.core.domain.repository.FakeUserFavoritesRepository
import com.rossomak.flashcards.core.domain.repository.FakeUserPreferencesRepository
import com.rossomak.flashcards.core.domain.repository.FakeVoiceDemoGateway
import com.rossomak.flashcards.core.domain.usecase.GetCurrentAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.GetOnboardingSubcategoriesUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveVoiceDemoStateUseCase
import com.rossomak.flashcards.core.domain.usecase.PlayVoiceDemoUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveOnboardingPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveStudySessionPreferenceUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveUserPreferenceUseCase
import com.rossomak.flashcards.core.domain.usecase.SetFavoriteSubcategoriesUseCase
import com.rossomak.flashcards.core.domain.usecase.SignInAnonymouslyUseCase
import com.rossomak.flashcards.core.domain.usecase.StartVoiceDemoUseCase
import com.rossomak.flashcards.core.domain.usecase.StopVoiceDemoUseCase
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
    private val onboardingSubcategoriesRepository = FakeOnboardingSubcategoriesRepository()
    private val userFavoritesRepository = FakeUserFavoritesRepository()
    private val voiceDemoGateway = FakeVoiceDemoGateway()

    private fun createViewModel(): OnboardingViewModel = OnboardingViewModel(
        getCurrentAuthUser = GetCurrentAuthUserUseCase(authRepository),
        saveOnboardingPreferences = SaveOnboardingPreferencesUseCase(
            saveStudySessionPreference = SaveStudySessionPreferenceUseCase(studySessionPreferencesRepository),
            saveUserPreference = SaveUserPreferenceUseCase(userPreferencesRepository),
        ),
        getOnboardingSubcategories = GetOnboardingSubcategoriesUseCase(onboardingSubcategoriesRepository),
        setFavoriteSubcategories = SetFavoriteSubcategoriesUseCase(userFavoritesRepository),
        signInAnonymously = SignInAnonymouslyUseCase(authRepository),
        observeVoiceDemoState = ObserveVoiceDemoStateUseCase(voiceDemoGateway),
        startVoiceDemo = StartVoiceDemoUseCase(voiceDemoGateway),
        playVoiceDemo = PlayVoiceDemoUseCase(voiceDemoGateway),
        stopVoiceDemo = StopVoiceDemoUseCase(voiceDemoGateway),
    )

    private fun authUser(displayName: String?, email: String?) = AuthUser(
        uid = "uid-1",
        email = email,
        displayName = displayName,
        photoUrl = null,
    )

    private fun subcategory(id: String) = OnboardingSubcategory(
        id = id,
        name = "Compose",
        categoryId = "android",
        categoryName = "Android",
        order = 0,
        iconSvg = "<svg/>",
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
    fun `toggling a favorite subcategory twice deselects it`() = runTest(mainDispatcherRule.testDispatcher) {
        onboardingSubcategoriesRepository.resultToReturn = Result.success(listOf(subcategory("android-compose")))
        val viewModel = createViewModel()
        viewModel.onFavoritesStepEntered()
        advanceUntilIdle()
        val subcategoryId = viewModel.state.value.favoriteSubcategoryOptions.first().id

        viewModel.onFavoriteSubcategoryToggle(subcategoryId)
        viewModel.state.value.selectedFavoriteSubcategoriesIds shouldBe setOf(subcategoryId)

        viewModel.onFavoriteSubcategoryToggle(subcategoryId)
        viewModel.state.value.selectedFavoriteSubcategoriesIds shouldBe emptySet()
    }

    @Test
    fun `favorites step entry shows loading then the fetched options`() = runTest(mainDispatcherRule.testDispatcher) {
        onboardingSubcategoriesRepository.resultToReturn = Result.success(listOf(subcategory("android-compose")))
        val viewModel = createViewModel()

        viewModel.onFavoritesStepEntered()
        viewModel.state.value.isFavoriteSubcategoriesLoading shouldBe true

        advanceUntilIdle()

        viewModel.state.value.isFavoriteSubcategoriesLoading shouldBe false
        viewModel.state.value.favoriteSubcategoryOptions.map { it.id } shouldBe listOf("android-compose")
    }

    @Test
    fun `favorites step entry surfaces a load failure`() = runTest(mainDispatcherRule.testDispatcher) {
        onboardingSubcategoriesRepository.resultToReturn = Result.failure(IllegalStateException("offline"))
        val viewModel = createViewModel()

        viewModel.onFavoritesStepEntered()
        advanceUntilIdle()

        viewModel.state.value.isFavoriteSubcategoriesLoading shouldBe false
        viewModel.state.value.favoriteSubcategoriesLoadingFailed shouldBe true
        viewModel.state.value.favoriteSubcategoryOptions shouldBe emptyList()
    }

    @Test
    fun `favorites step entry does not refetch once options are loaded`() = runTest(mainDispatcherRule.testDispatcher) {
        onboardingSubcategoriesRepository.resultToReturn = Result.success(listOf(subcategory("android-compose")))
        val viewModel = createViewModel()
        viewModel.onFavoritesStepEntered()
        advanceUntilIdle()

        onboardingSubcategoriesRepository.resultToReturn = Result.success(listOf(subcategory("kotlin-coroutines")))
        viewModel.onFavoritesStepEntered()
        advanceUntilIdle()

        viewModel.state.value.favoriteSubcategoryOptions.map { it.id } shouldBe listOf("android-compose")
    }

    @Test
    fun `retry after a load failure fetches again`() = runTest(mainDispatcherRule.testDispatcher) {
        onboardingSubcategoriesRepository.resultToReturn = Result.failure(IllegalStateException("offline"))
        val viewModel = createViewModel()
        viewModel.onFavoritesStepEntered()
        advanceUntilIdle()
        viewModel.state.value.favoriteSubcategoriesLoadingFailed shouldBe true

        onboardingSubcategoriesRepository.resultToReturn = Result.success(listOf(subcategory("android-compose")))
        viewModel.onFavoriteSubcategoriesRetry()
        advanceUntilIdle()

        viewModel.state.value.favoriteSubcategoriesLoadingFailed shouldBe false
        viewModel.state.value.favoriteSubcategoryOptions.map { it.id } shouldBe listOf("android-compose")
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

    @Test
    fun `finish with no favorites picked never starts an anonymous session`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createViewModel()

            viewModel.onFinish()
            advanceUntilIdle()

            authRepository.signInAnonymouslyCallCount shouldBe 0
            userPreferencesRepository.preferences.value.hasSeenOnboarding shouldBe true
        }

    @Test
    fun `finish with a favorite picked signs in anonymously and writes the pick`() =
        runTest(mainDispatcherRule.testDispatcher) {
            onboardingSubcategoriesRepository.resultToReturn = Result.success(listOf(subcategory("android-compose")))
            authRepository.signInAnonymouslyResult = Result.success(
                AuthUser(uid = "anon-uid", email = null, displayName = null, photoUrl = null, isAnonymous = true),
            )
            val viewModel = createViewModel()
            viewModel.onFavoritesStepEntered()
            advanceUntilIdle()
            viewModel.onFavoriteSubcategoryToggle("android-compose")

            viewModel.onFinish()
            advanceUntilIdle()

            authRepository.signInAnonymouslyCallCount shouldBe 1
            userFavoritesRepository.lastSetSubcategoriesFavoriteCall shouldBe (setOf("android-compose") to true)
            userPreferencesRepository.preferences.value.hasSeenOnboarding shouldBe true
        }

    @Test
    fun `finish with a favorite picked as an authenticated user does not start an anonymous session`() =
        runTest(mainDispatcherRule.testDispatcher) {
            onboardingSubcategoriesRepository.resultToReturn = Result.success(listOf(subcategory("android-compose")))
            authRepository.userToReturn = authUser(displayName = "Ada", email = "ada@example.com")
            val viewModel = createViewModel()
            viewModel.onFavoritesStepEntered()
            advanceUntilIdle()
            viewModel.onFavoriteSubcategoryToggle("android-compose")

            viewModel.onFinish()
            advanceUntilIdle()

            authRepository.signInAnonymouslyCallCount shouldBe 0
            userFavoritesRepository.lastSetSubcategoriesFavoriteCall shouldBe (setOf("android-compose") to true)
            userPreferencesRepository.preferences.value.hasSeenOnboarding shouldBe true
        }

    @Test
    fun `finish leaves the seen flag unset when the anonymous sign-in fails`() =
        runTest(mainDispatcherRule.testDispatcher) {
            onboardingSubcategoriesRepository.resultToReturn = Result.success(listOf(subcategory("android-compose")))
            authRepository.signInAnonymouslyResult = Result.failure(IllegalStateException("offline"))
            val viewModel = createViewModel()
            viewModel.onFavoritesStepEntered()
            advanceUntilIdle()
            viewModel.onFavoriteSubcategoryToggle("android-compose")

            viewModel.onFinish()
            advanceUntilIdle()

            userPreferencesRepository.preferences.value.hasSeenOnboarding shouldBe false
            userFavoritesRepository.lastSetSubcategoriesFavoriteCall shouldBe null
        }

    @Test
    fun `finish leaves the seen flag unset when the favorites write fails`() =
        runTest(mainDispatcherRule.testDispatcher) {
            onboardingSubcategoriesRepository.resultToReturn = Result.success(listOf(subcategory("android-compose")))
            authRepository.signInAnonymouslyResult = Result.success(
                AuthUser(uid = "anon-uid", email = null, displayName = null, photoUrl = null, isAnonymous = true),
            )
            userFavoritesRepository.setSubcategoriesFavoriteResult = Result.failure(IllegalStateException("offline"))
            val viewModel = createViewModel()
            viewModel.onFavoritesStepEntered()
            advanceUntilIdle()
            viewModel.onFavoriteSubcategoryToggle("android-compose")

            viewModel.onFinish()
            advanceUntilIdle()

            userPreferencesRepository.preferences.value.hasSeenOnboarding shouldBe false
        }

    @Test
    fun `finish leaves the seen flag unset when the anonymous sign-in times out`() =
        runTest(mainDispatcherRule.testDispatcher) {
            onboardingSubcategoriesRepository.resultToReturn = Result.success(listOf(subcategory("android-compose")))
            authRepository.signInAnonymouslyResult = Result.success(
                AuthUser(uid = "anon-uid", email = null, displayName = null, photoUrl = null, isAnonymous = true),
            )
            authRepository.signInAnonymouslyDelayMs = 10_000L
            val viewModel = createViewModel()
            viewModel.onFavoritesStepEntered()
            advanceUntilIdle()
            viewModel.onFavoriteSubcategoryToggle("android-compose")

            viewModel.onFinish()
            advanceUntilIdle()

            userPreferencesRepository.preferences.value.hasSeenOnboarding shouldBe false
            userFavoritesRepository.lastSetSubcategoriesFavoriteCall shouldBe null
        }

    @Test
    fun `voice demo state is mirrored into screen state`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        voiceDemoGateway.state.value = VoiceDemoState.Ready
        advanceUntilIdle()

        viewModel.state.value.voiceDemoState shouldBe VoiceDemoState.Ready
    }

    @Test
    fun `voice demo failure emits its reason`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.voiceDemoFailureMessages.test {
            voiceDemoGateway.state.value = VoiceDemoState.Failed(VoiceDemoFailureReason.RouteUnavailable)
            advanceUntilIdle()

            awaitItem() shouldBe VoiceDemoFailureReason.RouteUnavailable
        }
    }

    @Test
    fun `voice demo start, play and stop reach the gateway`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onVoiceDemoStart()
        viewModel.onVoiceDemoPlay()
        viewModel.onVoiceDemoStop()
        advanceUntilIdle()

        voiceDemoGateway.startCount shouldBe 1
        voiceDemoGateway.playCount shouldBe 1
        voiceDemoGateway.stopCount shouldBe 1
    }
}
