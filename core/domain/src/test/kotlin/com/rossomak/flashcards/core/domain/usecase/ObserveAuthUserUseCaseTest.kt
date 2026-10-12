package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.repository.AuthRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveAuthUserUseCaseTest {

    // A shared flow with no replay stands in for the listener, which reports nothing until later.
    private val listenerUpdates = MutableSharedFlow<AuthUser?>()
    private val authRepository: AuthRepository = mockk()

    private fun createUseCase(currentUser: AuthUser?): ObserveAuthUserUseCase {
        every { authRepository.getCurrentUser() } returns currentUser
        every { authRepository.observeAuthUser() } returns listenerUpdates
        return ObserveAuthUserUseCase(authRepository)
    }

    @Test
    fun `the current user comes first, before the listener reports anything`() = runTest {
        val useCase = createUseCase(currentUser = USER)

        useCase().first() shouldBe USER
    }

    @Test
    fun `no current user comes first as null`() = runTest {
        val useCase = createUseCase(currentUser = null)

        useCase().first() shouldBe null
    }

    @Test
    fun `the listener repeating the current user is dropped and later changes pass through`() = runTest {
        val useCase = createUseCase(currentUser = USER)
        val emissions = mutableListOf<AuthUser?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase().toList(emissions) }

        listenerUpdates.emit(USER)
        listenerUpdates.emit(OTHER_USER)
        listenerUpdates.emit(null)

        emissions shouldBe listOf(USER, OTHER_USER, null)
    }

    private companion object {
        val USER = AuthUser(uid = "uid-1", email = "alex@example.com", displayName = "Alex", photoUrl = null)
        val OTHER_USER = USER.copy(uid = "uid-2")
    }
}
