package com.rossomak.flashcards.core.data.worker

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctionsException
import com.rossomak.flashcards.core.data.model.PendingFlashcardResultDto
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionDto
import com.rossomak.flashcards.core.data.model.PendingXpConfigDto
import com.rossomak.flashcards.core.data.source.CardProgressRemoteDataSource
import com.rossomak.flashcards.core.data.source.FakeDeadLetteredSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.FakePendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.PendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.ScoringStateRemoteDataSource
import com.rossomak.flashcards.core.data.source.SessionSubmissionRemoteDataSource
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.repository.FakeAuthRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Exercises [SessionSubmissionDeliveryWorker.doWork] directly rather than via
 * [androidx.work.testing.TestListenableWorkerBuilder]: that harness needs
 * `ApplicationProvider.getApplicationContext()`, which requires an instrumented or Robolectric
 * environment, and this project's plain-JVM unit tests deliberately run without Robolectric
 * (TESTING.md). `doWork()` itself never touches the mocked `Context`/`WorkerParameters` constructor
 * arguments — only the injected collaborators — so direct instantiation exercises the exact same logic.
 */
class SessionSubmissionDeliveryWorkerTest {

    private val sessionSubmissionRemoteDataSource: SessionSubmissionRemoteDataSource = mockk()
    private val localDataSource = FakePendingSessionSubmissionLocalDataSource()
    private val deadLetterLocalDataSource = FakeDeadLetteredSessionSubmissionLocalDataSource()
    private val scoringStateRemoteDataSource: ScoringStateRemoteDataSource = mockk()
    private val cardProgressRemoteDataSource: CardProgressRemoteDataSource = mockk()
    private val authRepository = FakeAuthRepository()

    @Before
    fun setUp() {
        authRepository.userToReturn = SIGNED_IN_USER
        coEvery { scoringStateRemoteDataSource.getScoringState(any()) } returns null
        coEvery { cardProgressRemoteDataSource.getProgress(any(), any()) } returns null
    }

    /** [runAttemptCount] defaults to 0 — WorkManager's own count for "this is the first attempt". */
    private fun createWorker(
        runAttemptCount: Int = 0,
        pendingLocalDataSource: PendingSessionSubmissionLocalDataSource = localDataSource,
    ): SessionSubmissionDeliveryWorker {
        val workerParameters: WorkerParameters = mockk()
        every { workerParameters.runAttemptCount } returns runAttemptCount
        return SessionSubmissionDeliveryWorker(
            mockk<Context>(),
            workerParameters,
            sessionSubmissionRemoteDataSource,
            pendingLocalDataSource,
            deadLetterLocalDataSource,
            SessionServerStateRefresher(scoringStateRemoteDataSource, cardProgressRemoteDataSource),
            authRepository,
        )
    }

    private fun pendingSubmission(
        sessionId: String,
        startedAtEpochMillis: Long,
        uid: String = SIGNED_IN_UID,
        subcategoryIds: List<String> = listOf(SUBCATEGORY_ID),
    ): PendingSessionSubmissionDto = PendingSessionSubmissionDto(
        id = sessionId,
        uid = uid,
        mode = "Rated",
        startedAtEpochMillis = startedAtEpochMillis,
        durationSeconds = 60,
        abandoned = false,
        categoryId = "cat-1",
        categoryName = "Category",
        subcategoryIds = subcategoryIds,
        subcategoryNames = subcategoryIds.map { "Name of $it" },
        cardResults = listOf(
            PendingFlashcardResultDto(cardId = "card-1", subcategoryId = SUBCATEGORY_ID, state = "Mastered", attemptsUsed = 1, wasPreviouslyMastered = false),
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

    private fun functionsException(code: FirebaseFunctionsException.Code): FirebaseFunctionsException {
        val exception: FirebaseFunctionsException = mockk()
        every { exception.code } returns code
        every { exception.message } returns "rejected with $code"
        return exception
    }

    @Test
    fun `doWork submits every pending entry oldest-first by session start time`() = runTest {
        // Seeded out of start-time order on purpose — the drain must sort, not trust append order.
        localDataSource.seed(pendingSubmission("session-2", startedAtEpochMillis = 2_000L))
        localDataSource.seed(pendingSubmission("session-1", startedAtEpochMillis = 1_000L))
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
    fun `an empty queue succeeds without submitting anything`() = runTest {
        val result = createWorker().doWork()

        result shouldBe Result.success()
        coVerify(exactly = 0) { sessionSubmissionRemoteDataSource.submitSession(any()) }
    }

    @Test
    fun `only the signed-in User's entries are delivered, foreign entries stay queued and the run succeeds`() = runTest {
        val own = pendingSubmission("session-own", startedAtEpochMillis = 2_000L)
        val foreign = pendingSubmission("session-foreign", startedAtEpochMillis = 1_000L, uid = OTHER_UID)
        localDataSource.seed(foreign)
        localDataSource.seed(own)
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any()) } returns kotlin.Result.success(Unit)

        val result = createWorker().doWork()

        result shouldBe Result.success()
        localDataSource.listAll() shouldBe listOf(foreign)
        coVerify(exactly = 1) { sessionSubmissionRemoteDataSource.submitSession(match { it.id == "session-own" }) }
        coVerify(exactly = 0) { sessionSubmissionRemoteDataSource.submitSession(match { it.id == "session-foreign" }) }
    }

    @Test
    fun `a run with only foreign entries succeeds without delivering anything`() = runTest {
        val foreign = pendingSubmission("session-foreign", startedAtEpochMillis = 1_000L, uid = OTHER_UID)
        localDataSource.seed(foreign)

        val result = createWorker().doWork()

        result shouldBe Result.success()
        localDataSource.listAll() shouldBe listOf(foreign)
        coVerify(exactly = 0) { sessionSubmissionRemoteDataSource.submitSession(any()) }
    }

    @Test
    fun `a User switch mid-drain stops before the next submission and returns retry`() = runTest {
        localDataSource.seed(pendingSubmission("session-1", startedAtEpochMillis = 1_000L))
        localDataSource.seed(pendingSubmission("session-2", startedAtEpochMillis = 2_000L))
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any()) } coAnswers {
            authRepository.userToReturn = SIGNED_IN_USER.copy(uid = OTHER_UID)
            kotlin.Result.success(Unit)
        }

        val result = createWorker().doWork()

        result shouldBe Result.retry()
        localDataSource.listAll().map { it.id } shouldBe listOf("session-2")
        coVerify(exactly = 0) { sessionSubmissionRemoteDataSource.submitSession(match { it.id == "session-2" }) }
    }

    @Test
    fun `with nobody signed in the run succeeds without delivering anything`() = runTest {
        authRepository.userToReturn = null
        val entry = pendingSubmission("session-1", startedAtEpochMillis = 1_000L)
        localDataSource.seed(entry)

        val result = createWorker().doWork()

        result shouldBe Result.success()
        localDataSource.listAll() shouldBe listOf(entry)
        coVerify(exactly = 0) { sessionSubmissionRemoteDataSource.submitSession(any()) }
    }

    @Test
    fun `an entry without an owning uid is removed as malformed and does not block later entries`() = runTest {
        localDataSource.seed(pendingSubmission("session-1", startedAtEpochMillis = 1_000L, uid = ""))
        localDataSource.seed(pendingSubmission("session-2", startedAtEpochMillis = 2_000L))
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any()) } returns kotlin.Result.success(Unit)

        val result = createWorker().doWork()

        result shouldBe Result.success()
        localDataSource.listAll() shouldBe emptyList()
        coVerify(exactly = 0) { sessionSubmissionRemoteDataSource.submitSession(match { it.id == "session-1" }) }
        coVerify(exactly = 1) { sessionSubmissionRemoteDataSource.submitSession(match { it.id == "session-2" }) }
    }

    @Test
    fun `a malformed entry that fails domain conversion is dropped and does not block later entries`() = runTest {
        localDataSource.seed(pendingSubmission("session-1", startedAtEpochMillis = 1_000L).copy(mode = "NotARealStudyMode"))
        localDataSource.seed(pendingSubmission("session-2", startedAtEpochMillis = 2_000L))
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any()) } returns kotlin.Result.success(Unit)

        val result = createWorker().doWork()

        result shouldBe Result.success()
        localDataSource.listAll() shouldBe emptyList()
        coVerify(exactly = 0) { sessionSubmissionRemoteDataSource.submitSession(match { it.id == "session-1" }) }
        coVerify(exactly = 1) { sessionSubmissionRemoteDataSource.submitSession(match { it.id == "session-2" }) }
    }

    @Test
    fun `each permanent code dead-letters the entry and the next entry is still delivered in the same run`() = runTest {
        PERMANENT_CODES.forEach { code ->
            val rejected = pendingSubmission("session-rejected-$code", startedAtEpochMillis = 1_000L)
            localDataSource.seed(rejected)
            localDataSource.seed(pendingSubmission("session-next-$code", startedAtEpochMillis = 2_000L))
            coEvery { sessionSubmissionRemoteDataSource.submitSession(match { it.id == rejected.id }) } returns
                kotlin.Result.failure(functionsException(code))
            coEvery { sessionSubmissionRemoteDataSource.submitSession(match { it.id == "session-next-$code" }) } returns kotlin.Result.success(Unit)

            val result = createWorker().doWork()

            result shouldBe Result.success()
            localDataSource.listAll() shouldBe emptyList()
            coVerify(exactly = 1) { sessionSubmissionRemoteDataSource.submitSession(match { it.id == "session-next-$code" }) }
            val deadLettered = deadLetterLocalDataSource.listAll().last()
            deadLettered.entry shouldBe rejected
            deadLettered.failureCode shouldBe code.name
            deadLettered.failureMessage shouldBe "rejected with $code"
        }
        deadLetterLocalDataSource.listAll().size shouldBe PERMANENT_CODES.size
    }

    @Test
    fun `each transient code returns retry and keeps the entry and every later one queued`() = runTest {
        TRANSIENT_CODES.forEach { code ->
            val entries = listOf(
                pendingSubmission("session-1", startedAtEpochMillis = 1_000L),
                pendingSubmission("session-2", startedAtEpochMillis = 2_000L),
            )
            entries.forEach { localDataSource.seed(it) }
            coEvery { sessionSubmissionRemoteDataSource.submitSession(any()) } returns kotlin.Result.failure(functionsException(code))

            val result = createWorker().doWork()

            result shouldBe Result.retry()
            localDataSource.listAll() shouldBe entries
            entries.forEach { localDataSource.remove(it.id) }
        }
        deadLetterLocalDataSource.listAll() shouldBe emptyList()
        coVerify(exactly = 0) { sessionSubmissionRemoteDataSource.submitSession(match { it.id == "session-2" }) }
    }

    @Test
    fun `a non-Functions exception returns retry and keeps the entry`() = runTest {
        val entry = pendingSubmission("session-1", startedAtEpochMillis = 1_000L)
        localDataSource.seed(entry)
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any()) } returns kotlin.Result.failure(IOException("offline"))

        val result = createWorker().doWork()

        result shouldBe Result.retry()
        localDataSource.listAll() shouldBe listOf(entry)
        deadLetterLocalDataSource.listAll() shouldBe emptyList()
    }

    @Test
    fun `a transient failure on a high run attempt count still returns retry and keeps the entry`() = runTest {
        val entry = pendingSubmission("session-1", startedAtEpochMillis = 1_000L)
        localDataSource.seed(entry)
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any()) } returns
            kotlin.Result.failure(functionsException(FirebaseFunctionsException.Code.UNAVAILABLE))

        val result = createWorker(runAttemptCount = 1_000).doWork()

        result shouldBe Result.retry()
        localDataSource.listAll() shouldBe listOf(entry)
        deadLetterLocalDataSource.listAll() shouldBe emptyList()
    }

    @Test
    fun `a successful delivery refreshes the scoring state and each touched Subcategory's Card Progress from the server`() = runTest {
        localDataSource.seed(pendingSubmission("session-1", startedAtEpochMillis = 1_000L, subcategoryIds = listOf("sub-1", "sub-2")))
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any()) } returns kotlin.Result.success(Unit)

        createWorker().doWork()

        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState(Source.SERVER) }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress("sub-1", Source.SERVER) }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress("sub-2", Source.SERVER) }
    }

    @Test
    fun `a delivered entry is removed even when the post-delivery refresh fails`() = runTest {
        localDataSource.seed(pendingSubmission("session-1", startedAtEpochMillis = 1_000L, subcategoryIds = listOf("sub-1", "sub-2")))
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any()) } returns kotlin.Result.success(Unit)
        coEvery { scoringStateRemoteDataSource.getScoringState(any()) } throws IllegalStateException("offline")
        coEvery { cardProgressRemoteDataSource.getProgress("sub-1", any()) } throws IllegalStateException("offline")

        val result = createWorker().doWork()

        result shouldBe Result.success()
        localDataSource.listAll() shouldBe emptyList()
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress("sub-2", Source.SERVER) }
    }

    @Test
    fun `a failed delivery does not refresh anything`() = runTest {
        localDataSource.seed(pendingSubmission("session-1", startedAtEpochMillis = 1_000L))
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any()) } returns kotlin.Result.failure(IOException("offline"))

        createWorker().doWork()

        coVerify(exactly = 0) { scoringStateRemoteDataSource.getScoringState(any()) }
        coVerify(exactly = 0) { cardProgressRemoteDataSource.getProgress(any(), any()) }
    }

    @Test
    fun `a remove() that throws IOException retries the drain instead of losing track of the queue`() = runTest {
        // remove() propagates an IOException rather than silently rewriting the queue file as empty
        // (see FilePendingSessionSubmissionLocalDataSource's own doc) — doWork() must retry, not crash
        // the run as Result.failure() and stop being rescheduled by WorkManager's own backoff.
        val unreliableLocalDataSource: PendingSessionSubmissionLocalDataSource = mockk()
        coEvery { unreliableLocalDataSource.listAll() } returns listOf(pendingSubmission("session-1", startedAtEpochMillis = 1_000L))
        coEvery { unreliableLocalDataSource.remove(any()) } throws IOException("queue file unreadable")
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any()) } returns kotlin.Result.success(Unit)

        val result = createWorker(pendingLocalDataSource = unreliableLocalDataSource).doWork()

        result shouldBe Result.retry()
    }

    @Test
    fun `an initial listAll() that throws IOException retries the drain instead of reporting a false success`() = runTest {
        // listAll() propagates a whole-file IOException (see FilePendingSessionSubmissionLocalDataSource's
        // own doc) instead of swallowing it into emptyList() — doWork() must see this and retry, rather
        // than mistake the failure for a genuinely empty, already-drained queue.
        val unreliableLocalDataSource: PendingSessionSubmissionLocalDataSource = mockk()
        coEvery { unreliableLocalDataSource.listAll() } throws IOException("queue file unreadable")

        val result = createWorker(pendingLocalDataSource = unreliableLocalDataSource).doWork()

        result shouldBe Result.retry()
        coVerify(exactly = 0) { sessionSubmissionRemoteDataSource.submitSession(any()) }
    }

    private companion object {
        const val SIGNED_IN_UID = "uid-1"
        const val OTHER_UID = "uid-2"
        const val SUBCATEGORY_ID = "sub-1"
        val SIGNED_IN_USER = AuthUser(uid = SIGNED_IN_UID, email = "user@example.com", displayName = "User", photoUrl = null)
        val PERMANENT_CODES = listOf(
            FirebaseFunctionsException.Code.INVALID_ARGUMENT,
            FirebaseFunctionsException.Code.FAILED_PRECONDITION,
            FirebaseFunctionsException.Code.PERMISSION_DENIED,
        )
        val TRANSIENT_CODES = FirebaseFunctionsException.Code.entries - PERMANENT_CODES.toSet() - FirebaseFunctionsException.Code.OK
    }
}
