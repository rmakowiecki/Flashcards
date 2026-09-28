package com.rossomak.flashcards.core.data.source

import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionDto

/**
 * In-memory [PendingSessionSubmissionLocalDataSource] for tests. Lives under `src/test`, not
 * `testFixtures`: unlike `core:domain`'s fakes (consumed by every feature module's tests), nothing
 * outside `core:data` depends on this interface, so there is no cross-module test dependency to serve.
 */
class FakePendingSessionSubmissionLocalDataSource : PendingSessionSubmissionLocalDataSource {

    private val entries: MutableList<PendingSessionSubmissionDto> = mutableListOf()

    /** Every entry [append] was actually called with, in call order — separate from [entries] so a test can assert calls survived a [remove]. */
    val appendedEntries: MutableList<PendingSessionSubmissionDto> = mutableListOf()

    fun seed(pendingSessionSubmission: PendingSessionSubmissionDto) {
        entries.add(pendingSessionSubmission)
    }

    override suspend fun append(pendingSessionSubmission: PendingSessionSubmissionDto) {
        appendedEntries.add(pendingSessionSubmission)
        if (entries.none { it.id == pendingSessionSubmission.id }) entries.add(pendingSessionSubmission)
    }

    override suspend fun listAll(): List<PendingSessionSubmissionDto> = entries.toList()

    override suspend fun remove(sessionId: String) {
        entries.removeAll { it.id == sessionId }
    }
}
