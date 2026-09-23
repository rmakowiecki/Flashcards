package com.rossomak.flashcards.core.voice

import com.rossomak.flashcards.core.domain.model.VoiceCaptureFailureReason

sealed interface VoiceCaptureEvent {
    data object SpeechStarted : VoiceCaptureEvent
    data object SpeechEnded : VoiceCaptureEvent
    data class UtteranceCaptured(val utterance: CapturedUtterance) : VoiceCaptureEvent
    data class CaptureFailed(val reason: VoiceCaptureFailureReason) : VoiceCaptureEvent
}
