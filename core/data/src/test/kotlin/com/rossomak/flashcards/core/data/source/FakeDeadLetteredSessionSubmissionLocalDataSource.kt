package com.rossomak.flashcards.core.data.source

import com.rossomak.flashcards.core.data.model.DeadLetteredSessionSubmissionDto

/** In-memory [DeadLetteredSessionSubmissionLocalDataSource] for tests. */
class FakeDeadLetteredSessionSubmissionLocalDataSource : DeadLetteredSessionSubmissionLocalDataSource {

    private val entries: MutableList<DeadLetteredSessionSubmissionDto> = mutableListOf()

    override suspend fun append(deadLetteredSessionSubmission: DeadLetteredSessionSubmissionDto) {
        entries.add(deadLetteredSessionSubmission)
    }

    override suspend fun listAll(): List<DeadLetteredSessionSubmissionDto> = entries.toList()

    override suspend fun removeAllForUser(uid: String) {
        entries.removeAll { it.entry.uid == uid }
    }
}
