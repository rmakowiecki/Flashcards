package com.rossomak.flashcards.feature.study.summary

/**
 * One-shot snackbar messages from the Session Summary, per the SharedFlow-for-transient-events rule
 * (ADR-0019). Never screen state: the displayed results stay on screen regardless, nothing is
 * cleared and nothing navigates (ADR-0014's Offline section).
 */
sealed interface StudySessionSummaryMessage {

    /**
     * The local XP preview, needed because the server's score did not arrive in time, could not be
     * computed: a failed read of this account's prior card progress or scoring state
     * ([com.rossomak.flashcards.core.domain.usecase.SubmitStudySessionUseCase]'s own local reads). The
     * session is still queued for delivery either way. The screen's counts stay populated regardless;
     * only the XP fields fall back to their zero defaults.
     */
    data object SaveFailed : StudySessionSummaryMessage
}
