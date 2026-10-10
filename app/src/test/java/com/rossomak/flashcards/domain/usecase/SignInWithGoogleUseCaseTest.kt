package com.rossomak.flashcards.domain.usecase

import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.model.SignInFailureReason.Unknown
import com.rossomak.flashcards.core.domain.model.SignInResult.Failed
import com.rossomak.flashcards.core.domain.model.SignInResult.SignedIn
import com.rossomak.flashcards.core.domain.repository.AuthRepository
import com.rossomak.flashcards.core.domain.usecase.SignInWithGoogleUseCase
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SignInWithGoogleUseCaseTest {

    private val authRepository: AuthRepository = mockk()
    private val useCase = SignInWithGoogleUseCase(authRepository)

    @Test
    fun `forwards token to repository and returns the signed in result`() = runTest {
        val token = "token-xyz"
        val signedIn = SignedIn(AuthUser(uid = "u1", email = "a@b.com", displayName = "Alex", photoUrl = null))
        coEvery { authRepository.signInWithGoogleIdToken(token) } returns signedIn

        val result = useCase(token)

        result shouldBe signedIn
        coVerify(exactly = 1) { authRepository.signInWithGoogleIdToken(token) }
    }

    @Test
    fun `forwards failed result unchanged`() = runTest {
        val failed = Failed(Unknown)
        coEvery { authRepository.signInWithGoogleIdToken(any()) } returns failed

        val result = useCase("anything")

        result shouldBe failed
        coVerify(exactly = 1) { authRepository.signInWithGoogleIdToken("anything") }
    }
}
