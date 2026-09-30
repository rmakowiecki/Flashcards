package com.rossomak.flashcards.core.data.source

import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [PendingSessionSubmissionLocalDataSource] for tests. Lives under `src/test`, not
 * `testFixtures`: unlike `core:domain`'s fakes (consumed by every feature module's tests), nothing
 * outside `core:data` depends on this interface, so there is no cross-module test dependency to serve.
 */
class FakePendingSessionSubmissionLocalDataSource : PendingSessionSubmissionLocalDataSource {

    private val entries = MutableStateFlow<List<PendingSessionSubmissionDto>>(emptyList())

    /** Every entry [append] was actually called with, in call order — separate from [entries] so a test can assert calls survived a [remove]. */
    val appendedEntries: MutableList<PendingSessionSubmissionDto> = mutableListOf()

    fun seed(pendingSessionSubmission: PendingSessionSubmissionDto) {
        entries.update { it + pendingSessionSubmission }
    }

    override suspend fun append(pendingSessionSubmission: PendingSessionSubmissionDto) {
        appendedEntries.add(pendingSessionSubmission)
        entries.update { queued -> if (queued.none { it.id == pendingSessionSubmission.id }) queued + pendingSessionSubmission else queued }
    }

    override suspend fun listAll(): List<PendingSessionSubmissionDto> = entries.value

    override suspend fun remove(sessionId: String) {
        entries.update { queued -> queued.filterNot { it.id == sessionId } }
    }

    override fun observeAll(): Flow<List<PendingSessionSubmissionDto>> = entries.asStateFlow()
}
