package com.rossomak.flashcards.core.voice

import com.rossomak.flashcards.core.domain.model.VoiceCaptureFailureReason

/**
 * What [VoiceCaptureEngine] reports while listening. Per utterance the order is always
 * [SpeechStarted] → [SpeechEnded] → [UtteranceCaptured], cut short only by a stop or a
 * [CaptureFailed]. A blip with under 300 ms of speech is discarded as noise and emits nothing, so a
 * consumer never sees a [SpeechStarted] that no [UtteranceCaptured] follows.
 */
sealed interface VoiceCaptureEvent {
    /** The utterance has passed the minimum speech length, so it will be kept. */
    data object SpeechStarted : VoiceCaptureEvent

    /** The utterance ended (trailing silence or length cap); its [UtteranceCaptured] follows. */
    data object SpeechEnded : VoiceCaptureEvent
    data class UtteranceCaptured(val utterance: CapturedUtterance) : VoiceCaptureEvent
    data class CaptureFailed(val reason: VoiceCaptureFailureReason) : VoiceCaptureEvent
}
