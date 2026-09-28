package com.rossomak.flashcards.core.domain.model

/** Something the microphone capture reports while voice answering is started. */
sealed interface CaptureEvent {
    data object SpeechStarted : CaptureEvent
    data object SpeechEnded : CaptureEvent

    /**
     * One spoken answer, already obfuscated on the device. The bytes are only ever handed to the
     * grading call: never stored, logged or kept after it.
     */
    class UtteranceCaptured(val obfuscatedWav: ByteArray) : CaptureEvent

    data class CaptureFailed(val reason: VoiceCaptureFailureReason) : CaptureEvent
}
