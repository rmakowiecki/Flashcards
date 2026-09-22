package com.rossomak.flashcards.feature.settings

import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.DailyGoal
import com.rossomak.flashcards.core.domain.model.FlashcardSortOrder
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.VoiceOption
import com.rossomak.flashcards.core.domain.model.VoiceSettings as SavedVoiceSettings
import com.rossomak.flashcards.core.domain.repository.FakeStudySessionPreferencesRepository
import com.rossomak.flashcards.core.domain.repository.FakeUserPreferencesRepository
import com.rossomak.flashcards.core.domain.usecase.ObserveStudySessionPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveUserPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveStudySessionPreferenceUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveUserPreferenceUseCase
import com.rossomak.flashcards.core.domain.usecase.SignOutUseCase
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.DraftChange
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.voice.VoiceSettingsController
import com.rossomak.flashcards.core.ui.voice.VoiceSettingsDraftState
import com.rossomak.flashcards.feature.settings.SettingsDialog.DailyStudyGoal
import com.rossomak.flashcards.feature.settings.SettingsDialog.FastSessionReadAloud
import com.rossomak.flashcards.feature.settings.SettingsDialog.QuickSessionSubcategoryCountRange
import com.rossomak.flashcards.feature.settings.SettingsDialog.RatedSessionMaxCardAttempts
import com.rossomak.flashcards.feature.settings.SettingsDialog.RatedSessionPartialRatingCardRequeueing
import com.rossomak.flashcards.feature.settings.SettingsDialog.RatedSessionVoiceAnswering
import com.rossomak.flashcards.feature.settings.SettingsDialog.SessionCardCount
import com.rossomak.flashcards.feature.settings.SettingsDialog.SessionCardsSortingOrder
import com.rossomak.flashcards.feature.settings.SettingsDialog.SessionMode
import com.rossomak.flashcards.feature.settings.SettingsDialog.SessionVoiceSettings
import com.rossomak.flashcards.feature.settings.SettingsDialog.SignOut
import com.rossomak.flashcards.testutil.MainDispatcherRule
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userPreferencesRepository = FakeUserPreferencesRepository()
    private val studySessionPreferencesRepository = FakeStudySessionPreferencesRepository()
    private val signOutUseCase: SignOutUseCase = mockk()
    private val voiceSettingsController: VoiceSettingsController = mockk(relaxed = true)

    private fun createViewModel(): SettingsViewModel = SettingsViewModel(
        observeUserPreferences = ObserveUserPreferencesUseCase(userPreferencesRepository),
        observeStudySessionPreferences = ObserveStudySessionPreferencesUseCase(studySessionPreferencesRepository),
        saveUserPreference = SaveUserPreferenceUseCase(userPreferencesRepository),
        saveStudySessionPreference = SaveStudySessionPreferenceUseCase(studySessionPreferencesRepository),
        signOutUseCase = signOutUseCase,
        voiceSettingsController = voiceSettingsController,
    )

    @Test
    fun `a daily goal pushed into the store lands on the row`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        userPreferencesRepository.preferences.value = userPreferencesRepository.preferences.value.copy(
            dailyGoalMinutes = LONGER_GOAL,
        )
        advanceUntilIdle()

        viewModel.state.value.dailyGoalMinutes shouldBe LONGER_GOAL
    }

    @Test
    fun `a session length pushed into the store lands on the row`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        studySessionPreferencesRepository.preferences.value = studySessionPreferencesRepository.preferences.value.copy(
            sessionLength = LONGER_LENGTH,
        )
        advanceUntilIdle()

        viewModel.state.value.sessionLength shouldBe LONGER_LENGTH
    }

    @Test
    fun `a partial-rating-card-requeueing preference pushed into the store lands on the row`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            studySessionPreferencesRepository.preferences.value =
                studySessionPreferencesRepository.preferences.value.copy(partialRatingCardRequeueingEnabled = false)
            advanceUntilIdle()

            viewModel.state.value.partialRatingCardRequeueingEnabled shouldBe false
        }

    @Test
    fun `confirming daily goal writes it to the store`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onDialogEvent(Open(DailyStudyGoal(draftState = DailyGoal.DEFAULT_MINUTES)))
        viewModel.onDialogEvent(DraftChange(DailyStudyGoal(draftState = LONGER_GOAL)))
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()

        userPreferencesRepository.preferences.value.dailyGoalMinutes shouldBe LONGER_GOAL
        viewModel.state.value.activeDialog shouldBe null
    }

    @Test
    fun `dismissing daily goal writes nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onDialogEvent(Open(DailyStudyGoal(draftState = DailyGoal.DEFAULT_MINUTES)))
        viewModel.onDialogEvent(DraftChange(DailyStudyGoal(draftState = LONGER_GOAL)))
        viewModel.onDialogEvent(Dismiss)
        advanceUntilIdle()

        userPreferencesRepository.preferences.value.dailyGoalMinutes shouldBe DailyGoal.DEFAULT_MINUTES
    }

    @Test
    fun `confirming session length writes it to the store`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onDialogEvent(Open(SessionCardCount(draftState = DEFAULT_LENGTH)))
        viewModel.onDialogEvent(DraftChange(SessionCardCount(draftState = LONGER_LENGTH)))
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()

        studySessionPreferencesRepository.preferences.value.sessionLength shouldBe LONGER_LENGTH
        viewModel.state.value.activeDialog shouldBe null
    }

    @Test
    fun `dismissing session length writes nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        val committedLength = viewModel.state.value.sessionLength

        viewModel.onDialogEvent(Open(SessionCardCount(draftState = committedLength)))
        viewModel.onDialogEvent(DraftChange(SessionCardCount(draftState = LONGER_LENGTH)))
        viewModel.onDialogEvent(Dismiss)
        advanceUntilIdle()

        studySessionPreferencesRepository.preferences.value.sessionLength shouldBe committedLength
        viewModel.state.value.activeDialog shouldBe null
    }

    @Test
    fun `confirming rated attempts writes it to the store`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onDialogEvent(Open(RatedSessionMaxCardAttempts(draftState = DEFAULT_ATTEMPTS)))
        viewModel.onDialogEvent(DraftChange(RatedSessionMaxCardAttempts(draftState = FEWER_ATTEMPTS)))
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()

        studySessionPreferencesRepository.preferences.value.ratedAttempts shouldBe FEWER_ATTEMPTS
    }

    @Test
    fun `confirming study mode writes it to the store`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onDialogEvent(Open(SessionMode(draftState = StudyMode.Rated)))
        viewModel.onDialogEvent(DraftChange(SessionMode(draftState = StudyMode.Fast)))
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()

        studySessionPreferencesRepository.preferences.value.defaultStudyMode shouldBe StudyMode.Fast
        viewModel.state.value.defaultStudyMode shouldBe StudyMode.Fast
    }

    @Test
    fun `confirming sort order writes it to the store`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onDialogEvent(Open(SessionCardsSortingOrder(draftState = FlashcardSortOrder.Default)))
        viewModel.onDialogEvent(DraftChange(SessionCardsSortingOrder(draftState = FlashcardSortOrder.EasiestFirst)))
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()

        studySessionPreferencesRepository.preferences.value.sortOrder shouldBe FlashcardSortOrder.EasiestFirst
        viewModel.state.value.sortOrder shouldBe FlashcardSortOrder.EasiestFirst
    }

    @Test
    fun `confirming the subcategory count range writes it to the store`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = createViewModel()

            viewModel.onDialogEvent(Open(QuickSessionSubcategoryCountRange(draftState = DEFAULT_SUBCATEGORY_COUNT_RANGE)))
            viewModel.onDialogEvent(DraftChange(QuickSessionSubcategoryCountRange(draftState = NARROWER_SUBCATEGORY_COUNT_RANGE)))
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()

            studySessionPreferencesRepository.preferences.value.subcategoryCountRange shouldBe
                NARROWER_SUBCATEGORY_COUNT_RANGE
            viewModel.state.value.subcategoryCountRange shouldBe NARROWER_SUBCATEGORY_COUNT_RANGE
        }

    @Test
    fun `confirming voice answering writes it to the store`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onDialogEvent(Open(RatedSessionVoiceAnswering(draftState = false)))
        viewModel.onDialogEvent(DraftChange(RatedSessionVoiceAnswering(draftState = true)))
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()

        studySessionPreferencesRepository.preferences.value.voiceAnsweringEnabled shouldBe true
    }

    @Test
    fun `confirming read-aloud writes it to the store`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onDialogEvent(Open(FastSessionReadAloud(draftState = false)))
        viewModel.onDialogEvent(DraftChange(FastSessionReadAloud(draftState = true)))
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()

        studySessionPreferencesRepository.preferences.value.readAloudEnabled shouldBe true
        viewModel.state.value.readAloudEnabled shouldBe true
    }

    @Test
    fun `dismissing read-aloud writes nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onDialogEvent(Open(FastSessionReadAloud(draftState = false)))
        viewModel.onDialogEvent(DraftChange(FastSessionReadAloud(draftState = true)))
        viewModel.onDialogEvent(Dismiss)
        advanceUntilIdle()

        studySessionPreferencesRepository.preferences.value.readAloudEnabled shouldBe false
    }

    @Test
    fun `confirming partial-rating-card-requeueing writes it to the store`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onDialogEvent(Open(RatedSessionPartialRatingCardRequeueing(draftState = true)))
        viewModel.onDialogEvent(DraftChange(RatedSessionPartialRatingCardRequeueing(draftState = false)))
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()

        studySessionPreferencesRepository.preferences.value.partialRatingCardRequeueingEnabled shouldBe false
        viewModel.state.value.partialRatingCardRequeueingEnabled shouldBe false
    }

    @Test
    fun `dismissing partial-rating-card-requeueing writes nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onDialogEvent(Open(RatedSessionPartialRatingCardRequeueing(draftState = true)))
        viewModel.onDialogEvent(DraftChange(RatedSessionPartialRatingCardRequeueing(draftState = false)))
        viewModel.onDialogEvent(Dismiss)
        advanceUntilIdle()

        studySessionPreferencesRepository.preferences.value.partialRatingCardRequeueingEnabled shouldBe true
    }

    @Test
    fun `a failed study-session preference save closes the dialog and emits a message`() =
        runTest(mainDispatcherRule.testDispatcher) {
            studySessionPreferencesRepository.saveError = IllegalStateException("disk full")
            val viewModel = createViewModel()

            viewModel.messages.test {
                viewModel.onDialogEvent(Open(SessionCardCount(draftState = DEFAULT_LENGTH)))
                viewModel.onDialogEvent(DraftChange(SessionCardCount(draftState = LONGER_LENGTH)))
                viewModel.onDialogEvent(Confirm)
                advanceUntilIdle()

                awaitItem() shouldBe SettingsMessage.SaveFailed
                studySessionPreferencesRepository.preferences.value.sessionLength shouldBe DEFAULT_LENGTH
                viewModel.state.value.activeDialog shouldBe null
            }
        }

    @Test
    fun `a failed user preference save closes the dialog and emits a message`() =
        runTest(mainDispatcherRule.testDispatcher) {
            userPreferencesRepository.saveError = IllegalStateException("disk full")
            val viewModel = createViewModel()

            viewModel.messages.test {
                viewModel.onDialogEvent(Open(DailyStudyGoal(draftState = DailyGoal.DEFAULT_MINUTES)))
                viewModel.onDialogEvent(DraftChange(DailyStudyGoal(draftState = LONGER_GOAL)))
                viewModel.onDialogEvent(Confirm)
                advanceUntilIdle()

                awaitItem() shouldBe SettingsMessage.SaveFailed
                userPreferencesRepository.preferences.value.dailyGoalMinutes shouldBe DailyGoal.DEFAULT_MINUTES
                viewModel.state.value.activeDialog shouldBe null
            }
        }

    @Test
    fun `a successful save emits no message`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.messages.test {
            viewModel.onDialogEvent(Open(DailyStudyGoal(draftState = DailyGoal.DEFAULT_MINUTES)))
            viewModel.onDialogEvent(DraftChange(DailyStudyGoal(draftState = LONGER_GOAL)))
            viewModel.onDialogEvent(Confirm)
            advanceUntilIdle()

            expectNoEvents()
        }
    }

    @Test
    fun `editing the voice draft previews it`() = runTest(mainDispatcherRule.testDispatcher) {
        every { voiceSettingsController.seedDraft(any()) } returns VoiceSettingsDraftState()
        val viewModel = createViewModel()
        val editedDraft = VoiceSettingsDraftState(draftSpeed = FASTER_SPEECH_RATE)

        viewModel.onDialogEvent(Open(SessionVoiceSettings()))
        viewModel.onDialogEvent(DraftChange(SessionVoiceSettings(draftState = editedDraft)))

        verify(exactly = 1) { voiceSettingsController.preview(editedDraft) }
    }

    @Test
    fun `dismissing the voice dialog stops the preview`() = runTest(mainDispatcherRule.testDispatcher) {
        every { voiceSettingsController.seedDraft(any()) } returns VoiceSettingsDraftState()
        val viewModel = createViewModel()

        viewModel.onDialogEvent(Open(SessionVoiceSettings()))
        viewModel.onDialogEvent(Dismiss)

        verify(exactly = 1) { voiceSettingsController.stopPreview() }
    }

    @Test
    fun `dismissing a silent dialog leaves the shared player alone`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onDialogEvent(Open(SessionCardsSortingOrder(draftState = FlashcardSortOrder.Default)))
        viewModel.onDialogEvent(Dismiss)

        verify(exactly = 0) { voiceSettingsController.stopPreview() }
    }

    @Test
    fun `the voice row summary follows the saved settings`() = runTest(mainDispatcherRule.testDispatcher) {
        val savedVoice = VoiceOption(id = VOICE_ID, countryCode = VOICE_COUNTRY_CODE, variantIndex = VOICE_VARIANT_INDEX)
        every { voiceSettingsController.loadVoices(any(), any()) } answers {
            secondArg<(List<VoiceOption>) -> Unit>().invoke(listOf(savedVoice))
        }
        studySessionPreferencesRepository.preferences.value = studySessionPreferencesRepository.preferences.value.copy(
            voiceSettings = SavedVoiceSettings(speechRate = FASTER_SPEECH_RATE, voiceId = VOICE_ID),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.selectedVoice shouldBe savedVoice
        viewModel.state.value.speechRate shouldBe FASTER_SPEECH_RATE
    }

    @Test
    fun `the voice row summary has no name until the voice list arrives`() =
        runTest(mainDispatcherRule.testDispatcher) {
            studySessionPreferencesRepository.preferences.value = studySessionPreferencesRepository.preferences.value.copy(
                voiceSettings = SavedVoiceSettings(voiceId = VOICE_ID),
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.value.voiceId shouldBe VOICE_ID
            viewModel.state.value.selectedVoice shouldBe null
        }

    @Test
    fun `confirming sign out signs out and emits Login`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { signOutUseCase() } returns Unit

        val viewModel = createViewModel()
        viewModel.onDialogEvent(Open(SignOut))
        viewModel.onDialogEvent(Confirm)

        viewModel.events.test {
            awaitItem() shouldBe SettingsDestination.Login
        }
        viewModel.state.value.activeDialog shouldBe null
        coVerify(exactly = 1) { signOutUseCase() }
    }

    @Test
    fun `dismissing sign out does not sign out`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onDialogEvent(Open(SignOut))
        viewModel.onDialogEvent(Dismiss)
        advanceUntilIdle()

        viewModel.state.value.activeDialog shouldBe null
        viewModel.state.value.isSigningOut shouldBe false
        coVerify(exactly = 0) { signOutUseCase() }
    }

    @Test
    fun `confirming sign out emits Login even when sign-out fails`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { signOutUseCase() } throws RuntimeException("remote sign-out failed")

        val viewModel = createViewModel()
        viewModel.onDialogEvent(Open(SignOut))
        viewModel.onDialogEvent(Confirm)

        viewModel.events.test {
            awaitItem() shouldBe SettingsDestination.Login
        }
        coVerify(exactly = 1) { signOutUseCase() }
    }

    @Test
    fun `confirming sign out twice emits only one navigation event`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { signOutUseCase() } returns Unit

        val viewModel = createViewModel()
        viewModel.onDialogEvent(Open(SignOut))
        viewModel.onDialogEvent(Confirm)
        viewModel.onDialogEvent(Open(SignOut))
        viewModel.onDialogEvent(Confirm)
        advanceUntilIdle()

        viewModel.events.test {
            awaitItem() shouldBe SettingsDestination.Login
            expectNoEvents()
        }
        coVerify(exactly = 1) { signOutUseCase() }
    }

    private companion object {
        const val DEFAULT_LENGTH = 20
        const val LONGER_LENGTH = 35
        const val DEFAULT_ATTEMPTS = 3
        const val FEWER_ATTEMPTS = 1
        val DEFAULT_SUBCATEGORY_COUNT_RANGE = 3..5
        val NARROWER_SUBCATEGORY_COUNT_RANGE = 2..3
        const val LONGER_GOAL = DailyGoal.DEFAULT_MINUTES + DailyGoal.STEP_MINUTES
        const val FASTER_SPEECH_RATE = 1.25f
        const val VOICE_ID = "en-us-x-tpf-local"
        const val VOICE_COUNTRY_CODE = "US"
        const val VOICE_VARIANT_INDEX = 1
    }
}
