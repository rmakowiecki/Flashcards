package com.rossomak.flashcards.core.data.source

import android.content.Context
import android.util.Log
import app.cash.turbine.test
import com.rossomak.flashcards.core.data.model.PendingFlashcardResultDto
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionDto
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilePendingSessionSubmissionLocalDataSourceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        // Debug logging and a corrupted queue file both go through android.util.Log, unavailable
        // outside instrumented/Robolectric tests — stub it rather than pull in either just for this.
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun createDataSource(): FilePendingSessionSubmissionLocalDataSource {
        val context: Context = mockk()
        every { context.filesDir } returns temporaryFolder.root
        return FilePendingSessionSubmissionLocalDataSource(context)
    }

    private fun pendingSubmission(sessionId: String, startedAtEpochMillis: Long = 0L): PendingSessionSubmissionDto = PendingSessionSubmissionDto(
        id = sessionId,
        mode = "Rated",
        startedAtEpochMillis = startedAtEpochMillis,
        durationSeconds = 60,
        abandoned = false,
        categoryId = "cat-1",
        categoryName = "Category",
        subcategoryIds = listOf("sub-1"),
        subcategoryNames = listOf("Subcategory"),
        sourceType = "SingleSubcategory",
        cardResults = listOf(
            PendingFlashcardResultDto(cardId = "card-1", subcategoryId = "sub-1", state = "Mastered", attemptsUsed = 1, wasPreviouslyMastered = false),
        ),
        studyDate = "2026-09-08",
        dailyGoalMinutes = 20,
        studyDateUtcOffsetMinutes = 0,
        voiceAnsweringEnabled = false,
    )

    @Test
    fun `listAll on a fresh store with no file yet returns empty`() = runTest {
        val dataSource = createDataSource()

        dataSource.listAll() shouldBe emptyList()
    }

    @Test
    fun `append then listAll returns the appended entry`() = runTest {
        val dataSource = createDataSource()
        val submission = pendingSubmission("session-1")

        dataSource.append(submission)

        dataSource.listAll() shouldBe listOf(submission)
    }

    @Test
    fun `append twice preserves both entries in append order`() = runTest {
        val dataSource = createDataSource()
        val first = pendingSubmission("session-1")
        val second = pendingSubmission("session-2")

        dataSource.append(first)
        dataSource.append(second)

        dataSource.listAll() shouldBe listOf(first, second)
    }

    @Test
    fun `append of a session id already queued keeps only the first entry`() = runTest {
        val dataSource = createDataSource()
        val first = pendingSubmission("session-1", startedAtEpochMillis = 1_000L)

        dataSource.append(first)
        dataSource.append(pendingSubmission("session-1", startedAtEpochMillis = 2_000L))

        dataSource.listAll() shouldBe listOf(first)
    }

    @Test
    fun `remove clears only the matching entry, keeping the rest`() = runTest {
        val dataSource = createDataSource()
        val first = pendingSubmission("session-1")
        val second = pendingSubmission("session-2")
        dataSource.append(first)
        dataSource.append(second)

        dataSource.remove("session-1")

        dataSource.listAll() shouldBe listOf(second)
    }

    @Test
    fun `remove of an unknown sessionId is a no-op`() = runTest {
        val dataSource = createDataSource()
        val submission = pendingSubmission("session-1")
        dataSource.append(submission)

        dataSource.remove("session-does-not-exist")

        dataSource.listAll() shouldBe listOf(submission)
    }

    @Test
    fun `a new instance reads back what a previous instance persisted, surviving a process restart`() = runTest {
        val context: Context = mockk()
        every { context.filesDir } returns temporaryFolder.root
        val submission = pendingSubmission("session-1")
        FilePendingSessionSubmissionLocalDataSource(context).append(submission)

        val reloaded = FilePendingSessionSubmissionLocalDataSource(context).listAll()

        reloaded shouldBe listOf(submission)
    }

    @Test
    fun `a corrupted line is skipped rather than crashing, leaving the other entries intact`() = runTest {
        val dataSource = createDataSource()
        val first = pendingSubmission("session-1")
        val third = pendingSubmission("session-3")
        dataSource.append(first)
        File(temporaryFolder.root, "pending_session_submissions.jsonl").appendText("{ not valid json ][\n")
        dataSource.append(third)

        dataSource.listAll() shouldBe listOf(first, third)
    }

    @Test
    fun `remove compacts a previously skipped corrupted line off disk during its rewrite`() = runTest {
        val dataSource = createDataSource()
        val first = pendingSubmission("session-1")
        dataSource.append(first)
        File(temporaryFolder.root, "pending_session_submissions.jsonl").appendText("{ not valid json ][\n")

        // remove() rewrites the file from whatever listAll() (which already dropped the bad line) returns —
        // append() alone would not have compacted it; only remove()'s read-filter-rewrite does.
        dataSource.remove("session-does-not-exist")

        File(temporaryFolder.root, "pending_session_submissions.jsonl").readText() shouldBe
            "${Json.encodeToString(first)}\n"
    }

    @Test
    fun `listAll propagates an IOException instead of reporting an unreadable queue as empty`() = runTest {
        val context: Context = mockk()
        every { context.filesDir } returns temporaryFolder.root
        // A directory at the expected path exists (so file.exists() is true) but readLines() throws
        // FileNotFoundException — an IOException — rather than returning content.
        File(temporaryFolder.root, "pending_session_submissions.jsonl").mkdir()

        val dataSource = FilePendingSessionSubmissionLocalDataSource(context)

        // Swallowing this into emptyList() would make SessionSubmissionDeliveryWorker.doWork() see a
        // drained queue and return Result.success() for a run that never actually read it.
        shouldThrow<IOException> { dataSource.listAll() }
    }

    @Test
    fun `remove propagates an IOException instead of overwriting an unreadable queue with an empty one`() = runTest {
        val context: Context = mockk()
        every { context.filesDir } returns temporaryFolder.root
        File(temporaryFolder.root, "pending_session_submissions.jsonl").mkdir()
        val dataSource = FilePendingSessionSubmissionLocalDataSource(context)

        shouldThrow<IOException> { dataSource.remove("session-1") }

        // The "file" is still the directory it was before — remove() never got to writeAll().
        File(temporaryFolder.root, "pending_session_submissions.jsonl").isDirectory shouldBe true
    }

    @Test
    fun `remove does not leave a stray temp file behind after its atomic rewrite`() = runTest {
        val dataSource = createDataSource()
        dataSource.append(pendingSubmission("session-1"))

        dataSource.remove("session-1")

        File(temporaryFolder.root, "pending_session_submissions.jsonl.tmp").exists() shouldBe false
    }

    @Test
    fun `append opens the file in append mode, keeping earlier entries after a process restart`() = runTest {
        val context: Context = mockk()
        every { context.filesDir } returns temporaryFolder.root
        val first = pendingSubmission("session-1")
        val second = pendingSubmission("session-2")
        FilePendingSessionSubmissionLocalDataSource(context).append(first)

        FilePendingSessionSubmissionLocalDataSource(context).append(second)

        FilePendingSessionSubmissionLocalDataSource(context).listAll() shouldBe listOf(first, second)
    }

    @Test
    fun `observeAll emits what the file already holds`() = runTest {
        val context: Context = mockk()
        every { context.filesDir } returns temporaryFolder.root
        val submission = pendingSubmission("session-1")
        FilePendingSessionSubmissionLocalDataSource(context).append(submission)

        FilePendingSessionSubmissionLocalDataSource(context).observeAll().test {
            awaitItem() shouldBe listOf(submission)
        }
    }

    @Test
    fun `observeAll re-emits after each append and remove`() = runTest {
        val dataSource = createDataSource()
        val first = pendingSubmission("session-1")
        val second = pendingSubmission("session-2")

        dataSource.observeAll().test {
            awaitItem() shouldBe emptyList()
            dataSource.append(first)
            awaitItem() shouldBe listOf(first)
            dataSource.append(second)
            awaitItem() shouldBe listOf(first, second)
            dataSource.remove("session-1")
            awaitItem() shouldBe listOf(second)
        }
    }

    @Test
    fun `observeAll does not re-emit for a session id already queued`() = runTest {
        val dataSource = createDataSource()
        val submission = pendingSubmission("session-1")
        dataSource.append(submission)

        dataSource.observeAll().test {
            awaitItem() shouldBe listOf(submission)
            dataSource.append(pendingSubmission("session-1", startedAtEpochMillis = 2_000L))
            expectNoEvents()
        }
    }

    @Test
    fun `observeAll emits an empty list for an unreadable queue file instead of failing`() = runTest {
        val context: Context = mockk()
        every { context.filesDir } returns temporaryFolder.root
        File(temporaryFolder.root, "pending_session_submissions.jsonl").mkdir()

        FilePendingSessionSubmissionLocalDataSource(context).observeAll().test {
            awaitItem() shouldBe emptyList()
        }
    }

    @Test
    fun `observeAll retries the load on a later collection once an unreadable queue file becomes readable`() = runTest {
        val context: Context = mockk()
        every { context.filesDir } returns temporaryFolder.root
        val queueFile = File(temporaryFolder.root, "pending_session_submissions.jsonl")
        queueFile.mkdir()
        val dataSource = FilePendingSessionSubmissionLocalDataSource(context)
        dataSource.observeAll().test {
            awaitItem() shouldBe emptyList()
        }

        queueFile.delete()
        dataSource.append(pendingSubmission("session-1"))

        dataSource.observeAll().test {
            awaitItem().map { it.id } shouldBe listOf("session-1")
        }
    }
}
