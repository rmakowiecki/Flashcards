package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.data.SessionSubmissionDrainScheduler
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionDto
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionMapper.toDto
import com.rossomak.flashcards.core.data.source.FakePendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.PendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import com.rossomak.flashcards.core.domain.repository.FakeAuthRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DefaultSessionSubmissionRepositoryTest {

    private val localDataSource = FakePendingSessionSubmissionLocalDataSource()
    private val drainScheduler: SessionSubmissionDrainScheduler = mockk()
    private val authRepository = FakeAuthRepository().apply { userToReturn = SIGNED_IN_USER }

    private fun createRepository(
        pendingLocalDataSource: PendingSessionSubmissionLocalDataSource = localDataSource,
    ): DefaultSessionSubmissionRepository = DefaultSessionSubmissionRepository(pendingLocalDataSource, drainScheduler, authRepository)

    @Before
    fun setUp() {
        every { drainScheduler.scheduleDrainForFinishedSession() } returns Unit
    }

    private fun sessionResult(): SessionResult.Rated = SessionResult.Rated(
        id = SESSION_ID,
        startedAt = Instant.parse("2026-09-08T10:00:00Z"),
        durationSeconds = 60,
        abandoned = false,
        categoryId = "cat-1",
        categoryName = "Category",
        subcategoryIds = listOf("sub-1"),
        subcategoryNames = listOf("Subcategory"),
        sourceType = SingleSubcategory,
        cardResults = listOf(
            FlashcardResult.Rated(
                cardId = "card-1",
                subcategoryId = "sub-1",
                state = FlashcardStudyProgressState.Mastered,
                attemptsUsed = 1,
                wasPreviouslyMastered = false,
            ),
        ),
        studyDate = "2026-09-08",
        dailyGoalMinutes = 20,
        studyDateUtcOffsetMinutes = 0,
        voiceAnsweringEnabled = false,
    )

    @Test
    fun `submitSession appends the session stamped with the signed-in uid, schedules a replacing drain and succeeds`() = runTest {
        val session = sessionResult()

        val result = createRepository().submitSession(session)

        result.isSuccess shouldBe true
        localDataSource.listAll() shouldBe listOf(session.toDto(SIGNED_IN_USER.uid))
        verify(exactly = 1) { drainScheduler.scheduleDrainForFinishedSession() }
    }

    @Test
    fun `with nobody signed in submitSession fails without queuing or scheduling`() = runTest {
        authRepository.userToReturn = null

        val result = createRepository().submitSession(sessionResult())

        result.isFailure shouldBe true
        localDataSource.listAll() shouldBe emptyList()
        verify(exactly = 0) { drainScheduler.scheduleDrainForFinishedSession() }
    }

    @Test
    fun `a local append failure is logged and returned as a failure, never thrown`() = runTest {
        val result = createRepository(ThrowingPendingSessionSubmissionLocalDataSource()).submitSession(sessionResult())

        result.isFailure shouldBe true
        verify(exactly = 0) { drainScheduler.scheduleDrainForFinishedSession() }
    }

    @Test
    fun `a cancellation during local append is rethrown`() = runTest {
        shouldThrow<CancellationException> { createRepository(CancellingPendingSessionSubmissionLocalDataSource()).submitSession(sessionResult()) }
        verify(exactly = 0) { drainScheduler.scheduleDrainForFinishedSession() }
    }

    @Test
    fun `submitting the same session again queues it once`() = runTest {
        val repository = createRepository()

        repository.submitSession(sessionResult())
        repository.submitSession(sessionResult())

        localDataSource.listAll().size shouldBe 1
    }

    private companion object {
        const val SESSION_ID = "session-1"
        val SIGNED_IN_USER = AuthUser(uid = "uid-1", email = "user@example.com", displayName = "User", photoUrl = null)
    }
}

private class ThrowingPendingSessionSubmissionLocalDataSource : PendingSessionSubmissionLocalDataSource {

    override suspend fun append(pendingSessionSubmission: PendingSessionSubmissionDto): Unit = error("disk full")

    override suspend fun listAll(): List<PendingSessionSubmissionDto> = emptyList()

    override suspend fun remove(sessionId: String) = Unit

    override suspend fun removeAllForUser(uid: String) = Unit

    override fun observeAll(): Flow<List<PendingSessionSubmissionDto>> = flowOf(emptyList())
}

private class CancellingPendingSessionSubmissionLocalDataSource : PendingSessionSubmissionLocalDataSource {

    override suspend fun append(pendingSessionSubmission: PendingSessionSubmissionDto): Unit =
        throw CancellationException("session scope cancelled")

    override suspend fun listAll(): List<PendingSessionSubmissionDto> = emptyList()

    override suspend fun remove(sessionId: String) = Unit

    override suspend fun removeAllForUser(uid: String) = Unit

    override fun observeAll(): Flow<List<PendingSessionSubmissionDto>> = flowOf(emptyList())
}
