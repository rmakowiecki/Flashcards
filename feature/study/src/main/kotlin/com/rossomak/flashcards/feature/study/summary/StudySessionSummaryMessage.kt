package com.rossomak.flashcards.feature.study.summary

/**
 * One-shot snackbar messages from the Session Summary, per the SharedFlow-for-transient-events rule
 * (ADR-0019). Never screen state: the displayed results stay on screen regardless, nothing is
 * cleared and nothing navigates (ADR-0014's Offline section).
 */
sealed interface StudySessionSummaryMessage {

    /**
     * No XP could be calculated for this session: neither the server's score arrived nor could the
     * local preview be computed, because a read of this account's prior card progress or scoring
     * state failed. The session itself is saved and queued for delivery either way; only the XP
     * figures are missing, so the screen shows no numbers for them.
     */
    data object XpUnavailable : StudySessionSummaryMessage
}
