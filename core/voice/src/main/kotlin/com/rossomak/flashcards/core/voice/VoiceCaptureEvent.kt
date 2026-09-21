package com.rossomak.flashcards.core.voice

sealed interface VoiceCaptureEvent {
    data object SpeechStarted : VoiceCaptureEvent
    data object SpeechEnded : VoiceCaptureEvent
    data class UtteranceCaptured(val utterance: CapturedUtterance) : VoiceCaptureEvent
    data class CaptureFailed(val reason: VoiceCaptureFailureReason) : VoiceCaptureEvent
}
