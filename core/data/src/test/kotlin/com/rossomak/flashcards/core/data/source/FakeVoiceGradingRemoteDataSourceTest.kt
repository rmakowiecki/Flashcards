package com.rossomak.flashcards.core.data.source

import app.cash.turbine.test
import com.rossomak.flashcards.core.data.model.VoiceGradingStreamEventDto
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FakeVoiceGradingRemoteDataSourceTest {

    private val fakeApi = FakeVoiceGradingRemoteDataSource().apply {
        isTransientFailureInjectionEnabled = false
        isLatencySimulationEnabled = false
    }

    private val question = "What does a foreground service require?"
    private val expectedAnswer = "A persistent notification visible to the user"

    @Test
    fun `transcribeAndSanitize returns a non-blank sanitized transcript`() = runTest {
        val result = fakeApi.transcribeAndSanitize(ByteArray(64_000))

        result.getOrThrow().isNotBlank() shouldBe true
    }

    @Test
    fun `transcribeAndSanitize fails with entitlement exception when premium simulation is off`() = runTest {
        fakeApi.simulatePremiumEntitlement = false

        val result = fakeApi.transcribeAndSanitize(ByteArray(64))

        (result.exceptionOrNull() is VoiceGradingEntitlementException) shouldBe true
    }

    @Test
    fun `transcribeAndGradeSpokenAnswer throws entitlement exception when premium simulation is off`() = runTest {
        fakeApi.simulatePremiumEntitlement = false

        fakeApi.transcribeAndGradeSpokenAnswer("card-1", question, expectedAnswer, ByteArray(64)).test {
            (awaitError() is VoiceGradingEntitlementException) shouldBe true
        }
    }

    @Test
    fun `checkEntitlement reflects the simulated premium record`() = runTest {
        fakeApi.simulatePremiumEntitlement = false
        fakeApi.checkEntitlement().isPremium shouldBe false

        fakeApi.simulatePremiumEntitlement = true
        fakeApi.checkEntitlement().isPremium shouldBe true
    }

    @Test
    fun `transcribeAndGradeSpokenAnswer streams a transcript chunk derived from the expected answer, then a grade`() = runTest {
        val longExpectedAnswer = "a foreground service keeps running with a persistent notification " +
            "shown to the user even when the app itself is no longer visible on screen"

        fakeApi.transcribeAndGradeSpokenAnswer("card-1", question, longExpectedAnswer, ByteArray(64_000)).test {
            val chunk = awaitItem() as VoiceGradingStreamEventDto.TranscriptChunk
            chunk.sanitizedTranscript.isNotBlank() shouldBe true

            val graded = awaitItem() as VoiceGradingStreamEventDto.Graded
            graded.gradePercent shouldBeInRange 0..100

            awaitComplete()
        }
    }
}
