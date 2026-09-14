package com.rossomak.flashcards.core.data.source

import com.rossomak.flashcards.core.data.model.EntitlementDto
import com.rossomak.flashcards.core.data.model.VoiceGradingStreamEventDto
import kotlinx.coroutines.flow.Flow

/**
 * Client-side contract of the Firebase Callable voice-grading backend (ADR-0029). One deployed
 * function, `transcribeAndGradeSpokenAnswer`, serves both the production grade path and the debug
 * transcribe+sanitize path — the mode is inferred server-side from the payload. The client
 * surfaces the two modes as two intent-revealing methods so the mode is legible at the call site.
 *
 * [FirebaseVoiceGradingRemoteDataSource] talks to the deployment via Firebase callables; [FakeVoiceGradingRemoteDataSource] is a
 * test-only double (`core/data/src/test`). The proxy verifies the Firebase ID token and premium
 * entitlement server-side, forwards the WAV to ElevenLabs Scribe, runs the sanitize then grade LLM
 * calls, and never persists audio.
 */
interface VoiceGradingRemoteDataSource {

    /**
     * Full pipeline call, streamed over one Firebase Callable connection (ADR-0028): obfuscated
     * WAV in, a [VoiceGradingStreamEventDto.TranscriptChunk] out as soon as STT + sanitize finish,
     * then a [VoiceGradingStreamEventDto.Graded] out once grading finishes.
     */
    fun transcribeAndGradeSpokenAnswer(
        cardId: String,
        question: String,
        expectedAnswer: String,
        wavBytes: ByteArray,
    ): Flow<VoiceGradingStreamEventDto>

    /**
     * Debug: transcribe + sanitize only. Rides the same callable with no question/expected_answer,
     * so the server skips grading (ADR-0029 §3–4); returns the sanitized transcript from the first
     * streamed chunk and ignores the empty terminal result.
     */
    suspend fun transcribeAndSanitize(wavBytes: ByteArray): Result<String>

    /** Server-side premium entitlement verdict for the calling user. */
    suspend fun checkEntitlement(): EntitlementDto
}
