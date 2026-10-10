package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.GradingFailureReason
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.TransportCommand

/** One-shot things a Rated Study Session reports to its screen. */
sealed interface RatedSessionEvent {
    data object VoiceAnswerSilenceSkip : RatedSessionEvent
    data object VoiceAnswerSilencePause : RatedSessionEvent
    data class VoiceAnswerGradingFailed(val reason: GradingFailureReason) : RatedSessionEvent
    data object VoiceAnswerGradingPause : RatedSessionEvent
    data object VoiceAnswerCaptureUnavailable : RatedSessionEvent

    /** A text-to-speech engine could not start; the session is paused until resumed. */
    data object VoicePlaybackUnavailable : RatedSessionEvent

    /** The user asked to play while a call rings or runs, and nothing started. */
    data object PlayIgnoredDuringCall : RatedSessionEvent

    /** The microphone permission is no longer granted; voice is stopped and the session should end. */
    data object MicPermissionRevoked : RatedSessionEvent

    /**
     * A transport [command] from outside the app changed the session. Sent after it was applied,
     * and never for a command the session ignored.
     */
    data class ExternalTransportCommand(val command: TransportCommand) : RatedSessionEvent

    /** The session ended, completed or abandoned, with at least one Studied Flashcard. Sent exactly once. */
    data class SessionEnded(val result: SessionResult) : RatedSessionEvent

    /**
     * The session ended with no Studied Flashcard, so nothing is recorded. Sent exactly once, instead
     * of [SessionEnded].
     */
    data object SessionDiscarded : RatedSessionEvent
}
