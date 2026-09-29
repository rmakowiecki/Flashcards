package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.VoiceAnswerGradingEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class FakeVoiceAnswerGradingRepository : VoiceAnswerGradingRepository {

    /** One grading call, without the audio. */
    data class Request(val cardId: String, val question: String, val expectedAnswer: String)

    /** What the next grading calls return. Never completes on its own unless the flow does. */
    var gradingFlow: Flow<VoiceAnswerGradingEvent> = emptyFlow()

    val requests = mutableListOf<Request>()

    override fun transcribeAndGradeSpokenAnswer(
        cardId: String,
        question: String,
        expectedAnswer: String,
        obfuscatedAnswerWav: ByteArray,
    ): Flow<VoiceAnswerGradingEvent> {
        requests += Request(cardId, question, expectedAnswer)
        return gradingFlow
    }

    override suspend fun transcribeAndSanitize(obfuscatedAnswerWav: ByteArray): Result<String> = Result.success("")

    override suspend fun checkEntitlement(): Result<Boolean> = Result.success(true)
}
