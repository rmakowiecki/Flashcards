package com.rossomak.flashcards.core.data.model

import kotlinx.serialization.Serializable

/**
 * A pending session the server permanently rejected, kept for diagnostics only. [entry] is the queue
 * entry exactly as it was queued; [failureCode] and [failureMessage] come from the rejecting
 * `FirebaseFunctionsException`, and [deadLetteredAtEpochMillis] records when the worker gave up on it.
 * See [com.rossomak.flashcards.core.data.source.FileDeadLetteredSessionSubmissionLocalDataSource] for
 * where these are stored.
 */
@Serializable
data class DeadLetteredSessionSubmissionDto(
    val entry: PendingSessionSubmissionDto,
    val failureCode: String,
    val failureMessage: String?,
    val deadLetteredAtEpochMillis: Long,
)
