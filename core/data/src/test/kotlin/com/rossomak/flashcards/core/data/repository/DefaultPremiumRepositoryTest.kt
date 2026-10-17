package com.rossomak.flashcards.core.data.repository

import app.cash.turbine.test
import com.rossomak.flashcards.core.data.source.PremiumRemoteDataSource
import com.rossomak.flashcards.core.domain.model.AuthProvider
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.repository.FakeAuthRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultPremiumRepositoryTest {

    private val authRepository = FakeAuthRepository().apply { userToReturn = SIGNED_IN_USER }
    private val remotePremium = MutableStateFlow(true)
    private val remoteDataSource: PremiumRemoteDataSource = mockk {
        every { observePremium(any()) } returns remotePremium
    }

    private fun createRepository() = DefaultPremiumRepository(authRepository, remoteDataSource)

    @Test
    fun `observePremium is unknown until the record arrives, then follows it`() = runTest {
        createRepository().observePremium().test {
            awaitItem() shouldBe null
            awaitItem() shouldBe true

            remotePremium.value = false

            awaitItem() shouldBe false
        }
        verify { remoteDataSource.observePremium(USER_ID) }
    }

    @Test
    fun `observePremium reads a Guest as not Premium without a listener`() = runTest {
        authRepository.userToReturn = GUEST

        createRepository().observePremium().test {
            awaitItem() shouldBe false
        }
        verify(exactly = 0) { remoteDataSource.observePremium(any()) }
    }

    @Test
    fun `observePremium reads signed out as not Premium`() = runTest {
        authRepository.userToReturn = null

        createRepository().observePremium().test {
            awaitItem() shouldBe false
        }
    }

    @Test
    fun `observePremium follows the new User after a change of User`() = runTest {
        every { remoteDataSource.observePremium(OTHER_USER_ID) } returns MutableStateFlow(false)

        createRepository().observePremium().test {
            awaitItem() shouldBe null
            awaitItem() shouldBe true

            authRepository.userToReturn = SIGNED_IN_USER.copy(uid = OTHER_USER_ID)

            awaitItem() shouldBe null
            awaitItem() shouldBe false
        }
    }

    @Test
    fun `observePremium falls back to unknown when the listener fails`() = runTest {
        every { remoteDataSource.observePremium(USER_ID) } returns flow { throw IllegalStateException("listener failed") }

        createRepository().observePremium().test {
            awaitItem() shouldBe null
            expectNoEvents()
        }
    }

    @Test
    fun `fetchPremium returns the server's answer`() = runTest {
        coEvery { remoteDataSource.fetchPremium(USER_ID) } returns false

        createRepository().fetchPremium() shouldBe Result.success(false)
    }

    @Test
    fun `fetchPremium fails when the server can't be reached`() = runTest {
        val error = IOException("offline")
        coEvery { remoteDataSource.fetchPremium(USER_ID) } throws error

        createRepository().fetchPremium().exceptionOrNull() shouldBe error
    }

    @Test
    fun `fetchPremium reads a Guest as not Premium without a server read`() = runTest {
        authRepository.userToReturn = GUEST

        createRepository().fetchPremium() shouldBe Result.success(false)
        coVerify(exactly = 0) { remoteDataSource.fetchPremium(any()) }
    }

    private companion object {
        const val USER_ID = "user-1"
        const val OTHER_USER_ID = "user-2"
        val SIGNED_IN_USER = AuthUser(uid = USER_ID, email = null, displayName = null, photoUrl = null, provider = AuthProvider.Google)
        val GUEST = AuthUser(uid = USER_ID, email = null, displayName = null, photoUrl = null, isAnonymous = true)
    }
}
