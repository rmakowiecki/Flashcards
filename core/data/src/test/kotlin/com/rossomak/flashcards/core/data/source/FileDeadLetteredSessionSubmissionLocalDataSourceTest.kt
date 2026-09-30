package com.rossomak.flashcards.core.data.source

import android.content.Context
import com.rossomak.flashcards.core.data.model.DeadLetteredSessionSubmissionDto
import com.rossomak.flashcards.core.data.model.PendingFlashcardResultDto
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionDto
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileDeadLetteredSessionSubmissionLocalDataSourceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = mockk<Context>().also { every { it.filesDir } answers { temporaryFolder.root } }

    private val file: File get() = File(temporaryFolder.root, FILE_NAME)

    private fun createDataSource() = FileDeadLetteredSessionSubmissionLocalDataSource(context)

    private fun deadLettered(sessionId: String): DeadLetteredSessionSubmissionDto = DeadLetteredSessionSubmissionDto(
        entry = PendingSessionSubmissionDto(
            id = sessionId,
            uid = "uid-1",
            mode = "Rated",
            startedAtEpochMillis = 1_000L,
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
        ),
        failureCode = "INVALID_ARGUMENT",
        failureMessage = "rejected",
        deadLetteredAtEpochMillis = 5_000L,
    )

    @Test
    fun `listAll on a fresh store with no file yet returns empty`() = runTest {
        createDataSource().listAll() shouldBe emptyList()
    }

    @Test
    fun `append then listAll round-trips every record in append order`() = runTest {
        val dataSource = createDataSource()
        val first = deadLettered("session-1")
        val second = deadLettered("session-2")

        dataSource.append(first)
        dataSource.append(second)

        dataSource.listAll() shouldBe listOf(first, second)
    }

    @Test
    fun `a new instance reads back what a previous instance persisted`() = runTest {
        val record = deadLettered("session-1")
        createDataSource().append(record)

        createDataSource().listAll() shouldBe listOf(record)
    }

    @Test
    fun `append replaces the file atomically, leaving no temp file and only complete lines`() = runTest {
        val dataSource = createDataSource()
        val first = deadLettered("session-1")
        val second = deadLettered("session-2")

        dataSource.append(first)
        dataSource.append(second)

        File(temporaryFolder.root, "$FILE_NAME.tmp").exists() shouldBe false
        file.readText() shouldBe "${Json.encodeToString(first)}\n${Json.encodeToString(second)}\n"
    }

    @Test
    fun `a corrupted line is skipped and compacted away by the next append`() = runTest {
        val dataSource = createDataSource()
        val first = deadLettered("session-1")
        val second = deadLettered("session-2")
        dataSource.append(first)
        file.appendText("{ not valid json ][\n")

        dataSource.append(second)

        dataSource.listAll() shouldBe listOf(first, second)
        file.readText() shouldBe "${Json.encodeToString(first)}\n${Json.encodeToString(second)}\n"
    }

    @Test
    fun `append propagates an IOException instead of overwriting an unreadable file`() = runTest {
        file.mkdir()

        shouldThrow<IOException> { createDataSource().append(deadLettered("session-1")) }

        file.isDirectory shouldBe true
    }

    private companion object {
        const val FILE_NAME = "dead_lettered_session_submissions.jsonl"
    }
}
