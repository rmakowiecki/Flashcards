package com.rossomak.flashcards.feature.study.rated

/**
 * One-shot transient snackbar messages for a Rated voice study session.
 * Grading feedback is deliberately absent: it's long-form (Rating + rationale), so it
 * renders as bottom-sheet plain text instead of a snackbar.
 */
sealed interface RatedStudySessionMessage {

    data object VoicePlaybackUnavailable : RatedStudySessionMessage

    data object CurationSubmissionFailed : RatedStudySessionMessage

    /** Grading could not reach its service: the device is offline or the request timed out. */
    data object VoiceAnswerGradingOffline : RatedStudySessionMessage

    /** The grading service was reached but did not return a grade. */
    data object VoiceAnswerGradingServiceError : RatedStudySessionMessage

    data object VoiceAnswerSilenceSkip : RatedStudySessionMessage

    data object VoiceAnswerSilencePause : RatedStudySessionMessage

    /** A session restored after the microphone was revoked in system Settings; the session ends. */
    data object VoiceAnswerMicPermissionRevoked : RatedStudySessionMessage

    /**
     * A non-permission capture failure (e.g. Bluetooth mic dropped, capture-loop error) — voice
     * answering pauses on the current card; the session continues once the user resumes.
     */
    data object VoiceAnswerCaptureUnavailable : RatedStudySessionMessage
}
