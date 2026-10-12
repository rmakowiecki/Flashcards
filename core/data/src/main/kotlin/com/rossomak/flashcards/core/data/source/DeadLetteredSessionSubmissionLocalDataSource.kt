package com.rossomak.flashcards.core.data.source

import com.rossomak.flashcards.core.data.model.DeadLetteredSessionSubmissionDto

/**
 * Append-only diagnostics record of pending sessions the server permanently rejected.
 * [com.rossomak.flashcards.core.data.worker.SessionSubmissionDeliveryWorker] is the only [append]
 * caller and Account Deletion the only [removeAllForUser] caller. Nothing in the app reads the record
 * back: [listAll] exists for tests and for inspecting the file by hand.
 */
interface DeadLetteredSessionSubmissionLocalDataSource {

    suspend fun append(deadLetteredSessionSubmission: DeadLetteredSessionSubmissionDto)

    suspend fun listAll(): List<DeadLetteredSessionSubmissionDto>

    /** Drops every record whose entry [uid] owned, keeping other Users' records. */
    suspend fun removeAllForUser(uid: String)
}
