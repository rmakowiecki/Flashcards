package com.rossomak.flashcards.feature.study.summary

import com.rossomak.flashcards.core.domain.model.LevelProgress

/**
 * What the Session Summary knows about its Level card, read from the live Level stream rather than
 * from the session's own score. It never gates the reveal sequence: the card slot shows whatever
 * this is when the sequence reaches it.
 */
sealed interface StudySessionSummaryLevelCardState {

    /** The Level stream has not emitted yet. No ceiling applies, so this can last offline. */
    data object Loading : StudySessionSummaryLevelCardState

    /** The latest [levelProgress] from the stream. */
    data class Content(val levelProgress: LevelProgress) : StudySessionSummaryLevelCardState

    /** The Level stream failed or ended before its first emission: the card is omitted. */
    data object Unavailable : StudySessionSummaryLevelCardState
}
