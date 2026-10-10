package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.data.source.AuthRemoteDataSource
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.model.SignInResult.SignedIn
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAuthRepositoryTest {

    private val remoteDataSource: AuthRemoteDataSource = mockk()

    private fun createRepository(): DefaultAuthRepository = DefaultAuthRepository(remoteDataSource)

    @Test
    fun `getCurrentUser delegates to the remote data source`() {
        val user = AuthUser(uid = "uid-1", email = "user@example.com", displayName = "Alex", photoUrl = null)
        every { remoteDataSource.getCurrentUser() } returns user

        val result = createRepository().getCurrentUser()

        result shouldBe user
        verify(exactly = 1) { remoteDataSource.getCurrentUser() }
    }

    @Test
    fun `signInWithGoogleIdToken delegates to the remote data source`() = runTest {
        val idToken = "id-token"
        val user = AuthUser(uid = "uid-1", email = "user@example.com", displayName = "Alex", photoUrl = null)
        coEvery { remoteDataSource.signInWithGoogleIdToken(idToken) } returns SignedIn(user)

        val result = createRepository().signInWithGoogleIdToken(idToken)

        result shouldBe SignedIn(user)
        coVerify(exactly = 1) { remoteDataSource.signInWithGoogleIdToken(idToken) }
    }

    @Test
    fun `signOut delegates to the remote data source`() {
        every { remoteDataSource.signOut() } returns Unit

        createRepository().signOut()

        verify(exactly = 1) { remoteDataSource.signOut() }
    }
}
