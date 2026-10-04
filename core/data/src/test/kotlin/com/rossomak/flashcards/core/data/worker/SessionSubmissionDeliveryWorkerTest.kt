package com.rossomak.flashcards.core.data.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.ProgressUpdater
import androidx.work.WorkerParameters
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctionsException
import com.rossomak.flashcards.core.data.mapper.toDto
import com.rossomak.flashcards.core.data.model.DeliveredSessionDto
import com.rossomak.flashcards.core.data.model.PendingFlashcardResultDto
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionDto
import com.rossomak.flashcards.core.data.source.CardProgressRemoteDataSource
import com.rossomak.flashcards.core.data.source.FakeDeadLetteredSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.FakePendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.PendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.ScoringStateRemoteDataSource
import com.rossomak.flashcards.core.data.source.SessionSubmissionRemoteDataSource
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.model.SessionScore
import com.rossomak.flashcards.core.domain.model.SessionScoreCounts
import com.rossomak.flashcards.core.domain.model.XpBreakdown
import com.rossomak.flashcards.core.domain.repository.FakeAuthRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.util.UUID
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
    private val progressUpdater: ProgressUpdater = mockk()

    /** Every progress [Data] the worker published, in order. */
    private val publishedProgress = mutableListOf<Data>()

    @Before
    fun setUp() {
        authRepository.userToReturn = SIGNED_IN_USER
        coEvery { scoringStateRemoteDataSource.getScoringState(any()) } returns null
        coEvery { cardProgressRemoteDataSource.getProgress(any(), any()) } returns null
        val completedFuture: ListenableFuture<Void> = mockk()
        every { completedFuture.isDone } returns true
        every { completedFuture.get() } returns null
        every { progressUpdater.updateProgress(any(), any(), any()) } answers {
            publishedProgress += thirdArg<Data>()
            completedFuture
        }
    }

    /** [runAttemptCount] defaults to 0 — WorkManager's own count for "this is the first attempt". */
    private fun createWorker(
        runAttemptCount: Int = 0,
        pendingLocalDataSource: PendingSessionSubmissionLocalDataSource = localDataSource,
    ): SessionSubmissionDeliveryWorker {
        val workerParameters: WorkerParameters = mockk()
        every { workerParameters.runAttemptCount } returns runAttemptCount
        every { workerParameters.id } returns UUID.randomUUID()
        every { workerParameters.progressUpdater } returns progressUpdater
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
        sourceType = "SingleSubcategory",
        cardResults = listOf(
            PendingFlashcardResultDto(cardId = "card-1", subcategoryId = SUBCATEGORY_ID, state = "Mastered", attemptsUsed = 1, wasPreviouslyMastered = false),
        ),
        studyDate = "2026-09-08",
        dailyGoalMinutes = 20,
        studyDateUtcOffsetMinutes = 0,
        voiceAnsweringEnabled = false,
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
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any(), any()) } returns kotlin.Result.success(SCORE)

        createWorker().doWork()

        coVerifySequence {
            sessionSubmissionRemoteDataSource.submitSession(SIGNED_IN_UID, withArg { it.id shouldBe "session-1" })
            sessionSubmissionRemoteDataSource.submitSession(SIGNED_IN_UID, withArg { it.id shouldBe "session-2" })
        }
    }

    @Test
    fun `doWork clears every entry that was delivered successfully`() = runTest {
        localDataSource.seed(pendingSubmission("session-1", startedAtEpochMillis = 1_000L))
        localDataSource.seed(pendingSubmission("session-2", startedAtEpochMillis = 2_000L))
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any(), any()) } returns kotlin.Result.success(SCORE)

        val result = createWorker().doWork()

        result.shouldBeInstanceOf<Result.Success>()
        localDataSource.listAll() shouldBe emptyList()
    }

    @Test
    fun `an empty queue succeeds without submitting anything`() = runTest {
        val result = createWorker().doWork()

        result.shouldBeInstanceOf<Result.Success>()
        coVerify(exactly = 0) { sessionSubmissionRemoteDataSource.submitSession(any(), any()) }
    }

    @Test
    fun `only the signed-in User's entries are delivered, foreign entries stay queued and the run succeeds`() = runTest {
        val own = pendingSubmission("session-own", startedAtEpochMillis = 2_000L)
        val foreign = pendingSubmission("session-foreign", startedAtEpochMillis = 1_000L, uid = OTHER_UID)
        localDataSource.seed(foreign)
        localDataSource.seed(own)
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any(), any()) } returns kotlin.Result.success(SCORE)

        val result = createWorker().doWork()

        result.shouldBeInstanceOf<Result.Success>()
        localDataSource.listAll() shouldBe listOf(foreign)
        coVerify(exactly = 1) { sessionSubmissionRemoteDataSource.submitSession(any(), match { it.id == "session-own" }) }
        coVerify(exactly = 0) { sessionSubmissionRemoteDataSource.submitSession(any(), match { it.id == "session-foreign" }) }
    }

    @Test
    fun `a run with only foreign entries succeeds without delivering anything`() = runTest {
        val foreign = pendingSubmission("session-foreign", startedAtEpochMillis = 1_000L, uid = OTHER_UID)
        localDataSource.seed(foreign)

        val result = createWorker().doWork()

        result.shouldBeInstanceOf<Result.Success>()
        localDataSource.listAll() shouldBe listOf(foreign)
        coVerify(exactly = 0) { sessionSubmissionRemoteDataSource.submitSession(any(), any()) }
    }

    @Test
    fun `a User switch mid-drain stops before the next submission and returns retry`() = runTest {
        localDataSource.seed(pendingSubmission("session-1", startedAtEpochMillis = 1_000L))
        localDataSource.seed(pendingSubmission("session-2", startedAtEpochMillis = 2_000L))
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any(), any()) } coAnswers {
            authRepository.userToReturn = SIGNED_IN_USER.copy(uid = OTHER_UID)
            kotlin.Result.success(SCORE)
        }

        val result = createWorker().doWork()

        result shouldBe Result.retry()
        localDataSource.listAll().map { it.id } shouldBe listOf("session-2")
        coVerify(exactly = 0) { sessionSubmissionRemoteDataSource.submitSession(any(), match { it.id == "session-2" }) }
    }

    @Test
    fun `with nobody signed in the run succeeds without delivering anything`() = runTest {
        authRepository.userToReturn = null
        val entry = pendingSubmission("session-1", startedAtEpochMillis = 1_000L)
        localDataSource.seed(entry)

        val result = createWorker().doWork()

        result.shouldBeInstanceOf<Result.Success>()
        localDataSource.listAll() shouldBe listOf(entry)
        coVerify(exactly = 0) { sessionSubmissionRemoteDataSource.submitSession(any(), any()) }
    }

    @Test
    fun `an entry without an owning uid is dead-lettered unchanged and does not block later entries`() = runTest {
        val ownerless = pendingSubmission("session-1", startedAtEpochMillis = 1_000L, uid = "")
        localDataSource.seed(ownerless)
        localDataSource.seed(pendingSubmission("session-2", startedAtEpochMillis = 2_000L))
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any(), any()) } returns kotlin.Result.success(SCORE)

        val result = createWorker().doWork()

        result.shouldBeInstanceOf<Result.Success>()
        localDataSource.listAll() shouldBe emptyList()
        with(deadLetterLocalDataSource.listAll().single()) {
            entry shouldBe ownerless
            failureCode shouldBe MALFORMED_FAILURE_CODE
        }
        coVerify(exactly = 0) { sessionSubmissionRemoteDataSource.submitSession(any(), match { it.id == "session-1" }) }
        coVerify(exactly = 1) { sessionSubmissionRemoteDataSource.submitSession(any(), match { it.id == "session-2" }) }
    }

    @Test
    fun `a malformed entry that fails domain conversion is dead-lettered and does not block later entries`() = runTest {
        val malformed = pendingSubmission("session-1", startedAtEpochMillis = 1_000L).copy(mode = "NotARealStudyMode")
        localDataSource.seed(malformed)
        localDataSource.seed(pendingSubmission("session-2", startedAtEpochMillis = 2_000L))
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any(), any()) } returns kotlin.Result.success(SCORE)

        val result = createWorker().doWork()

        result.shouldBeInstanceOf<Result.Success>()
        localDataSource.listAll() shouldBe emptyList()
        with(deadLetterLocalDataSource.listAll().single()) {
            entry shouldBe malformed
            failureCode shouldBe MALFORMED_FAILURE_CODE
        }
        coVerify(exactly = 0) { sessionSubmissionRemoteDataSource.submitSession(any(), match { it.id == "session-1" }) }
        coVerify(exactly = 1) { sessionSubmissionRemoteDataSource.submitSession(any(), match { it.id == "session-2" }) }
    }

    @Test
    fun `each permanent code dead-letters the entry and the next entry is still delivered in the same run`() = runTest {
        PERMANENT_CODES.forEach { code ->
            val rejected = pendingSubmission("session-rejected-$code", startedAtEpochMillis = 1_000L)
            localDataSource.seed(rejected)
            localDataSource.seed(pendingSubmission("session-next-$code", startedAtEpochMillis = 2_000L))
            coEvery { sessionSubmissionRemoteDataSource.submitSession(any(), match { it.id == rejected.id }) } returns
                kotlin.Result.failure(functionsException(code))
            coEvery { sessionSubmissionRemoteDataSource.submitSession(any(), match { it.id == "session-next-$code" }) } returns kotlin.Result.success(SCORE)

            val result = createWorker().doWork()

            result.shouldBeInstanceOf<Result.Success>()
            localDataSource.listAll() shouldBe emptyList()
            coVerify(exactly = 1) { sessionSubmissionRemoteDataSource.submitSession(any(), match { it.id == "session-next-$code" }) }
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
            coEvery { sessionSubmissionRemoteDataSource.submitSession(any(), any()) } returns kotlin.Result.failure(functionsException(code))

            val result = createWorker().doWork()

            result shouldBe Result.retry()
            localDataSource.listAll() shouldBe entries
            entries.forEach { localDataSource.remove(it.id) }
        }
        deadLetterLocalDataSource.listAll() shouldBe emptyList()
        coVerify(exactly = 0) { sessionSubmissionRemoteDataSource.submitSession(any(), match { it.id == "session-2" }) }
    }

    @Test
    fun `a non-Functions exception returns retry and keeps the entry`() = runTest {
        val entry = pendingSubmission("session-1", startedAtEpochMillis = 1_000L)
        localDataSource.seed(entry)
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any(), any()) } returns kotlin.Result.failure(IOException("offline"))

        val result = createWorker().doWork()

        result shouldBe Result.retry()
        localDataSource.listAll() shouldBe listOf(entry)
        deadLetterLocalDataSource.listAll() shouldBe emptyList()
    }

    @Test
    fun `a transient failure on a high run attempt count still returns retry and keeps the entry`() = runTest {
        val entry = pendingSubmission("session-1", startedAtEpochMillis = 1_000L)
        localDataSource.seed(entry)
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any(), any()) } returns
            kotlin.Result.failure(functionsException(FirebaseFunctionsException.Code.UNAVAILABLE))

        val result = createWorker(runAttemptCount = 1_000).doWork()

        result shouldBe Result.retry()
        localDataSource.listAll() shouldBe listOf(entry)
        deadLetterLocalDataSource.listAll() shouldBe emptyList()
    }

    @Test
    fun `a successful delivery refreshes the scoring state and each touched Subcategory's Card Progress from the server`() = runTest {
        localDataSource.seed(pendingSubmission("session-1", startedAtEpochMillis = 1_000L, subcategoryIds = listOf("sub-1", "sub-2")))
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any(), any()) } returns kotlin.Result.success(SCORE)

        createWorker().doWork()

        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState(Source.SERVER) }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress("sub-1", Source.SERVER) }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress("sub-2", Source.SERVER) }
    }

    @Test
    fun `a delivered entry is removed even when the post-delivery refresh fails`() = runTest {
        localDataSource.seed(pendingSubmission("session-1", startedAtEpochMillis = 1_000L, subcategoryIds = listOf("sub-1", "sub-2")))
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any(), any()) } returns kotlin.Result.success(SCORE)
        coEvery { scoringStateRemoteDataSource.getScoringState(any()) } throws IllegalStateException("offline")
        coEvery { cardProgressRemoteDataSource.getProgress("sub-1", any()) } throws IllegalStateException("offline")

        val result = createWorker().doWork()

        result.shouldBeInstanceOf<Result.Success>()
        localDataSource.listAll() shouldBe emptyList()
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress("sub-2", Source.SERVER) }
    }

    @Test
    fun `a failed delivery does not refresh anything`() = runTest {
        localDataSource.seed(pendingSubmission("session-1", startedAtEpochMillis = 1_000L))
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any(), any()) } returns kotlin.Result.failure(IOException("offline"))

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
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any(), any()) } returns kotlin.Result.success(SCORE)

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
        coVerify(exactly = 0) { sessionSubmissionRemoteDataSource.submitSession(any(), any()) }
    }

    @Test
    fun `a successful run outputs each delivered session's score and each dead-lettered session's rejection`() = runTest {
        localDataSource.seed(pendingSubmission("session-rejected", startedAtEpochMillis = 1_000L))
        localDataSource.seed(pendingSubmission("session-scored", startedAtEpochMillis = 2_000L))
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any(), match { it.id == "session-rejected" }) } returns
            kotlin.Result.failure(functionsException(FirebaseFunctionsException.Code.INVALID_ARGUMENT))
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any(), match { it.id == "session-scored" }) } returns kotlin.Result.success(SCORE)

        val result = createWorker().doWork()

        SessionDeliveryReport.read(result.outputData) shouldBe mapOf(
            "session-rejected" to DeliveredSessionDto.Rejected,
            "session-scored" to DeliveredSessionDto.Scored(SCORE.toDto()),
        )
    }

    @Test
    fun `a session delivered without a score is refreshed, removed and left out of the report without blocking the next entry`() = runTest {
        localDataSource.seed(pendingSubmission("session-unscored", startedAtEpochMillis = 1_000L))
        localDataSource.seed(pendingSubmission("session-scored", startedAtEpochMillis = 2_000L))
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any(), match { it.id == "session-unscored" }) } returns kotlin.Result.success(null)
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any(), match { it.id == "session-scored" }) } returns kotlin.Result.success(SCORE)

        val result = createWorker().doWork()

        result.shouldBeInstanceOf<Result.Success>()
        localDataSource.listAll() shouldBe emptyList()
        deadLetterLocalDataSource.listAll() shouldBe emptyList()
        coVerify(exactly = 2) { scoringStateRemoteDataSource.getScoringState(Source.SERVER) }
        SessionDeliveryReport.read(result.outputData) shouldBe mapOf("session-scored" to DeliveredSessionDto.Scored(SCORE.toDto()))
    }

    @Test
    fun `progress is published after each entry, before its post-delivery refresh`() = runTest {
        localDataSource.seed(pendingSubmission("session-1", startedAtEpochMillis = 1_000L))
        localDataSource.seed(pendingSubmission("session-2", startedAtEpochMillis = 2_000L))
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any(), any()) } returns kotlin.Result.success(SCORE)

        createWorker().doWork()

        publishedProgress.map { SessionDeliveryReport.read(it).keys } shouldBe listOf(setOf("session-1"), setOf("session-1", "session-2"))
        coVerifyOrder {
            progressUpdater.updateProgress(any(), any(), any())
            scoringStateRemoteDataSource.getScoringState(Source.SERVER)
        }
    }

    @Test
    fun `a session delivered before a later transient failure is still published as progress`() = runTest {
        localDataSource.seed(pendingSubmission("session-1", startedAtEpochMillis = 1_000L))
        localDataSource.seed(pendingSubmission("session-2", startedAtEpochMillis = 2_000L))
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any(), match { it.id == "session-1" }) } returns kotlin.Result.success(SCORE)
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any(), match { it.id == "session-2" }) } returns kotlin.Result.failure(IOException("offline"))

        val result = createWorker().doWork()

        result shouldBe Result.retry()
        SessionDeliveryReport.read(publishedProgress.last()) shouldBe mapOf("session-1" to DeliveredSessionDto.Scored(SCORE.toDto()))
    }

    @Test
    fun `a failure to publish progress never stops the drain`() = runTest {
        every { progressUpdater.updateProgress(any(), any(), any()) } throws IllegalStateException("database closed")
        localDataSource.seed(pendingSubmission("session-1", startedAtEpochMillis = 1_000L))
        coEvery { sessionSubmissionRemoteDataSource.submitSession(any(), any()) } returns kotlin.Result.success(SCORE)

        val result = createWorker().doWork()

        result.shouldBeInstanceOf<Result.Success>()
        localDataSource.listAll() shouldBe emptyList()
    }

    private companion object {
        const val SIGNED_IN_UID = "uid-1"
        const val OTHER_UID = "uid-2"
        const val SUBCATEGORY_ID = "sub-1"
        const val MALFORMED_FAILURE_CODE = "IllegalArgumentException"
        val SIGNED_IN_USER = AuthUser(uid = SIGNED_IN_UID, email = "user@example.com", displayName = "User", photoUrl = null)
        val PERMANENT_CODES = listOf(
            FirebaseFunctionsException.Code.INVALID_ARGUMENT,
            FirebaseFunctionsException.Code.FAILED_PRECONDITION,
            FirebaseFunctionsException.Code.PERMISSION_DENIED,
        )
        val TRANSIENT_CODES = FirebaseFunctionsException.Code.entries - PERMANENT_CODES.toSet() - FirebaseFunctionsException.Code.OK
        val SCORE = SessionScore(
            breakdown = XpBreakdown(newCards = 10, mastered = 100),
            level = 1,
            xpIntoCurrentLevel = 110,
            xpForNextLevel = 1000,
            levelsCrossed = emptyList(),
            counts = SessionScoreCounts(newCardsStudied = 1, newlyMastered = 1, partial = 0, defended = 0, demastered = 0),
            rates = null,
        )
    }
}
