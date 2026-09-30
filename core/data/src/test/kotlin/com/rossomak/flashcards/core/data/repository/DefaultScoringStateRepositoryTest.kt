package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.data.model.ScoringStateDto
import com.rossomak.flashcards.core.data.source.CardProgressRemoteDataSource
import com.rossomak.flashcards.core.data.source.FakePendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.ScoringStateRemoteDataSource
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.repository.FakeAuthRepository
import com.rossomak.flashcards.core.domain.repository.FakeXpConfigRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultScoringStateRepositoryTest {

    private val remoteDataSource: ScoringStateRemoteDataSource = mockk()
    private val cardProgressRemoteDataSource: CardProgressRemoteDataSource = mockk()
    private val authRepository = FakeAuthRepository().apply { userToReturn = authUser(USER_ID) }
    private val pendingSessionQueue = FakePendingSessionSubmissionLocalDataSource()
    private val xpConfigRepository = FakeXpConfigRepository()

    private fun createRepository(): DefaultScoringStateRepository = DefaultScoringStateRepository(
        PendingSessionProjector(authRepository, pendingSessionQueue, cardProgressRemoteDataSource, remoteDataSource, xpConfigRepository),
    )

    @Test
    fun `getScoringState maps the dto to domain`() = runTest {
        val xp = 620L
        val level = 2
        val xpIntoCurrentLevel = 100L
        val currentStreak = 3
        val bestStreak = 5
        val lastStudyDate = "2026-09-06"
        val goalMetDate = "2026-09-06"
        val studiedSecondsOnLastStudyDate = 1500L
        val dto = ScoringStateDto(
            xp = xp,
            level = level,
            xpIntoCurrentLevel = xpIntoCurrentLevel,
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            lastStudyDate = lastStudyDate,
            goalMetDate = goalMetDate,
            studiedSecondsOnLastStudyDate = studiedSecondsOnLastStudyDate,
        )
        coEvery { remoteDataSource.getScoringState() } returns dto

        val result = createRepository().getScoringState()

        result.isSuccess shouldBe true
        val state = result.getOrThrow()
        state?.xp shouldBe xp
        state?.level shouldBe level
        state?.xpIntoCurrentLevel shouldBe xpIntoCurrentLevel
        state?.currentStreak shouldBe currentStreak
        state?.bestStreak shouldBe bestStreak
        state?.lastStudyDate shouldBe lastStudyDate
        state?.goalMetDate shouldBe goalMetDate
        state?.studiedSecondsOnLastStudyDate shouldBe studiedSecondsOnLastStudyDate
        coVerify(exactly = 1) { remoteDataSource.getScoringState() }
    }

    @Test
    fun `getScoringState returns success with null for an absent document`() = runTest {
        coEvery { remoteDataSource.getScoringState() } returns null

        val result = createRepository().getScoringState()

        result.isSuccess shouldBe true
        result.getOrThrow() shouldBe null
        coVerify(exactly = 1) { remoteDataSource.getScoringState() }
    }

    @Test
    fun `getScoringState wraps a data source failure in a failure result`() = runTest {
        val error = IllegalStateException("firestore down")
        coEvery { remoteDataSource.getScoringState() } throws error

        val result = createRepository().getScoringState()

        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe error
        coVerify(exactly = 1) { remoteDataSource.getScoringState() }
    }

    @Test
    fun `getScoringState rethrows cancellation instead of wrapping it`() = runTest {
        coEvery { remoteDataSource.getScoringState() } throws CancellationException("cancelled")

        val thrown = runCatching { createRepository().getScoringState() }.exceptionOrNull()

        (thrown is CancellationException) shouldBe true
        coVerify(exactly = 1) { remoteDataSource.getScoringState() }
    }

    private fun authUser(uid: String) = AuthUser(uid = uid, email = null, displayName = null, photoUrl = null)

    private companion object {
        const val USER_ID = "user-1"
    }
}
