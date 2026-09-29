package com.rossomak.flashcards.core.domain.model

/**
 * What the Session Summary shows for a just-finished session: the server's score, or the local preview
 * when that did not arrive in time. Both carry a [SessionScore] scored by the same rules, so the Summary
 * renders either the same way; the variant only says where [score] came from.
 */
sealed interface SessionSubmissionResult {
    val score: SessionScore

    /** The server scored the session within the wait budget. */
    data class ServerScored(override val score: SessionScore) : SessionSubmissionResult

    /** The server's score did not arrive in time; this is the client's own estimate. */
    data class LocalPreview(override val score: SessionScore) : SessionSubmissionResult
}
