package com.rossomak.flashcards.core.domain.model

/** Something the voice player reports, in the order it happened. */
sealed interface PlaybackEvent {

    /** A transport [command] from outside the app. The player never acts on it by itself. */
    data class ExternalCommand(val command: TransportCommand) : PlaybackEvent

    /** The question of [cardId] was read in full. Never sent for a read that was paused or replaced. */
    data class QuestionFinished(val cardId: String) : PlaybackEvent

    /** The answer of [cardId] was read in full. Never sent for a read that was paused or replaced. */
    data class AnswerFinished(val cardId: String) : PlaybackEvent

    /**
     * The answer of [cardId] was revealed: read aloud, or shown while paused. Sent every time the
     * card enters its answer phase, whether or not a screen is showing it.
     */
    data class AnswerRevealed(val cardId: String) : PlaybackEvent

    /** [notice] has finished, or was given up on. Exactly one per spoken notice, in speaking order. */
    data class NoticeFinished(val notice: SpokenNotice) : PlaybackEvent

    /** A text-to-speech engine, for questions or for notices, could not start. */
    data object EngineUnavailable : PlaybackEvent
}
