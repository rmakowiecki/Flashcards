package com.rossomak.flashcards.core.domain.model

/** Something the voice player reports, in the order it happened. */
sealed interface PlaybackEvent {

    /** A transport [command] from outside the app. The player never acts on it by itself. */
    data class ExternalCommand(val command: TransportCommand) : PlaybackEvent

    /** In question-only mode (Rated Voice sessions, where the question is read aloud), the question of [cardId] has been read in full. */
    data class QuestionFinished(val cardId: String) : PlaybackEvent

    /**
     * The answer of [cardId] was revealed: read aloud, or shown while paused. Sent every time the
     * card enters its answer phase, whether or not a screen is showing it.
     */
    data class AnswerRevealed(val cardId: String) : PlaybackEvent

    /** [notice] has finished, or was given up on. Exactly one per spoken notice, in speaking order. */
    data class NoticeFinished(val notice: SpokenNotice) : PlaybackEvent

    /**
     * The player read past the last card's answer and stopped by itself. Never sent for a pause, a
     * seek or a question-only read; what it means for the session is the listener's decision.
     */
    data object EndReached : PlaybackEvent

    /** A text-to-speech engine, for questions or for notices, could not start. */
    data object EngineUnavailable : PlaybackEvent
}
