package com.rossomak.flashcards.presentation.startup

import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.usecase.ClearDynamicShortcutsUseCase
import com.rossomak.flashcards.core.domain.usecase.GetCurrentAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.ObserveAuthUserUseCase
import com.rossomak.flashcards.core.domain.usecase.SyncDynamicShortcutsUseCase
import com.rossomak.flashcards.core.domain.usecase.SyncFlashcardCacheGenerationUseCase
import com.rossomak.flashcards.testutil.MainDispatcherRule
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
        every { this@mockk() } returns authUserFlow
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

    private val testUser = AuthUser("u1", "a@b.com", "Alex", null)

    private fun createViewModel(): AppStartViewModel = AppStartViewModel(
        getCurrentAuthUserUseCase,
        observeAuthUserUseCase,
        syncFlashcardCacheGenerationUseCase,
        syncDynamicShortcutsUseCase,
        clearDynamicShortcutsUseCase,
    )

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
}
