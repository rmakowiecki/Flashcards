package com.rossomak.flashcards.core.data

import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.repository.FakeAuthRepository
import com.rossomak.flashcards.core.domain.repository.FakeXpConfigRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignedInWorkRunnerTest {

    private val authRepository = FakeAuthRepository()
    private val xpConfigRepository = FakeXpConfigRepository()

    private fun TestScope.startRunner() {
        SignedInWorkRunner(backgroundScope, authRepository, xpConfigRepository).start()
    }

    @Test
    fun `a session restored at app start refreshes the XP configuration once`() = runTest(UnconfinedTestDispatcher()) {
        authRepository.userToReturn = USER

        startRunner()

        xpConfigRepository.refreshCallCount shouldBe 1
    }

    @Test
    fun `nothing runs while signed out, and a sign-in refreshes the XP configuration`() = runTest(UnconfinedTestDispatcher()) {
        startRunner()
        xpConfigRepository.refreshCallCount shouldBe 0

        authRepository.userToReturn = USER

        xpConfigRepository.refreshCallCount shouldBe 1
    }

    @Test
    fun `an auth update for the same signed-in user does not refresh again`() = runTest(UnconfinedTestDispatcher()) {
        authRepository.userToReturn = USER
        startRunner()

        authRepository.userToReturn = USER.copy(displayName = "Renamed")

        xpConfigRepository.refreshCallCount shouldBe 1
    }

    @Test
    fun `a Guest linking to a real account keeps the uid and does not refresh again`() = runTest(UnconfinedTestDispatcher()) {
        authRepository.userToReturn = USER.copy(email = null, isAnonymous = true)
        startRunner()

        authRepository.userToReturn = USER

        xpConfigRepository.refreshCallCount shouldBe 1
    }

    @Test
    fun `signing out and back in refreshes again`() = runTest(UnconfinedTestDispatcher()) {
        authRepository.userToReturn = USER
        startRunner()

        authRepository.signOut()
        authRepository.userToReturn = USER

        xpConfigRepository.refreshCallCount shouldBe 2
    }

    private companion object {
        val USER = AuthUser(uid = "uid-1", email = "user@example.com", displayName = "User", photoUrl = null)
    }
}
