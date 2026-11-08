package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.AuthUser
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class IsSignedInUseCaseTest {

    private val getCurrentAuthUser: GetCurrentAuthUserUseCase = mockk()
    private val isSignedIn = IsSignedInUseCase(getCurrentAuthUser)

    private val user = AuthUser(uid = "u1", email = "a@b.com", displayName = "Alex", photoUrl = null)

    @Test
    fun `a signed-in User is signed in`() = runTest {
        coEvery { getCurrentAuthUser() } returns user

        isSignedIn() shouldBe true
    }

    @Test
    fun `a Guest is not signed in`() = runTest {
        coEvery { getCurrentAuthUser() } returns user.copy(isAnonymous = true)

        isSignedIn() shouldBe false
    }

    @Test
    fun `no user is not signed in`() = runTest {
        coEvery { getCurrentAuthUser() } returns null

        isSignedIn() shouldBe false
    }
}
