package com.rossomak.flashcards.core.domain.model

/**
 * A Fast Study Session's rules state: an immutable snapshot that `FastSessionReducer` takes in and
 * returns the next one of.
 *
 * @param cards the session's cards, in routed order. Fixed for the whole session.
 * @param seenCardIds every card whose answer was shown, in first-seen order: Fast's Studied
 * criterion. A revisited card is not added twice.
 * @param pauseReason why the session is paused; `null` while it is not.
 */
data class FastSessionState(
    val cards: List<Flashcard>,
    val currentIndex: Int = 0,
    val seenCardIds: List<String> = emptyList(),
    val isAnswerRevealed: Boolean = false,
    val pauseReason: SessionPauseReason? = null,
)
