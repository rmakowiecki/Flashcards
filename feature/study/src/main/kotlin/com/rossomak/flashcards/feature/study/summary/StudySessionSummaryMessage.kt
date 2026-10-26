package com.rossomak.flashcards.feature.study.summary

sealed interface StudySessionSummaryMessage {

    /** The score's reads failed. The session is still queued. */
    data object XpUnavailable : StudySessionSummaryMessage

    /** The session could not be queued, so it is not saved and earns no XP. */
    data object SaveFailed : StudySessionSummaryMessage
}
