package com.rossomak.flashcards.core.data.model

/**
 * Ordered wire events for the streamed `transcribeAndGradeSpokenAnswer` callable (ADR-0028). Not
 * `@Serializable`: the Firebase Functions callable SDK decodes `StreamResponse.Message`/`StreamResponse.Result` payloads into raw
 * `Map<String, Any?>` itself, so [com.rossomak.flashcards.core.data.source.FirebaseVoiceGradingRemoteDataSource]
 * builds these by hand from that map.
 */
sealed interface VoiceGradingStreamEventDto {
    data class TranscriptChunk(val sanitizedTranscript: String) : VoiceGradingStreamEventDto
    data class Graded(val gradePercent: Int, val feedback: String) : VoiceGradingStreamEventDto
}
