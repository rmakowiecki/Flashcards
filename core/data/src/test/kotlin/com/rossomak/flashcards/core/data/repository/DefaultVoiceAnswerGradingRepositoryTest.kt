package com.rossomak.flashcards.core.data.repository

import app.cash.turbine.test
import com.rossomak.flashcards.core.data.model.EntitlementDto
import com.rossomak.flashcards.core.data.model.VoiceGradingStreamEventDto
import com.rossomak.flashcards.core.data.source.VoiceGradingRemoteDataSource
import com.rossomak.flashcards.core.domain.model.GradingFailureReason
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGrade
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGradingEvent
import com.rossomak.flashcards.core.domain.model.VoiceGradingEntitlementException
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultVoiceAnswerGradingRepositoryTest {

    private val voiceGradingRemoteDataSource: VoiceGradingRemoteDataSource = mockk()

    private val cardId = "card-1"
    private val question = "What is a foreground service?"
    private val expectedAnswer = "A service with a persistent notification"
    private val wavBytes = byteArrayOf(1, 2, 3)
    private val sanitizedTranscript = "A service with a notification"
    private val expectedGrade = VoiceAnswerGrade(
        sanitizedTranscript = sanitizedTranscript,
        gradePercent = 80,
        feedback = "Mostly right",
    )

    private fun TestScope.createRepository(): DefaultVoiceAnswerGradingRepository =
        DefaultVoiceAnswerGradingRepository(voiceGradingRemoteDataSource, StandardTestDispatcher(testScheduler))

    private fun successfulStream() = flow {
        emit(VoiceGradingStreamEventDto.TranscriptChunk(sanitizedTranscript))
        emit(VoiceGradingStreamEventDto.Graded(expectedGrade.gradePercent, expectedGrade.feedback))
    }

    @Test
    fun `transcribeAndGradeSpokenAnswer streams transcript then grade`() = runTest {
        every {
            voiceGradingRemoteDataSource.transcribeAndGradeSpokenAnswer(cardId, question, expectedAnswer, wavBytes)
        } returns successfulStream()

        createRepository().transcribeAndGradeSpokenAnswer(cardId, question, expectedAnswer, wavBytes).test {
            awaitItem() shouldBe VoiceAnswerGradingEvent.TranscriptReady(sanitizedTranscript)
            awaitItem() shouldBe VoiceAnswerGradingEvent.Graded(expectedGrade)
            awaitComplete()
        }
    }

    @Test
    fun `transcribeAndGradeSpokenAnswer ends an io failure with a no-connection failure after one call`() = runTest {
        every {
            voiceGradingRemoteDataSource.transcribeAndGradeSpokenAnswer(cardId, question, expectedAnswer, wavBytes)
        } returns flow<VoiceGradingStreamEventDto> { throw IOException("network down") }

        createRepository().transcribeAndGradeSpokenAnswer(cardId, question, expectedAnswer, wavBytes).test {
            awaitItem() shouldBe VoiceAnswerGradingEvent.Failed(GradingFailureReason.NoConnection)
            awaitComplete()
        }
        coVerify(exactly = 1) {
            voiceGradingRemoteDataSource.transcribeAndGradeSpokenAnswer(cardId, question, expectedAnswer, wavBytes)
        }
    }

    @Test
    fun `transcribeAndGradeSpokenAnswer ends an io failure the SDK wrapped in its own exception with a no-connection failure`() = runTest {
        every {
            voiceGradingRemoteDataSource.transcribeAndGradeSpokenAnswer(cardId, question, expectedAnswer, wavBytes)
        } returns flow<VoiceGradingStreamEventDto> { throw IllegalStateException("request failed", IOException("timeout")) }

        createRepository().transcribeAndGradeSpokenAnswer(cardId, question, expectedAnswer, wavBytes).test {
            awaitItem() shouldBe VoiceAnswerGradingEvent.Failed(GradingFailureReason.NoConnection)
            awaitComplete()
        }
    }

    @Test
    fun `transcribeAndGradeSpokenAnswer ends a call still running after 20 seconds with a no-connection failure`() = runTest {
        every {
            voiceGradingRemoteDataSource.transcribeAndGradeSpokenAnswer(cardId, question, expectedAnswer, wavBytes)
        } returns flow {
            emit(VoiceGradingStreamEventDto.TranscriptChunk(sanitizedTranscript))
            awaitCancellation()
        }

        createRepository().transcribeAndGradeSpokenAnswer(cardId, question, expectedAnswer, wavBytes).test {
            awaitItem() shouldBe VoiceAnswerGradingEvent.TranscriptReady(sanitizedTranscript)
            awaitItem() shouldBe VoiceAnswerGradingEvent.Failed(GradingFailureReason.NoConnection)
            awaitComplete()
        }
        currentTime shouldBe DefaultVoiceAnswerGradingRepository.GRADING_TIME_BUDGET.inWholeMilliseconds
    }

    @Test
    fun `transcribeAndGradeSpokenAnswer ends an entitlement rejection with a service-error failure, without retrying`() = runTest {
        every {
            voiceGradingRemoteDataSource.transcribeAndGradeSpokenAnswer(cardId, question, expectedAnswer, wavBytes)
        } returns flow<VoiceGradingStreamEventDto> { throw VoiceGradingEntitlementException() }

        createRepository().transcribeAndGradeSpokenAnswer(cardId, question, expectedAnswer, wavBytes).test {
            awaitItem() shouldBe VoiceAnswerGradingEvent.Failed(GradingFailureReason.ServiceError)
            awaitComplete()
        }
        coVerify(exactly = 1) {
            voiceGradingRemoteDataSource.transcribeAndGradeSpokenAnswer(cardId, question, expectedAnswer, wavBytes)
        }
    }

    @Test
    fun `transcribeAndGradeSpokenAnswer ends a grade that arrives before any transcript with a service-error failure`() = runTest {
        every {
            voiceGradingRemoteDataSource.transcribeAndGradeSpokenAnswer(cardId, question, expectedAnswer, wavBytes)
        } returns flow { emit(VoiceGradingStreamEventDto.Graded(expectedGrade.gradePercent, expectedGrade.feedback)) }

        createRepository().transcribeAndGradeSpokenAnswer(cardId, question, expectedAnswer, wavBytes).test {
            awaitItem() shouldBe VoiceAnswerGradingEvent.Failed(GradingFailureReason.ServiceError)
            awaitComplete()
        }
    }

    @Test
    fun `transcribeAndSanitize returns the sanitized transcript from the api`() = runTest {
        val transcript = "A service with a notification"
        coEvery { voiceGradingRemoteDataSource.transcribeAndSanitize(wavBytes) } returns Result.success(transcript)

        val result = createRepository().transcribeAndSanitize(wavBytes)

        result.getOrThrow() shouldBe transcript
        coVerify(exactly = 1) { voiceGradingRemoteDataSource.transcribeAndSanitize(wavBytes) }
    }

    @Test
    fun `transcribeAndSanitize wraps api failure in failure result`() = runTest {
        val error = IOException("boom")
        coEvery { voiceGradingRemoteDataSource.transcribeAndSanitize(wavBytes) } throws error

        val result = createRepository().transcribeAndSanitize(wavBytes)

        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe error
        coVerify(exactly = 1) { voiceGradingRemoteDataSource.transcribeAndSanitize(wavBytes) }
    }

    @Test
    fun `checkEntitlement returns the premium verdict`() = runTest {
        coEvery { voiceGradingRemoteDataSource.checkEntitlement() } returns EntitlementDto(isPremium = true)

        val result = createRepository().checkEntitlement()

        result.getOrThrow() shouldBe true
        coVerify(exactly = 1) { voiceGradingRemoteDataSource.checkEntitlement() }
    }

    @Test
    fun `checkEntitlement wraps api failure in failure result`() = runTest {
        val error = IllegalStateException("boom")
        coEvery { voiceGradingRemoteDataSource.checkEntitlement() } throws error

        val result = createRepository().checkEntitlement()

        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe error
        coVerify(exactly = 1) { voiceGradingRemoteDataSource.checkEntitlement() }
    }
}
