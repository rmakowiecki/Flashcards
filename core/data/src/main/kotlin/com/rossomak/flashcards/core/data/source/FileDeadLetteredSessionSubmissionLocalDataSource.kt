package com.rossomak.flashcards.core.data.source

import android.content.Context
import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.data.model.DeadLetteredSessionSubmissionDto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * File-backed [DeadLetteredSessionSubmissionLocalDataSource]: [FILE_NAME] under [Context.filesDir], one
 * [DeadLetteredSessionSubmissionDto] JSON-encoded per line, the same layout as
 * [FilePendingSessionSubmissionLocalDataSource].
 *
 * [append] rewrites the whole file through a sibling temp file and an atomic rename, so a process death
 * mid-write leaves either the old complete file or the new complete file on disk. Dead-lettering is
 * rare, so the cost of a full rewrite does not matter. A line that fails to decode is logged and
 * skipped, and dropped from disk by the next [append]. A whole-file [IOException] propagates, so an
 * unreadable file is never silently replaced with a shorter one.
 */
class FileDeadLetteredSessionSubmissionLocalDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DeadLetteredSessionSubmissionLocalDataSource {

    private val mutex = Mutex()
    private val file: File get() = File(context.filesDir, FILE_NAME)

    override suspend fun append(deadLetteredSessionSubmission: DeadLetteredSessionSubmissionDto) = withContext(Dispatchers.IO) {
        mutex.withLock { writeAll(readAll() + deadLetteredSessionSubmission) }
    }

    override suspend fun listAll(): List<DeadLetteredSessionSubmissionDto> = withContext(Dispatchers.IO) {
        mutex.withLock { readAll() }
    }

    /** Must only be called while holding [mutex]. */
    @Throws(IOException::class)
    private fun readAll(): List<DeadLetteredSessionSubmissionDto> {
        if (!file.exists()) return emptyList()
        return file.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    Json.decodeFromString<DeadLetteredSessionSubmissionDto>(line)
                } catch (exception: SerializationException) {
                    loge(exception) { "Skipping one corrupted dead-lettered session submission line" }
                    null
                }
            }
    }

    /** Must only be called while holding [mutex]. Atomic: never leaves [file] half-written. */
    private fun writeAll(entries: List<DeadLetteredSessionSubmissionDto>) {
        val tempFile = File(context.filesDir, "$FILE_NAME.tmp")
        tempFile.writeText(entries.joinToString(separator = "") { "${Json.encodeToString(it)}\n" })
        if (!tempFile.renameTo(file)) {
            error("Failed to atomically replace $FILE_NAME with its updated contents")
        }
    }

    private companion object {
        const val FILE_NAME = "dead_lettered_session_submissions.jsonl"
    }
}
