package com.rossomak.flashcards.feature.study.summary

sealed interface StudySessionSummaryMessage {

    /** No server score arrived and the local preview's read failed. The session is still queued. */
    data object XpUnavailable : StudySessionSummaryMessage

    /** The session could not be queued, so it is not saved and earns no XP. */
    data object SaveFailed : StudySessionSummaryMessage
}
