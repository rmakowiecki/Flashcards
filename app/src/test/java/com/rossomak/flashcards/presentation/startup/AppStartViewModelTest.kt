package com.rossomak.flashcards.presentation.startup

import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import com.rossomak.flashcards.core.domain.usecase.ClearDynamicShortcutsUseCase
import com.rossomak.flashcards.core.domain.usecase.GetCurrentAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.IsSignedInUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.SyncDynamicShortcutsUseCase
import com.rossomak.flashcards.core.domain.usecase.SyncFlashcardCacheGenerationUseCase
import com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsRoute
import com.rossomak.flashcards.presentation.shortcut.ShortcutDestination
import com.rossomak.flashcards.presentation.shortcut.shortcutCategory
import com.rossomak.flashcards.presentation.shortcut.shortcutRouteResolver
import com.rossomak.flashcards.testutil.MainDispatcherRule
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppStartViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getCurrentAuthUserUseCase: GetCurrentAuthUserUseCase = mockk()
    private val authUserFlow = MutableStateFlow<AuthUser?>(null)
    private val observeAuthUserUseCase: ObserveAuthUserUseCase = mockk {
        coEvery { this@mockk() } returns authUserFlow
    }
    private val syncFlashcardCacheGenerationUseCase: SyncFlashcardCacheGenerationUseCase = mockk {
        coEvery { this@mockk() } returns Unit
    }
    private val syncDynamicShortcutsUseCase: SyncDynamicShortcutsUseCase = mockk {
        coEvery { this@mockk() } coAnswers { delay(Long.MAX_VALUE) }
    }
    private val clearDynamicShortcutsUseCase: ClearDynamicShortcutsUseCase = mockk {
        coEvery { this@mockk() } returns Unit
    }

    private val flashcardRepository = FakeFlashcardRepository().apply {
        categoriesByIdsToReturn = Result.success(listOf(shortcutCategory(id = ANDROID_ID, name = ANDROID_NAME)))
    }

    private val testUser = AuthUser("u1", "a@b.com", "Alex", null)

    private fun createViewModel(): AppStartViewModel = AppStartViewModel(
        IsSignedInUseCase(getCurrentAuthUserUseCase),
        observeAuthUserUseCase,
        syncFlashcardCacheGenerationUseCase,
        syncDynamicShortcutsUseCase,
        clearDynamicShortcutsUseCase,
        shortcutRouteResolver(flashcardRepository),
    )

    private fun createWarmViewModel(
        state: AppNavigationState = AppNavigationState(top = TopDestination.Other),
        authUser: AuthUser? = testUser,
    ): AppStartViewModel {
        coEvery { getCurrentAuthUserUseCase() } returns authUser
        return createViewModel().apply { onNavigationStateChange(state) }
    }

    @Test
    fun `startupState initial value is Loading`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { getCurrentAuthUserUseCase() } returns testUser

        val viewModel = createViewModel()

        viewModel.startupState.value shouldBe AppStartupState.Loading
    }

    @Test
    fun `authenticated user emits Ready with true`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { getCurrentAuthUserUseCase() } returns testUser

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startupState.value shouldBe AppStartupState.Ready(authenticated = true)
    }

    @Test
    fun `no user emits Ready with false`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { getCurrentAuthUserUseCase() } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startupState.value shouldBe AppStartupState.Ready(authenticated = false)
    }

    @Test
    fun `auth timeout emits Ready with false after 1000ms`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { getCurrentAuthUserUseCase() } coAnswers {
            delay(Long.MAX_VALUE)
            null
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startupState.value shouldBe AppStartupState.Ready(authenticated = false)
        testScheduler.currentTime shouldBe 1000L
    }

    @Test
    fun `startup runs the cache generation sync alongside the auth check`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { getCurrentAuthUserUseCase() } returns testUser

        createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { syncFlashcardCacheGenerationUseCase() }
    }

    @Test
    fun `a hanging cache generation sync does not delay Ready`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { getCurrentAuthUserUseCase() } returns testUser
        coEvery { syncFlashcardCacheGenerationUseCase() } coAnswers { delay(Long.MAX_VALUE) }

        val viewModel = createViewModel()
        // runCurrent(), not advanceUntilIdle(): advanceUntilIdle() fast-forwards virtual time
        // through the mocked Long.MAX_VALUE delay too, so it would let this "hanging" coroutine
        // finish and pass even if Ready were actually gated behind it. runCurrent() only runs work
        // already scheduled at the current instant, proving Ready arrives while the sync is still
        // parked on its delay, not merely that it arrives eventually.
        runCurrent()

        viewModel.startupState.value shouldBe AppStartupState.Ready(authenticated = true)
        testScheduler.currentTime shouldBe 0L
    }

    @Test
    fun `authenticated auth state starts dynamic shortcut sync`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { getCurrentAuthUserUseCase() } returns null
        authUserFlow.value = testUser

        createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { syncDynamicShortcutsUseCase() }
        coVerify(exactly = 0) { clearDynamicShortcutsUseCase() }
    }

    @Test
    fun `signed-out auth state clears dynamic shortcuts`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { getCurrentAuthUserUseCase() } returns null
        authUserFlow.value = null

        createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { clearDynamicShortcutsUseCase() }
        coVerify(exactly = 0) { syncDynamicShortcutsUseCase() }
    }

    @Test
    fun `sign-out mid-session cancels the shortcut sync and clears shortcuts`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { getCurrentAuthUserUseCase() } returns null
            authUserFlow.value = testUser

            createViewModel()
            advanceUntilIdle()
            coVerify(exactly = 1) { syncDynamicShortcutsUseCase() }

            authUserFlow.value = null
            advanceUntilIdle()

            coVerify(exactly = 1) { clearDynamicShortcutsUseCase() }
        }

    @Test
    fun `anonymous auth user is treated as signed out for shortcuts`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { getCurrentAuthUserUseCase() } returns null
        authUserFlow.value = testUser.copy(isAnonymous = true)

        createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { clearDynamicShortcutsUseCase() }
        coVerify(exactly = 0) { syncDynamicShortcutsUseCase() }
    }

    @Test
    fun `switching to a second authenticated user without an intervening null restarts the shortcut sync`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { getCurrentAuthUserUseCase() } returns null
            val otherUser = testUser.copy(uid = "u2")
            authUserFlow.value = testUser

            createViewModel()
            advanceUntilIdle()
            coVerify(exactly = 1) { syncDynamicShortcutsUseCase() }

            authUserFlow.value = otherUser
            advanceUntilIdle()

            coVerify(exactly = 2) { syncDynamicShortcutsUseCase() }
            coVerify(exactly = 0) { clearDynamicShortcutsUseCase() }
        }

    // region warm shortcut tap

    @Test
    fun `a resolvable shortcut tap while signed in opens its Details`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createWarmViewModel()

        viewModel.shortcutEvents.test {
            viewModel.onShortcutTap(CATEGORY_ROUTE)

            awaitItem() shouldBe ShortcutDestination.ToCategoryDetails(CategoryDetailsRoute(categoryId = ANDROID_ID, categoryName = ANDROID_NAME))
            expectNoEvents()
        }
    }

    @Test
    fun `a shortcut tap from a Guest is dropped`() = runTest(mainDispatcherRule.testDispatcher) {
        assertShortcutTapDropped(createWarmViewModel(authUser = testUser.copy(isAnonymous = true)))
    }

    @Test
    fun `a shortcut tap while signed out is dropped`() = runTest(mainDispatcherRule.testDispatcher) {
        assertShortcutTapDropped(createWarmViewModel(authUser = null))
    }

    @Test
    fun `a shortcut tap at a launch screen is dropped`() = runTest(mainDispatcherRule.testDispatcher) {
        assertShortcutTapDropped(createWarmViewModel(AppNavigationState(top = TopDestination.Launch)))
    }

    @Test
    fun `a shortcut tap before the nav host reports anything is dropped`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { getCurrentAuthUserUseCase() } returns testUser

        assertShortcutTapDropped(createViewModel())
    }

    @Test
    fun `a shortcut tap during a Study Session is dropped`() = runTest(mainDispatcherRule.testDispatcher) {
        assertShortcutTapDropped(createWarmViewModel(AppNavigationState(top = TopDestination.Other, isStudySessionInBackStack = true)))
    }

    @Test
    fun `a shortcut tap whose target no longer resolves is dropped`() = runTest(mainDispatcherRule.testDispatcher) {
        flashcardRepository.categoriesByIdsToReturn = Result.success(emptyList())

        assertShortcutTapDropped(createWarmViewModel())
    }

    @Test
    fun `a shortcut tap whose lookup times out is dropped`() = runTest(mainDispatcherRule.testDispatcher) {
        flashcardRepository.byIdsFetchDelay = 2.seconds

        assertShortcutTapDropped(createWarmViewModel())
    }

    @Test
    fun `a shortcut tap whose target is already on top is dropped`() = runTest(mainDispatcherRule.testDispatcher) {
        assertShortcutTapDropped(createWarmViewModel(AppNavigationState(top = TopDestination.CategoryDetails(ANDROID_ID))))
    }

    @Test
    fun `a shortcut tap is dropped when a Study Session starts while it resolves`() = runTest(mainDispatcherRule.testDispatcher) {
        flashcardRepository.byIdsFetchDelay = 0.5.seconds
        val viewModel = createWarmViewModel()

        viewModel.shortcutEvents.test {
            viewModel.onShortcutTap(CATEGORY_ROUTE)
            runCurrent()
            viewModel.onNavigationStateChange(AppNavigationState(top = TopDestination.Other, isStudySessionInBackStack = true))
            advanceUntilIdle()

            expectNoEvents()
        }
    }

    private suspend fun TestScope.assertShortcutTapDropped(viewModel: AppStartViewModel) {
        viewModel.shortcutEvents.test {
            viewModel.onShortcutTap(CATEGORY_ROUTE)
            advanceUntilIdle()

            expectNoEvents()
        }
    }

    // endregion

    private companion object {
        const val ANDROID_ID = "android"
        const val ANDROID_NAME = "Android"
        const val CATEGORY_ROUTE = "/study/category/$ANDROID_ID"
    }
}
