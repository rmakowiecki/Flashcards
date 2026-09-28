package com.rossomak.flashcards.core.domain.model

/**
 * Ordered events delivered over the single streamed `transcribeAndGradeSpokenAnswer` call
 * (ADR-0028, renamed in ADR-0029): the
 * sanitized transcript arrives first, the grade second, both over one connection — no second
 * request triggers the grade phase. The stream ends with either [Graded] or [Failed].
 */
sealed interface VoiceAnswerGradingEvent {
    data class TranscriptReady(val sanitizedTranscript: String) : VoiceAnswerGradingEvent
    data class Graded(val grade: VoiceAnswerGrade) : VoiceAnswerGradingEvent

    /** The answer could not be transcribed or graded, for [reason]. Terminal: nothing follows it. */
    data class Failed(val reason: GradingFailureReason) : VoiceAnswerGradingEvent
}
