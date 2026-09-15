package com.rossomak.flashcards.core.data.worker

import android.content.Context
import android.util.Log
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.rossomak.flashcards.core.data.model.PendingFlashcardResultDto
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionDto
import com.rossomak.flashcards.core.data.model.PendingXpConfigDto
import com.rossomak.flashcards.core.data.source.FakePendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.PendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.SessionSubmissionRemoteDataSource
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Exercises [SessionSubmissionDeliveryWorker.doWork] directly rather than via
 * [androidx.work.testing.TestListenableWorkerBuilder]: that harness needs
 * `ApplicationProvider.getApplicationContext()`, which requires an instrumented or Robolectric
 * environment, and this project's plain-JVM unit tests deliberately run without Robolectric
 * (TESTING.md). `doWork()` itself never touches the mocked `Context`/`WorkerParameters` constructor
 * arguments — only the injected repository and local data source — so direct instantiation exercises
 * the exact same logic.
 */
class SessionSubmissionDeliveryWorkerTest {

    private val sessionSubmissionRemoteDataSource: SessionSubmissionRemoteDataSource = mockk()
    private val localDataSource = FakePendingSessionSubmissionLocalDataSource()

    @Before
    fun setUp() {
        // Drain progress logs via android.util.Log, unavailable outside instrumented/Robolectric
        // tests — stub it rather than pull in either just for this.
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    /** [runAttemptCount] defaults to 0 — WorkManager's own count for "this is the first attempt". */
    private fun createWorker(runAttemptCount: Int = 0): SessionSubmissionDeliveryWorker {
        val workerParameters: WorkerParameters = mockk()
        every { workerParameters.runAttemptCount } returns runAttemptCount
        return SessionSubmissionDeliveryWorker(
            mockk<Context>(),
            workerParameters,
            sessionSubmissionRemoteDataSource,
            localDataSource,
        )
    }

    private fun pendingSubmission(sessionId: String, startedAtEpochMillis: Long): PendingSessionSubmissionDto = PendingSessionSubmissionDto(
        id = sessionId,
        mode = "Rated",
        startedAtEpochMillis = startedAtEpochMillis,
        durationSeconds = 60,
        abandoned = false,
        categoryId = "cat-1",
        categoryName = "Category",
        subcategoryIds = listOf("sub-1"),
        subcategoryNames = listOf("Subcategory"),
        cardResults = listOf(
            PendingFlashcardResultDto(cardId = "card-1", subcategoryId = "sub-1", state = "Mastered", attemptsUsed = 1, wasPreviouslyMastered = false),
        ),
        studyDate = "2026-09-08",
        dailyGoalMinutes = 20,
        studyDateUtcOffsetMinutes = 0,
        xpConfig = PendingXpConfigDto(
            newCardStudied = 10,
            cardMastered = 100,
            cardPartial = 25,
            masteryDefended = 50,
            cardDemastered = -80,
            sessionCompleted = 500,
            dailyGoalMet = 1000,
            streakPerDay = 250,
            streakMaxPerDay = 2500,
            minuteStudied = 10,
            levelCurveBase = 1000.0,
            levelCurveExponent = 2.5,
        ),
    )

    @Test
    fun `doWork submits every pending entry oldest-first by session start time`() = runTest {
        // Seeded out of start-time order on purpose — the drain must sort, not trust append order.
        val second = pendingSubmission("session-2", startedAtEpochMillis = 2_000L)
        val first = pendingSubmission("session-1", startedAtEpochMillis = 1_000L)
        localDataSource.seed(second)
        localDataSource.seed(first)
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any()) } returns kotlin.Result.success(Unit)

        createWorker().doWork()

        coVerifySequence {
            sessionSubmissionRemoteDataSource.submitSession(withArg { it.id shouldBe "session-1" })
            sessionSubmissionRemoteDataSource.submitSession(withArg { it.id shouldBe "session-2" })
        }
    }

    @Test
    fun `doWork clears every entry that was delivered successfully`() = runTest {
        localDataSource.seed(pendingSubmission("session-1", startedAtEpochMillis = 1_000L))
        localDataSource.seed(pendingSubmission("session-2", startedAtEpochMillis = 2_000L))
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any()) } returns kotlin.Result.success(Unit)

        val result = createWorker().doWork()

        result shouldBe Result.success()
        localDataSource.listAll() shouldBe emptyList()
    }

    @Test
    fun `a failure partway through a multi-entry drain leaves the failed and later entries queued`() = runTest {
        val first = pendingSubmission("session-1", startedAtEpochMillis = 1_000L)
        val second = pendingSubmission("session-2", startedAtEpochMillis = 2_000L)
        val third = pendingSubmission("session-3", startedAtEpochMillis = 3_000L)
        localDataSource.seed(first)
        localDataSource.seed(second)
        localDataSource.seed(third)
        coEvery { sessionSubmissionRemoteDataSource.submitSession(match { it.id == "session-1" }) } returns kotlin.Result.success(Unit)
        coEvery { sessionSubmissionRemoteDataSource.submitSession(match { it.id == "session-2" }) } returns
            kotlin.Result.failure(IllegalStateException("network error"))

        val result = createWorker().doWork()

        result shouldBe Result.retry()
        localDataSource.listAll().map { it.id } shouldBe listOf("session-2", "session-3")
        coVerify(exactly = 0) { sessionSubmissionRemoteDataSource.submitSession(match { it.id == "session-3" }) }
    }

    @Test
    fun `a single-entry drain returns retry on failure without clearing the entry`() = runTest {
        val entry = pendingSubmission("session-1", startedAtEpochMillis = 1_000L)
        localDataSource.seed(entry)
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any()) } returns kotlin.Result.failure(IllegalStateException("offline"))

        val result = createWorker().doWork()

        result shouldBe Result.retry()
        localDataSource.listAll() shouldBe listOf(entry)
    }

    @Test
    fun `a single-entry drain clears the pending record on success`() = runTest {
        val entry = pendingSubmission("session-1", startedAtEpochMillis = 1_000L)
        localDataSource.seed(entry)
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any()) } returns kotlin.Result.success(Unit)

        val result = createWorker().doWork()

        result shouldBe Result.success()
        localDataSource.listAll() shouldBe emptyList()
    }

    @Test
    fun `an empty queue succeeds without submitting anything`() = runTest {
        val result = createWorker().doWork()

        result shouldBe Result.success()
        coVerify(exactly = 0) { sessionSubmissionRemoteDataSource.submitSession(any()) }
    }

    @Test
    fun `a failure below the attempt limit still retries without dropping the entry`() = runTest {
        val entry = pendingSubmission("session-1", startedAtEpochMillis = 1_000L)
        localDataSource.seed(entry)
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any()) } returns kotlin.Result.failure(IllegalStateException("offline"))

        // Attempt 4 of 5 (runAttemptCount is 0-indexed) — still under the limit.
        val result = createWorker(runAttemptCount = 3).doWork()

        result shouldBe Result.retry()
        localDataSource.listAll() shouldBe listOf(entry)
    }

    @Test
    fun `a failure at the attempt limit drops the entry and lets the rest of the queue proceed`() = runTest {
        val first = pendingSubmission("session-1", startedAtEpochMillis = 1_000L)
        val second = pendingSubmission("session-2", startedAtEpochMillis = 2_000L)
        localDataSource.seed(first)
        localDataSource.seed(second)
        coEvery { sessionSubmissionRemoteDataSource.submitSession(match { it.id == "session-1" }) } returns
            kotlin.Result.failure(IllegalStateException("permanently rejected"))
        coEvery { sessionSubmissionRemoteDataSource.submitSession(match { it.id == "session-2" }) } returns kotlin.Result.success(Unit)

        // Attempt 5 of 5 (runAttemptCount 4, 0-indexed) — the limit.
        val result = createWorker(runAttemptCount = 4).doWork()

        result shouldBe Result.success()
        localDataSource.listAll() shouldBe emptyList()
        coVerify(exactly = 1) { sessionSubmissionRemoteDataSource.submitSession(match { it.id == "session-2" }) }
    }

    @Test
    fun `a shared-cause failure at the attempt limit drops only the head entry, keeping later entries queued`() = runTest {
        // Every entry fails for the same reason (e.g. backend outage) — only the head entry has
        // actually been retried MAX_DELIVERY_ATTEMPTS times; the rest must stay queued, not get wiped.
        val first = pendingSubmission("session-1", startedAtEpochMillis = 1_000L)
        val second = pendingSubmission("session-2", startedAtEpochMillis = 2_000L)
        val third = pendingSubmission("session-3", startedAtEpochMillis = 3_000L)
        localDataSource.seed(first)
        localDataSource.seed(second)
        localDataSource.seed(third)
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any()) } returns
            kotlin.Result.failure(IllegalStateException("backend unavailable"))

        // Attempt 5 of 5 (runAttemptCount 4, 0-indexed) — the limit.
        val result = createWorker(runAttemptCount = 4).doWork()

        result shouldBe Result.retry()
        localDataSource.listAll().map { it.id } shouldBe listOf("session-2", "session-3")
        coVerify(exactly = 1) { sessionSubmissionRemoteDataSource.submitSession(match { it.id == "session-2" }) }
        coVerify(exactly = 0) { sessionSubmissionRemoteDataSource.submitSession(match { it.id == "session-3" }) }
    }

    @Test
    fun `a remove() that throws IOException retries the drain instead of losing track of the queue`() = runTest {
        // remove() propagates an IOException rather than silently rewriting the queue file as empty
        // (see FilePendingSessionSubmissionLocalDataSource's own doc) — doWork() must retry, not crash
        // the run as Result.failure() and stop being rescheduled by WorkManager's own backoff.
        val unreliableLocalDataSource: PendingSessionSubmissionLocalDataSource = mockk()
        val entry = pendingSubmission("session-1", startedAtEpochMillis = 1_000L)
        coEvery { unreliableLocalDataSource.listAll() } returns listOf(entry)
        coEvery { unreliableLocalDataSource.remove(any()) } throws IOException("queue file unreadable")
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any()) } returns kotlin.Result.success(Unit)
        val workerParameters: WorkerParameters = mockk()
        every { workerParameters.runAttemptCount } returns 0
        val worker = SessionSubmissionDeliveryWorker(
            mockk<Context>(),
            workerParameters,
            sessionSubmissionRemoteDataSource,
            unreliableLocalDataSource,
        )

        val result = worker.doWork()

        result shouldBe Result.retry()
    }

    @Test
    fun `an initial listAll() that throws IOException retries the drain instead of reporting a false success`() = runTest {
        // listAll() now propagates a whole-file IOException (see FilePendingSessionSubmissionLocalDataSource's
        // own doc) instead of swallowing it into emptyList() — doWork() must see this and retry, rather
        // than mistake the failure for a genuinely empty, already-drained queue.
        val unreliableLocalDataSource: PendingSessionSubmissionLocalDataSource = mockk()
        coEvery { unreliableLocalDataSource.listAll() } throws IOException("queue file unreadable")
        val workerParameters: WorkerParameters = mockk()
        every { workerParameters.runAttemptCount } returns 0
        val worker = SessionSubmissionDeliveryWorker(
            mockk<Context>(),
            workerParameters,
            sessionSubmissionRemoteDataSource,
            unreliableLocalDataSource,
        )

        val result = worker.doWork()

        result shouldBe Result.retry()
        coVerify(exactly = 0) { sessionSubmissionRemoteDataSource.submitSession(any()) }
    }

    @Test
    fun `a malformed entry that fails domain conversion is dropped and does not block later entries`() = runTest {
        val malformed = pendingSubmission("session-1", startedAtEpochMillis = 1_000L).copy(mode = "NotARealStudyMode")
        val valid = pendingSubmission("session-2", startedAtEpochMillis = 2_000L)
        localDataSource.seed(malformed)
        localDataSource.seed(valid)
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any()) } returns kotlin.Result.success(Unit)

        val result = createWorker().doWork()

        result shouldBe Result.success()
        localDataSource.listAll() shouldBe emptyList()
        coVerify(exactly = 0) { sessionSubmissionRemoteDataSource.submitSession(match { it.id == "session-1" }) }
        coVerify(exactly = 1) { sessionSubmissionRemoteDataSource.submitSession(match { it.id == "session-2" }) }
    }
}
