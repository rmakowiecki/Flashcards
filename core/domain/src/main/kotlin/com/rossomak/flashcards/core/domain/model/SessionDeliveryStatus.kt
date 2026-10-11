package com.rossomak.flashcards.core.domain.model

/**
 * Where one just-finished session stands in its delivery to the `submitStudySession` Cloud Function,
 * as reported by [com.rossomak.flashcards.core.domain.repository.SessionSubmissionRepository.submitSession].
 * [InFlight] is the only non-final value; every other value ends the report.
 */
sealed interface SessionDeliveryStatus {

    /** Queued and a delivery attempt is expected soon; no result yet. */
    data object InFlight : SessionDeliveryStatus

    /** The server accepted and scored the session. */
    data class Scored(val score: SessionScore) : SessionDeliveryStatus

    /** The server rejected the session outright; it will never be scored. */
    data object Rejected : SessionDeliveryStatus

    /**
     * Queued, but no result is coming soon: no network when the session was queued, the delivery run
     * ended without this session, or the server recorded it but its answer was unreadable. Except in
     * that last case, the session stays queued and is still delivered later.
     */
    data object NotDelivered : SessionDeliveryStatus

    /**
     * The session could not be queued at all (no signed-in User, a failed local write), so nothing
     * on the device holds it and it will never be delivered.
     */
    data object NotQueued : SessionDeliveryStatus
}
