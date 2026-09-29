package com.rossomak.flashcards.core.domain.model

/**
 * A Fast Study Session's rules state: an immutable snapshot that `FastSessionReducer` takes in and
 * returns the next one of.
 *
 * @param cards the session's cards, in routed order. Fixed for the whole session.
 * @param currentIndex the presented card. Owned here in both deliveries: with read-aloud on, the
 * voice player is told which card to present and never moves on by itself.
 * @param seenCardIds every card whose answer was shown, in first-seen order: Fast's Studied
 * criterion. A revisited card is not added twice.
 * @param isAnswerRevealed whether the presented card shows its answer.
 * @param readAloudStep where read-aloud is in the presented card. Only meaningful with read-aloud on.
 * @param pauseReason who paused the session; `null` while nothing did. A player that stopped by
 * itself (an audio-focus loss, a failed utterance) has no pause reason.
 * @param isPlaying the voice player's playing state, as last reported.
 * @param isAdvanceHoldRequested while set, read-aloud stops on the current card at the auto-advance
 * point instead of moving on.
 * @param isHeldAtAdvancePoint read-aloud reached the auto-advance point with a hold requested and
 * stopped there. Not a user pause: releasing the hold moves on and plays.
 * @param isReleaseLingering the hold was released while held: the session stays on the held card
 * for the release linger, then moves on. A new hold request drops the linger and holds again;
 * anything that ends the hold drops it too.
 * @param isPausedAtAdvancePoint a user pause replaced a hold at the auto-advance point: the next play
 * moves on instead of re-reading the answer.
 */
data class FastSessionState(
    val cards: List<Flashcard>,
    val currentIndex: Int = 0,
    val seenCardIds: List<String> = emptyList(),
    val isAnswerRevealed: Boolean = false,
    val readAloudStep: ReadAloudStep = ReadAloudStep.Question,
    val pauseReason: FastPauseReason? = null,
    val isPlaying: Boolean = false,
    val isAdvanceHoldRequested: Boolean = false,
    val isHeldAtAdvancePoint: Boolean = false,
    val isReleaseLingering: Boolean = false,
    val isPausedAtAdvancePoint: Boolean = false,
) {
    /**
     * Whether the read-aloud Next does anything: at a question it reveals that card's answer, at an
     * answer it moves on. At the last card's answer it does nothing, so the session only ends once
     * that answer has been read in full.
     */
    val isReadAloudNextAvailable: Boolean
        get() = !isAnswerRevealed || currentIndex < cards.lastIndex
}

/**
 * Where read-aloud is in the presented card. [Question] and [Answer] mean that part is presented:
 * being read, read in full a moment ago, or paused mid-read. The two pauses mean the coordinator's
 * timer runs, or ran and was stopped by a pause.
 */
enum class ReadAloudStep {
    Question,
    QuestionPause,
    Answer,
    AdvancePause,
    ;

    val isPause: Boolean get() = this == QuestionPause || this == AdvancePause
}

/** Who paused a Fast Study Session. */
enum class FastPauseReason {
    /** The user paused, in the app or from outside it. */
    User,

    /** A temporary pause, ended by the same caller; a user pause or play in between replaces it. */
    Temporary,

    /**
     * A text-to-speech engine could not start. The session stays in read-aloud and waits for a
     * play, which restarts the voice stack.
     */
    EngineUnavailable,
}
