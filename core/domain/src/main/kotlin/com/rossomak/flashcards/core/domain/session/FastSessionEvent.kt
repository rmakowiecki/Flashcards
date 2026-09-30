package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.TransportCommand

/** One-shot things a Fast Study Session reports to its screen. */
sealed interface FastSessionEvent {

    /** A text-to-speech engine could not start; the session is paused until played again. */
    data object VoicePlaybackUnavailable : FastSessionEvent

    /**
     * A transport [command] from outside the app changed the session. Sent after it was applied,
     * and never for a command the session ignored.
     */
    data class ExternalTransportCommand(val command: TransportCommand) : FastSessionEvent

    /** The session ended, completed or abandoned. Sent exactly once. */
    data class SessionEnded(val result: SessionResult) : FastSessionEvent
}
