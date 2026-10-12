package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.data.model.VoiceGradingStreamEventDto
import com.rossomak.flashcards.core.data.source.VoiceGradingRemoteDataSource
import com.rossomak.flashcards.core.domain.model.GradingFailureReason
import com.rossomak.flashcards.core.domain.model.GradingFailureReason.NoConnection
import com.rossomak.flashcards.core.domain.model.GradingFailureReason.ServiceError
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGrade
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGradingEvent
import com.rossomak.flashcards.core.domain.repository.VoiceAnswerGradingRepository
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Never writes to Firestore per answer (ADR-0014): grades are handed back to the caller only.
 * Batch persistence at session end belongs to the not-yet-built Rating/Attempt/Terminal-State
 * pipeline (ADR-0016/ADR-0026).
 */
class DefaultVoiceAnswerGradingRepository internal constructor(
    private val voiceGradingRemoteDataSource: VoiceGradingRemoteDataSource,
    private val ioDispatcher: CoroutineDispatcher,
) : VoiceAnswerGradingRepository {

    @Inject
    constructor(voiceGradingRemoteDataSource: VoiceGradingRemoteDataSource) : this(voiceGradingRemoteDataSource, Dispatchers.IO)

    /**
     * Collects the single streamed call (ADR-0028), re-emitting each wire event as a domain
     * [VoiceAnswerGradingEvent]. Any failure ends the flow with [VoiceAnswerGradingEvent.Failed]
     * rather than throwing; only cancellation propagates.
     *
     * The whole call, transcript and grade, has [GRADING_TIME_BUDGET]. A call still running then
     * ends with [GradingFailureReason.NoConnection]. The server gives up just before this budget
     * with an error of its own, so a slow service reads as a service error instead. There is no
     * retry: a retry would re-upload the answer and restart the transcript dwell inside the same
     * budget.
     */
    override fun transcribeAndGradeSpokenAnswer(
        cardId: String,
        question: String,
        expectedAnswer: String,
        obfuscatedAnswerWav: ByteArray,
    ): Flow<VoiceAnswerGradingEvent> = flow {
        val completed = withTimeoutOrNull(GRADING_TIME_BUDGET) {
            emitAll(streamGradingEvents(cardId, question, expectedAnswer, obfuscatedAnswerWav))
        }
        if (completed == null) {
            loge { "Voice answer grading exceeded $GRADING_TIME_BUDGET" }
            emit(VoiceAnswerGradingEvent.Failed(NoConnection))
        }
    }

    private fun streamGradingEvents(
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
                        // a Graded event with no prior transcript is a protocol violation and fails the answer as a service error, never an empty transcript.
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
        .catch { exception ->
            loge(exception) { "Voice answer grading failed" }
            emit(VoiceAnswerGradingEvent.Failed(exception.toFailureReason(NoConnection, ServiceError)))
        }
        .flowOn(ioDispatcher)

    override suspend fun transcribeAndSanitize(obfuscatedAnswerWav: ByteArray): Result<String> =
        withContext(ioDispatcher) {
            try {
                voiceGradingRemoteDataSource.transcribeAndSanitize(obfuscatedAnswerWav)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Result.failure(exception)
            }
        }

    override suspend fun checkEntitlement(): Result<Boolean> = withContext(ioDispatcher) {
        try {
            Result.success(voiceGradingRemoteDataSource.checkEntitlement().isPremium)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    companion object {
        /** How long the whole streamed call may take before it fails as [GradingFailureReason.NoConnection]. */
        val GRADING_TIME_BUDGET: Duration = 20.seconds
    }
}
