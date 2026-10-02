package com.rossomak.flashcards.core.data.repository

import androidx.work.Data
import androidx.work.WorkInfo
import com.rossomak.flashcards.core.data.SessionSubmissionDrainScheduler
import com.rossomak.flashcards.core.data.mapper.toDto
import com.rossomak.flashcards.core.data.model.DeliveredSessionDto
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionDto
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionMapper.toDto
import com.rossomak.flashcards.core.data.network.NetworkAvailability
import com.rossomak.flashcards.core.data.source.FakePendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.PendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.worker.SessionDeliveryReport
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus.InFlight
import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus.NotDelivered
import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus.Rejected
import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus.Scored
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SessionScore
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import com.rossomak.flashcards.core.domain.model.XpBreakdown
import com.rossomak.flashcards.core.domain.repository.FakeAuthRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DefaultSessionSubmissionRepositoryTest {

    private val localDataSource = FakePendingSessionSubmissionLocalDataSource()
    private val drainScheduler: SessionSubmissionDrainScheduler = mockk()
    private val authRepository = FakeAuthRepository().apply { userToReturn = SIGNED_IN_USER }
    private var internetAvailable = true
    private val drainWorkInfo = MutableStateFlow<WorkInfo?>(workInfo(WorkInfo.State.ENQUEUED))

    private fun createRepository(
        pendingLocalDataSource: PendingSessionSubmissionLocalDataSource = localDataSource,
    ): DefaultSessionSubmissionRepository =
        DefaultSessionSubmissionRepository(pendingLocalDataSource, drainScheduler, authRepository, NetworkAvailability { internetAvailable })

    @Before
    fun setUp() {
        every { drainScheduler.scheduleDrainForFinishedSession() } returns REQUEST_ID
        every { drainScheduler.observeDrain(REQUEST_ID) } returns drainWorkInfo
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
    )

    @Test
    fun `submitSession appends the session stamped with the signed-in uid and schedules a replacing drain`() = runTest {
        val session = sessionResult()

        createRepository().submitSession(session)

        localDataSource.listAll() shouldBe listOf(session.toDto(SIGNED_IN_USER.uid))
        verify(exactly = 1) { drainScheduler.scheduleDrainForFinishedSession() }
    }

    @Test
    fun `a progress report with this session's score emits InFlight then Scored`() = runTest {
        drainWorkInfo.value = workInfo(WorkInfo.State.RUNNING, progress = report(DeliveredSessionDto.Scored(SCORE.toDto())))

        val statuses = createRepository().submitSession(sessionResult()).toList()

        statuses shouldBe listOf(InFlight, Scored(SCORE))
    }

    @Test
    fun `an output report with this session's rejection emits Rejected`() = runTest {
        drainWorkInfo.value = workInfo(WorkInfo.State.SUCCEEDED, output = report(DeliveredSessionDto.Rejected))

        val statuses = createRepository().submitSession(sessionResult()).toList()

        statuses shouldBe listOf(InFlight, Rejected)
    }

    @Test
    fun `a finished run without this session emits NotDelivered`() = runTest {
        drainWorkInfo.value = workInfo(WorkInfo.State.SUCCEEDED)

        val statuses = createRepository().submitSession(sessionResult()).toList()

        statuses shouldBe listOf(InFlight, NotDelivered)
    }

    @Test
    fun `a run back in the queue after an attempt emits NotDelivered`() = runTest {
        drainWorkInfo.value = workInfo(WorkInfo.State.ENQUEUED, runAttemptCount = 1)

        val statuses = createRepository().submitSession(sessionResult()).toList()

        statuses shouldBe listOf(InFlight, NotDelivered)
    }

    @Test
    fun `no internet at submit time emits only NotDelivered, with the session still queued and the drain scheduled`() = runTest {
        internetAvailable = false

        val statuses = createRepository().submitSession(sessionResult()).toList()

        statuses shouldBe listOf(NotDelivered)
        localDataSource.listAll().size shouldBe 1
        verify(exactly = 1) { drainScheduler.scheduleDrainForFinishedSession() }
        verify(exactly = 0) { drainScheduler.observeDrain(any()) }
    }

    @Test
    fun `with nobody signed in submitSession emits NotDelivered without queuing or scheduling`() = runTest {
        authRepository.userToReturn = null

        val statuses = createRepository().submitSession(sessionResult()).toList()

        statuses shouldBe listOf(NotDelivered)
        localDataSource.listAll() shouldBe emptyList()
        verify(exactly = 0) { drainScheduler.scheduleDrainForFinishedSession() }
    }

    @Test
    fun `a local append failure is logged and emits NotDelivered, never thrown`() = runTest {
        val statuses = createRepository(ThrowingPendingSessionSubmissionLocalDataSource()).submitSession(sessionResult()).toList()

        statuses shouldBe listOf(NotDelivered)
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

    private fun report(deliveredSession: DeliveredSessionDto): Data = SessionDeliveryReport.toData(mapOf(SESSION_ID to deliveredSession))

    private companion object {
        const val SESSION_ID = "session-1"
        val REQUEST_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val SIGNED_IN_USER = AuthUser(uid = "uid-1", email = "user@example.com", displayName = "User", photoUrl = null)
        val SCORE = SessionScore(
            breakdown = XpBreakdown(newCards = 10, mastered = 100, streakBonus = 250),
            level = 1,
            xpIntoCurrentLevel = 360,
            xpForNextLevel = 1000,
            levelsCrossed = emptyList(),
            counts = null,
            rates = null,
        )

        fun workInfo(
            state: WorkInfo.State,
            output: Data = Data.EMPTY,
            progress: Data = Data.EMPTY,
            runAttemptCount: Int = 0,
        ): WorkInfo = WorkInfo(
            id = REQUEST_ID,
            state = state,
            tags = emptySet(),
            outputData = output,
            progress = progress,
            runAttemptCount = runAttemptCount,
        )
    }
}

private class ThrowingPendingSessionSubmissionLocalDataSource : PendingSessionSubmissionLocalDataSource {

    override suspend fun append(pendingSessionSubmission: PendingSessionSubmissionDto): Unit = error("disk full")

    override suspend fun listAll(): List<PendingSessionSubmissionDto> = emptyList()

    override suspend fun remove(sessionId: String) = Unit

    override fun observeAll(): Flow<List<PendingSessionSubmissionDto>> = flowOf(emptyList())
}

private class CancellingPendingSessionSubmissionLocalDataSource : PendingSessionSubmissionLocalDataSource {

    override suspend fun append(pendingSessionSubmission: PendingSessionSubmissionDto): Unit =
        throw CancellationException("session scope cancelled")

    override suspend fun listAll(): List<PendingSessionSubmissionDto> = emptyList()

    override suspend fun remove(sessionId: String) = Unit

    override fun observeAll(): Flow<List<PendingSessionSubmissionDto>> = flowOf(emptyList())
}
