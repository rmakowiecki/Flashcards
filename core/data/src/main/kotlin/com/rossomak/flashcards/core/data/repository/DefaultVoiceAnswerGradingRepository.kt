package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.data.model.VoiceGradingStreamEventDto
import com.rossomak.flashcards.core.data.source.VoiceGradingRemoteDataSource
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGrade
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGradingEvent
import com.rossomak.flashcards.core.domain.repository.VoiceAnswerGradingRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext

/**
 * Never writes to Firestore per answer (ADR-0014): grades are handed back to the caller only.
 * Batch persistence at session end belongs to the not-yet-built Rating/Attempt/Terminal-State
 * pipeline (ADR-0016/ADR-0026).
 */
class DefaultVoiceAnswerGradingRepository @Inject constructor(
    private val voiceGradingRemoteDataSource: VoiceGradingRemoteDataSource,
) : VoiceAnswerGradingRepository {

    /**
     * Collects the single streamed call (ADR-0028), re-emitting each wire event as a domain
     * [VoiceAnswerGradingEvent]. A transient [IOException] retries the *whole* call from
     * scratch with backoff — the server has no partial-progress concept to resume, so a retry
     * after the transcript chunk already arrived simply re-emits a fresh
     * [VoiceAnswerGradingEvent.TranscriptReady] to the collector. Any other failure, and an
     * [IOException] once the retries run out, ends the flow with [VoiceAnswerGradingEvent.Failed]
     * rather than throwing; only cancellation propagates.
     */
    override fun transcribeAndGradeSpokenAnswer(
        cardId: String,
        question: String,
        expectedAnswer: String,
        obfuscatedAnswerWav: ByteArray,
    ): Flow<VoiceAnswerGradingEvent> = flow {
        var sanitizedTranscript: String? = null
        voiceGradingRemoteDataSource.transcribeAndGradeSpokenAnswer(cardId, question, expectedAnswer, obfuscatedAnswerWav)
            .collect { event ->
                when (event) {
                    is VoiceGradingStreamEventDto.TranscriptChunk -> {
                        sanitizedTranscript = event.sanitizedTranscript
                        emit(VoiceAnswerGradingEvent.TranscriptReady(event.sanitizedTranscript))
                    }
                    is VoiceGradingStreamEventDto.Graded -> {
                        // The stream contract guarantees the transcript chunk precedes the grade
                        // (ADR-0028); a Graded event with no prior transcript is a protocol
                        // violation and fails the answer as a service error, never an empty
                        // transcript.
                        val transcript = sanitizedTranscript
                            ?: error("Graded event arrived before any transcript chunk")
                        val grade = VoiceAnswerGrade(
                            sanitizedTranscript = transcript,
                            gradePercent = event.gradePercent,
                            feedback = event.feedback,
                        )
                        emit(VoiceAnswerGradingEvent.Graded(grade))
                    }
                }
            }
    }
        .retryWhen { cause, attempt ->
            val shouldRetry = cause is IOException && attempt + 1 < MAX_UPLOAD_ATTEMPTS
            if (shouldRetry) delay(BASE_RETRY_DELAY_MS * (1L shl attempt.toInt()))
            shouldRetry
        }
        .catch { exception ->
            loge(exception) { "Voice answer grading failed" }
            emit(VoiceAnswerGradingEvent.Failed(exception.toGradingFailureReason()))
        }
        .flowOn(Dispatchers.IO)

    override suspend fun transcribeAndSanitize(obfuscatedAnswerWav: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                voiceGradingRemoteDataSource.transcribeAndSanitize(obfuscatedAnswerWav)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Result.failure(exception)
            }
        }

    override suspend fun checkEntitlement(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Result.success(voiceGradingRemoteDataSource.checkEntitlement().isPremium)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private companion object {
        const val MAX_UPLOAD_ATTEMPTS = 3
        const val BASE_RETRY_DELAY_MS = 500L
    }
}
