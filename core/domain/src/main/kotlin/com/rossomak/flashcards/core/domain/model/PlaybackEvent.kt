package com.rossomak.flashcards.core.domain.model

/** Something the voice player reports, in the order it happened. */
sealed interface PlaybackEvent {

    /** A transport [command] from outside the app. The player never acts on it by itself. */
    data class ExternalCommand(val command: TransportCommand) : PlaybackEvent

    /** In question-only mode, the question of [cardId] has been read in full. */
    data class QuestionFinished(val cardId: String) : PlaybackEvent

    /** [notice] has finished, or was given up on. Exactly one per spoken notice, in speaking order. */
    data class NoticeFinished(val notice: SpokenNotice) : PlaybackEvent

    /** A text-to-speech engine, for questions or for notices, could not start. */
    data object EngineUnavailable : PlaybackEvent
}
