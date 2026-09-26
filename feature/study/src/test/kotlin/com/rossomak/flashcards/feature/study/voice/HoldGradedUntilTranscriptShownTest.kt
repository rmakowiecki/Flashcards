package com.rossomak.flashcards.feature.study.voice

import com.rossomak.flashcards.core.domain.model.VoiceAnswerGrade
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGradingEvent
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGradingEvent.Graded
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGradingEvent.TranscriptReady
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HoldGradedUntilTranscriptShownTest {

    private val transcriptReady = TranscriptReady(sanitizedTranscript = "state hoisting")
    private val graded = Graded(VoiceAnswerGrade(sanitizedTranscript = "state hoisting", gradePercent = 90, feedback = "Spot on."))

    @Test
    fun `graded 300 ms after transcript is held until 1000 ms`() = runTest {
        val emissions = collectTimed(gradingFlow(transcriptReady, delayBeforeLast = 300.milliseconds, last = graded))

        emissions shouldBe listOf(transcriptReady to 0L, graded to 1_000L)
    }

    @Test
    fun `graded 1500 ms after transcript is emitted immediately`() = runTest {
        val emissions = collectTimed(gradingFlow(transcriptReady, delayBeforeLast = 1_500.milliseconds, last = graded))

        emissions shouldBe listOf(transcriptReady to 0L, graded to 1_500L)
    }

    @Test
    fun `graded without prior transcript is emitted immediately`() = runTest {
        val emissions = collectTimed(flow { emit(graded) })

        emissions shouldBe listOf(graded to 0L)
    }

    @Test
    fun `error 300 ms after transcript propagates at 1000 ms`() = runTest {
        val failure = IllegalStateException("grading failed")
        val upstream = flow {
            emit(transcriptReady)
            delay(300.milliseconds)
            throw failure
        }

        val thrown = shouldThrow<IllegalStateException> { collectTimed(upstream) }

        thrown shouldBe failure
        currentTime shouldBe 1_000L
    }

    @Test
    fun `error without prior transcript propagates immediately`() = runTest {
        val failure = IllegalStateException("upload failed")

        val thrown = shouldThrow<IllegalStateException> { collectTimed(flow<VoiceAnswerGradingEvent> { throw failure }) }

        thrown shouldBe failure
        currentTime shouldBe 0L
    }

    @Test
    fun `custom minimum display duration is respected`() = runTest {
        val minDisplay = 2_000.milliseconds

        val emissions = collectTimed(gradingFlow(transcriptReady, delayBeforeLast = 300.milliseconds, last = graded), minDisplay)

        emissions shouldBe listOf(transcriptReady to 0L, graded to minDisplay.inWholeMilliseconds)
    }

    private fun gradingFlow(first: VoiceAnswerGradingEvent, delayBeforeLast: Duration, last: VoiceAnswerGradingEvent): Flow<VoiceAnswerGradingEvent> = flow {
        emit(first)
        delay(delayBeforeLast)
        emit(last)
    }

    private suspend fun TestScope.collectTimed(upstream: Flow<VoiceAnswerGradingEvent>, minDisplay: Duration = MIN_TRANSCRIPT_DISPLAY): List<Pair<VoiceAnswerGradingEvent, Long>> {
        val emissions = mutableListOf<Pair<VoiceAnswerGradingEvent, Long>>()
        upstream
            .holdGradedUntilTranscriptShown(minDisplay = minDisplay, timeSource = testScheduler.timeSource)
            .onEach { event -> emissions += event to currentTime }
            .collect()
        return emissions
    }
}
