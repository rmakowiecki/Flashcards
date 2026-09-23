package com.rossomak.flashcards.feature.study.preview

import androidx.lifecycle.SavedStateHandle
import com.rossomak.flashcards.core.domain.model.FlashcardSortOrder
import com.rossomak.flashcards.core.domain.model.StudySessionPreferences
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import com.rossomak.flashcards.core.domain.repository.FakePermissionRepository
import com.rossomak.flashcards.core.domain.repository.FakeStudySessionPreferencesRepository
import com.rossomak.flashcards.core.domain.repository.FakeUserPreferencesRepository
import com.rossomak.flashcards.core.domain.usecase.FilterFlashcardsUseCase
import com.rossomak.flashcards.core.domain.usecase.GetFlashcardsUseCase
import com.rossomak.flashcards.core.domain.usecase.ObservePermissionStatusUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveStudySessionPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveUserPreferencesUseCase
import com.rossomak.flashcards.core.domain.usecase.RequestPermissionUseCase
import com.rossomak.flashcards.core.domain.usecase.SampleQuickSessionSubcategoriesUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveStudySessionPreferenceUseCase
import com.rossomak.flashcards.core.domain.usecase.SaveUserPreferenceUseCase
import com.rossomak.flashcards.core.domain.usecase.SelectSessionFlashcardsUseCase
import com.rossomak.flashcards.core.ui.navigation.RouteDecoder
import com.rossomak.flashcards.core.ui.voice.VoiceSettingsController
import com.rossomak.flashcards.feature.study.PreviewStudySessionRoute
import com.rossomak.flashcards.testutil.MainDispatcherRule
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * How the Preview screen decides which sort order and difficulty range a session opens with.
 *
 * Split out of [PreviewStudySessionViewModelTest] purely to keep that class within detekt's size
 * limit — this is the route-versus-saved-default precedence rule from ADR-0038, nothing else.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PreviewStudySessionSortSeedingTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val savedStateHandle: SavedStateHandle = mockk()
    private val flashcardRepository = FakeFlashcardRepository()
    private val preferencesRepository = FakeStudySessionPreferencesRepository()
    private val userPreferencesRepository = FakeUserPreferencesRepository()
    private val permissionRepository = FakePermissionRepository()
    private val voiceSettingsController: VoiceSettingsController = mockk(relaxed = true)

    private val route = PreviewStudySessionRoute(
        categoryId = "android",
        categoryName = "Android",
        subcategoryIds = listOf("android-compose"),
        subcategoryNames = listOf("Compose"),
    )

    @Before
    fun setUp() {
        mockkObject(RouteDecoder)
        stubRoute(route)
    }

    @After
    fun tearDown() {
        unmockkObject(RouteDecoder)
    }

    private fun stubRoute(route: PreviewStudySessionRoute) {
        every { RouteDecoder.decode(any<() -> PreviewStudySessionRoute>()) } returns route
    }

    private fun createViewModel(): PreviewStudySessionViewModel = PreviewStudySessionViewModel(
        savedStateHandle,
        SelectSessionFlashcardsUseCase(
            getFlashcards = GetFlashcardsUseCase(flashcardRepository),
            filterFlashcards = FilterFlashcardsUseCase(),
            random = Random.Default,
        ),
        SampleQuickSessionSubcategoriesUseCase(random = Random.Default),
        ObserveStudySessionPreferencesUseCase(preferencesRepository),
        SaveStudySessionPreferenceUseCase(preferencesRepository),
        ObserveUserPreferencesUseCase(userPreferencesRepository),
        SaveUserPreferenceUseCase(userPreferencesRepository),
        ObservePermissionStatusUseCase(permissionRepository),
        RequestPermissionUseCase(permissionRepository),
        voiceSettingsController,
    )

    @Test
    fun `a route sort order wins over the saved default`() = runTest(mainDispatcherRule.testDispatcher) {
        preferencesRepository.preferences.value =
            StudySessionPreferences(sortOrder = FlashcardSortOrder.EasiestFirst)
        stubRoute(route.copy(sortOrder = FlashcardSortOrder.HardestFirst))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.config.sortOrder shouldBe FlashcardSortOrder.HardestFirst
    }

    @Test
    fun `a null route sort order falls back to the saved default`() = runTest(mainDispatcherRule.testDispatcher) {
        preferencesRepository.preferences.value =
            StudySessionPreferences(sortOrder = FlashcardSortOrder.EasiestFirst)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.config.sortOrder shouldBe FlashcardSortOrder.EasiestFirst
    }

    /** A deliberate Default on the route must not be mistaken for "nothing was chosen". */
    @Test
    fun `an explicit Default on the route is honoured over a non-default saved order`() =
        runTest(mainDispatcherRule.testDispatcher) {
            preferencesRepository.preferences.value =
                StudySessionPreferences(sortOrder = FlashcardSortOrder.HardestFirst)
            stubRoute(route.copy(sortOrder = FlashcardSortOrder.Default))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.value.config.sortOrder shouldBe FlashcardSortOrder.Default
        }

    @Test
    fun `the route difficulty range seeds the session config`() = runTest(mainDispatcherRule.testDispatcher) {
        stubRoute(route.copy(difficultyMin = 3, difficultyMax = 7))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.state.value.config.difficultyRange shouldBe 3..7
    }
}
