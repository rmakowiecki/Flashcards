package com.rossomak.flashcards.feature.study.fast

sealed interface FastStudySessionMessage {
    data object VoicePlaybackUnavailable : FastStudySessionMessage
    data object CurationReportFailed : FastStudySessionMessage
}
