package com.rossomak.flashcards.feature.study.voice.data

import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.model.GradingFailureReason
import com.rossomak.flashcards.core.domain.model.SpokenNotice
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoticeSpeakerTest {

    private val feedback = SpokenNotice.Feedback(FlashcardAttemptRating.Correct, "Right.")
    private val finished = mutableListOf<SpokenNotice>()
    private var engineUnavailableCount = 0
    private lateinit var engine: FakeNoticeEngine

    private fun TestScope.createSpeaker(isEngineReady: Boolean? = true): NoticeSpeaker {
        val speaker = NoticeSpeaker(
            scope = backgroundScope,
            resolveText = { notice -> notice.toString() },
            onNoticeFinished = { notice -> finished += notice },
            onEngineUnavailable = { engineUnavailableCount++ },
            engineFactory = { listener -> FakeNoticeEngine(listener).also { engine = it } },
        )
        isEngineReady?.let { engine.listener.onInitialized(it) }
        runCurrent()
        return speaker
    }

    @Test
    fun `a notice finishes when the engine reports it done`() = runTest {
        val speaker = createSpeaker()

        speaker.speak(SpokenNotice.SilenceSkip)
        engine.listener.onUtteranceEnded(engine.spokenUtteranceIds.single())
        runCurrent()

        finished shouldBe listOf(SpokenNotice.SilenceSkip)
    }

    @Test
    fun `a short notice the engine never reports is given up on 5 seconds after speaking`() = runTest {
        val speaker = createSpeaker()

        speaker.speak(SpokenNotice.GradingFailed(GradingFailureReason.NoConnection))
        advanceTimeBy(NoticeSpeaker.WATCHDOG_TIMEOUT - 1.milliseconds)
        finished shouldBe emptyList()

        advanceTimeBy(2.milliseconds)
        finished shouldBe listOf(SpokenNotice.GradingFailed(GradingFailureReason.NoConnection))
    }

    @Test
    fun `a short notice is given up on even after the engine started it`() = runTest {
        val speaker = createSpeaker()

        speaker.speak(SpokenNotice.SilencePause)
        engine.listener.onUtteranceStarted(engine.spokenUtteranceIds.single())
        advanceTimeBy(NoticeSpeaker.WATCHDOG_TIMEOUT + 1.milliseconds)

        finished shouldBe listOf(SpokenNotice.SilencePause)
    }

    @Test
    fun `feedback the engine never starts is given up on after 5 seconds`() = runTest {
        val speaker = createSpeaker()

        speaker.speak(feedback)
        advanceTimeBy(NoticeSpeaker.WATCHDOG_TIMEOUT + 1.milliseconds)

        finished shouldBe listOf(feedback)
    }

    @Test
    fun `feedback the engine started outlives the short watchdog and finishes when the engine reports it done`() = runTest {
        val speaker = createSpeaker()

        speaker.speak(feedback)
        engine.listener.onUtteranceStarted(engine.spokenUtteranceIds.single())
        advanceTimeBy(NoticeSpeaker.STARTED_FEEDBACK_TIMEOUT - 1.milliseconds)
        finished shouldBe emptyList()

        engine.listener.onUtteranceEnded(engine.spokenUtteranceIds.single())
        runCurrent()
        finished shouldBe listOf(feedback)
        engine.stopCount shouldBe 0
    }

    @Test
    fun `feedback still speaking 20 seconds after it started is cut off and finished`() = runTest {
        val speaker = createSpeaker()

        speaker.speak(feedback)
        engine.listener.onUtteranceStarted(engine.spokenUtteranceIds.single())
        advanceTimeBy(NoticeSpeaker.STARTED_FEEDBACK_TIMEOUT + 1.milliseconds)

        engine.stopCount shouldBe 1
        finished shouldBe listOf(feedback)

        engine.listener.onUtteranceEnded(engine.spokenUtteranceIds.single())
        runCurrent()
        finished shouldBe listOf(feedback)
    }

    @Test
    fun `a late engine callback after the watchdog fired is dropped`() = runTest {
        val speaker = createSpeaker()

        speaker.speak(SpokenNotice.CaptureFailed)
        advanceTimeBy(NoticeSpeaker.WATCHDOG_TIMEOUT + 1.milliseconds)
        engine.listener.onUtteranceEnded(engine.spokenUtteranceIds.single())
        runCurrent()

        finished shouldBe listOf(SpokenNotice.CaptureFailed)
    }

    @Test
    fun `notices finish in the order they were spoken, even when a later one is given up on first`() = runTest {
        val speaker = createSpeaker()

        speaker.speak(feedback)
        engine.listener.onUtteranceStarted(engine.spokenUtteranceIds[0])
        speaker.speak(SpokenNotice.CaptureFailed)
        advanceTimeBy(NoticeSpeaker.WATCHDOG_TIMEOUT + 1.milliseconds)
        finished shouldBe emptyList()

        engine.listener.onUtteranceEnded(engine.spokenUtteranceIds[0])
        runCurrent()

        finished shouldBe listOf(feedback, SpokenNotice.CaptureFailed)
    }

    @Test
    fun `a notice spoken before the engine is ready finishes at once`() = runTest {
        val speaker = createSpeaker(isEngineReady = null)

        speaker.speak(SpokenNotice.SilenceSkip)

        finished shouldBe listOf(SpokenNotice.SilenceSkip)
        engine.spokenUtteranceIds shouldBe emptyList()
    }

    @Test
    fun `an engine that fails to start is reported, and later notices finish at once`() = runTest {
        val speaker = createSpeaker(isEngineReady = false)

        speaker.speak(feedback)

        engineUnavailableCount shouldBe 1
        finished shouldBe listOf(feedback)
    }

    private class FakeNoticeEngine(val listener: NoticeEngineListener) : NoticeEngine {
        val spokenUtteranceIds = mutableListOf<String>()
        var stopCount = 0

        override fun speak(text: String, utteranceId: String) {
            spokenUtteranceIds += utteranceId
        }

        override fun stop() {
            stopCount++
        }

        override fun shutdown() = Unit
    }
}
