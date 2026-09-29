package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.GradingFailureReason
import com.rossomak.flashcards.core.domain.model.SessionResult

/** One-shot things a Rated Study Session reports to its screen. */
sealed interface RatedSessionEvent {
    data object VoiceAnswerSilenceSkip : RatedSessionEvent
    data object VoiceAnswerSilencePause : RatedSessionEvent
    data class VoiceAnswerGradingFailed(val reason: GradingFailureReason) : RatedSessionEvent
    data object VoiceAnswerGradingPause : RatedSessionEvent
    data object VoiceAnswerCaptureUnavailable : RatedSessionEvent

    /** A text-to-speech engine could not start; the session is paused until resumed. */
    data object VoicePlaybackUnavailable : RatedSessionEvent

    /** The microphone permission is no longer granted; voice is stopped and the session should end. */
    data object MicPermissionRevoked : RatedSessionEvent

    /** The session ended, completed or abandoned. Sent exactly once. */
    data class SessionEnded(val result: SessionResult) : RatedSessionEvent
}
