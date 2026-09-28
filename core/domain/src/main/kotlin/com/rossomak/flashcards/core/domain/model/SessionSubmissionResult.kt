package com.rossomak.flashcards.core.domain.model

/** What the Session Summary shows for a just-finished session: the server's score, or the local preview when that did not arrive in time. */
sealed interface SessionSubmissionResult {

    /** The server scored the session within the wait budget. */
    data class ServerScored(val score: SessionScore) : SessionSubmissionResult

    /** The server's score did not arrive in time; this is the client's own estimate. */
    data class LocalPreview(val sessionXpResult: SessionXpResult) : SessionSubmissionResult
}
