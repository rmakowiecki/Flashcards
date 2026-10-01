package com.rossomak.flashcards.feature.study.fast

sealed interface FastStudySessionMessage {
    data object VoicePlaybackUnavailable : FastStudySessionMessage
    data object CurationReportFailed : FastStudySessionMessage

    /** The user asked to play while a call rings or runs, and nothing started. */
    data object PlayIgnoredDuringCall : FastStudySessionMessage
}
