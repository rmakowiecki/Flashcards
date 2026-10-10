package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.AuthProvider.GitHub
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.model.SignInFailureReason.Unknown
import com.rossomak.flashcards.core.domain.model.SignInResult
import com.rossomak.flashcards.core.domain.model.SignInResult.Cancelled
import com.rossomak.flashcards.core.domain.model.SignInResult.Failed
import com.rossomak.flashcards.core.domain.model.SignInResult.SignedIn
import com.rossomak.flashcards.core.domain.repository.AuthRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SignInWithGitHubUseCaseTest {

    private val authRepository: AuthRepository = mockk()
    private val useCase = SignInWithGitHubUseCase(authRepository)

    @Test
    fun `forwards the signed in result unchanged`() = runTest {
        val signedIn = SignedIn(
            AuthUser(uid = "u1", email = "a@b.com", displayName = "Alex", photoUrl = null, provider = GitHub),
        )

        assertForwarded(signedIn)
    }

    @Test
    fun `forwards the cancelled result unchanged`() = runTest {
        assertForwarded(Cancelled)
    }

    @Test
    fun `forwards the failed result unchanged`() = runTest {
        assertForwarded(Failed(Unknown))
    }

    private suspend fun assertForwarded(expected: SignInResult) {
        coEvery { authRepository.signInWithGitHub() } returns expected

        val result = useCase()

        result shouldBe expected
        coVerify(exactly = 1) { authRepository.signInWithGitHub() }
    }
}
