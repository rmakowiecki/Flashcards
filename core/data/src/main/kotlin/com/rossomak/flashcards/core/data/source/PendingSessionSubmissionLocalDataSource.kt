package com.rossomak.flashcards.core.data.source

import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionDto
import kotlinx.coroutines.flow.Flow

/**
 * The local durable delivery queue's read/write surface — matches this codebase's
 * `XxxLocalDataSource` convention (cf. [StudySessionPreferencesLocalDataSource] /
 * [DataStoreStudySessionPreferencesLocalDataSource]), extended here to a list-shaped store: every
 * other local data source in this app is DataStore's key-value Preferences, this is the first one
 * holding an ordered collection of pending records.
 *
 * [com.rossomak.flashcards.core.data.repository.DefaultSessionSubmissionRepository] is the only
 * [append] caller; [com.rossomak.flashcards.core.data.worker.SessionSubmissionDeliveryWorker] is the
 * only [listAll]/[remove] caller. [com.rossomak.flashcards.core.data.repository.PendingSessionProjector]
 * is the only [observeAll] caller. See [FilePendingSessionSubmissionLocalDataSource] for the concrete
 * storage shape and its concurrency guarantee across those callers.
 */
interface PendingSessionSubmissionLocalDataSource {

    /**
     * Adds [pendingSessionSubmission] to the queue, unless an entry with the same session id is already
     * queued: a session submitted again, as when the Session Summary is recreated after process death,
     * is still delivered once.
     */
    suspend fun append(pendingSessionSubmission: PendingSessionSubmissionDto)

    suspend fun listAll(): List<PendingSessionSubmissionDto>

    suspend fun remove(sessionId: String)

    /**
     * Every User's queued entries, re-emitted after each [append] and [remove]. Unlike [listAll], never
     * fails: a queue file that cannot be read emits an empty list, since an observer only projects the
     * queue and has nothing to retry.
     */
    fun observeAll(): Flow<List<PendingSessionSubmissionDto>>
}
