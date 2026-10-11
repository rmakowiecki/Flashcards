package com.rossomak.flashcards.feature.study.summary

import com.rossomak.flashcards.core.domain.model.LevelProgress

/**
 * What the Session Summary knows about the live Level stream, the same one Home's card reads. Only a
 * Summary whose score is unavailable draws its Level card from it; a scored Summary draws the score's
 * Level position instead.
 */
sealed interface StudySessionSummaryLevelCardState {

    /** The Level stream has not emitted yet. No ceiling applies, so this can last offline. */
    data object Loading : StudySessionSummaryLevelCardState

    /** The latest [levelProgress] from the stream. */
    data class Content(val levelProgress: LevelProgress) : StudySessionSummaryLevelCardState

    /** The Level stream failed or ended before its first emission: the card is omitted. */
    data object Unavailable : StudySessionSummaryLevelCardState
}
