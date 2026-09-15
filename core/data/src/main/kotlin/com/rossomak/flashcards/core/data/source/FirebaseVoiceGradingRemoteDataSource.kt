package com.rossomak.flashcards.core.data.source

import android.util.Base64
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.StreamResponse
import com.rossomak.flashcards.core.data.model.EntitlementDto
import com.rossomak.flashcards.core.data.model.VoiceGradingStreamEventDto
import com.rossomak.flashcards.core.domain.model.VoiceGradingEntitlementException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase Callable client for the voice-grading backend (ADR-0029). Every call resolves its
 * endpoint from the initialized [FirebaseApp] (`google-services.json`), not a base URL — the
 * whole REST/Retrofit stack is gone. `transcribeAndGradeSpokenAnswer` streams over one Callable
 * connection (ADR-0028); `transcribeAndSanitize` rides the same callable with no question/answer
 * and reads only the first streamed chunk; `checkEntitlement` is a plain unary callable.
 */
class FirebaseVoiceGradingRemoteDataSource @Inject constructor(
    private val functions: FirebaseFunctions,
) : VoiceGradingRemoteDataSource {

    override fun transcribeAndGradeSpokenAnswer(
        cardId: String,
        question: String,
        expectedAnswer: String,
        wavBytes: ByteArray,
    ): Flow<VoiceGradingStreamEventDto> {
        val payload = mapOf(
            FIELD_CARD_ID to cardId,
            FIELD_QUESTION to question,
            FIELD_EXPECTED_ANSWER to expectedAnswer,
            FIELD_AUDIO_BASE64 to encodeWav(wavBytes),
        )
        return streamCallable(payload)
            .map { response -> response.toStreamEventDto() }
            .catch { throwable -> throw throwable.mapEntitlementRejection() }
    }

    // Broad on purpose - the callable stream can fail with more than just FirebaseFunctionsException
    // (a transport-layer error before the SDK wraps it, say), and this call site has no surrounding
    // try/catch of its own - narrowing this would let an unanticipated exception type crash instead
    // of surfacing as Result.failure.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun transcribeAndSanitize(wavBytes: ByteArray): Result<String> = try {
        val payload = mapOf(FIELD_AUDIO_BASE64 to encodeWav(wavBytes))
        val firstChunk = streamCallable(payload)
            .filterIsInstance<StreamResponse.Message>()
            .firstOrNull()
            ?: error("transcribeAndSanitize received no transcript chunk")
        val data = firstChunk.message.data as? Map<*, *>
            ?: error("Unexpected transcribeAndSanitize chunk shape: ${firstChunk.message.data}")
        val sanitizedTranscript = data[FIELD_SANITIZED_TRANSCRIPT] as? String
            ?: error("transcribeAndSanitize chunk missing transcript: ${firstChunk.message.data}")
        Result.success(sanitizedTranscript)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception.mapEntitlementRejection())
    }

    override suspend fun checkEntitlement(): EntitlementDto {
        val result = functions.getHttpsCallable(ENTITLEMENT_FUNCTION_NAME).call().await()
        val data = result.data as? Map<*, *>
            ?: error("Unexpected entitlement result shape: ${result.data}")
        val isPremium = data[FIELD_IS_PREMIUM] as? Boolean
            ?: error("Entitlement result missing is_premium: ${result.data}")
        return EntitlementDto(isPremium = isPremium)
    }

    private fun streamCallable(payload: Map<String, Any>): Flow<StreamResponse> =
        functions.getHttpsCallable(TRANSCRIBE_AND_GRADE_FUNCTION_NAME)
            .stream(payload)
            .asFlow()

    private fun StreamResponse.toStreamEventDto(): VoiceGradingStreamEventDto = when (this) {
        is StreamResponse.Message -> {
            val data = message.data as? Map<*, *>
                ?: error("Unexpected transcribeAndGradeSpokenAnswer chunk shape: ${message.data}")
            val sanitizedTranscript = data[FIELD_SANITIZED_TRANSCRIPT] as? String
                ?: error("transcribeAndGradeSpokenAnswer chunk missing transcript: ${message.data}")
            VoiceGradingStreamEventDto.TranscriptChunk(sanitizedTranscript = sanitizedTranscript)
        }
        is StreamResponse.Result -> {
            val data = result.data as? Map<*, *>
                ?: error("Unexpected transcribeAndGradeSpokenAnswer result shape: ${result.data}")
            // Production always grades; an empty terminal Result is impossible on this path, so a
            // missing field is a real server bug and must surface as a mapping error, never a
            // silent Graded(0, "") (ADR-0029 §4).
            val gradePercent = (data[FIELD_GRADE] as? Number)?.toInt()
                ?: error("transcribeAndGradeSpokenAnswer result missing grade: ${result.data}")
            val feedback = data[FIELD_FEEDBACK] as? String
                ?: error("transcribeAndGradeSpokenAnswer result missing feedback: ${result.data}")
            VoiceGradingStreamEventDto.Graded(
                gradePercent = gradePercent,
                feedback = feedback,
            )
        }
        // StreamResponse isn't a Kotlin `sealed class` at the bytecode level (just `abstract`),
        // so the compiler can't prove Message/Result are exhaustive across module boundaries.
        else -> error("Unknown StreamResponse subtype: $this")
    }

    /** A server-side entitlement rejection (PERMISSION_DENIED) surfaces as [VoiceGradingEntitlementException]. */
    private fun Throwable.mapEntitlementRejection(): Throwable =
        if (this is FirebaseFunctionsException &&
            code == FirebaseFunctionsException.Code.PERMISSION_DENIED
        ) {
            VoiceGradingEntitlementException(cause = this)
        } else {
            this
        }

    private fun encodeWav(wavBytes: ByteArray): String = Base64.encodeToString(wavBytes, Base64.NO_WRAP)

    private companion object {
        const val TRANSCRIBE_AND_GRADE_FUNCTION_NAME = "transcribeAndGradeSpokenAnswer"
        const val ENTITLEMENT_FUNCTION_NAME = "entitlement"
        const val FIELD_CARD_ID = "card_id"
        const val FIELD_QUESTION = "question"
        const val FIELD_EXPECTED_ANSWER = "expected_answer"
        const val FIELD_AUDIO_BASE64 = "audio_base64"
        const val FIELD_SANITIZED_TRANSCRIPT = "sanitized_transcript"
        const val FIELD_GRADE = "grade"
        const val FIELD_FEEDBACK = "feedback"
        const val FIELD_IS_PREMIUM = "is_premium"
    }
}
