package com.rossomak.flashcards.core.data.model

import kotlinx.serialization.Serializable

/**
 * A pending session that can never be delivered, kept for diagnostics only: either the server
 * permanently rejected it, or the entry is malformed and could not be converted back to a
 * `SessionResult`. [entry] is the queue entry exactly as it was queued. [failureCode] is the
 * rejecting `FirebaseFunctionsException`'s code, or the exception's class name for any other failure;
 * [failureMessage] is that exception's message. [deadLetteredAtEpochMillis] records when the worker
 * gave up on it.
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
