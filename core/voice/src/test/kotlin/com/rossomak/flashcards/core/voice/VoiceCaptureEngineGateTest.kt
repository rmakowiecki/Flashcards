package com.rossomak.flashcards.core.voice

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceCaptureEngineGateTest {

    private val speechFrame = ShortArray(VoiceCaptureEngine.FRAME_SIZE_SAMPLES) { SPEECH_SAMPLE }
    private val silentFrame = ShortArray(VoiceCaptureEngine.FRAME_SIZE_SAMPLES)

    private val voiceActivityDetector = CountingVoiceActivityDetector()
    private val engine = VoiceCaptureEngine(
        context = mockk(relaxed = true),
        voiceActivityDetector = voiceActivityDetector,
        voiceObfuscator = IdentityVoiceObfuscator(),
        audioRouteManager = mockk(relaxed = true),
    )
    private val gate = MutableStateFlow(false)
    private val state = VoiceCaptureEngine.UtteranceState()
    private val levelMeter = InputLevelMeter()

    private suspend fun feed(frame: ShortArray, count: Int = 1) {
        repeat(count) {
            engine.handleFrame(frame.copyOf(), frame.size, state, levelMeter, MAX_UTTERANCE_FRAMES) { false }
        }
    }

    @Test
    fun `gated frames reach neither the detector, nor the utterance, nor the pre-roll`() = runTest {
        engine.captureGate = gate
        gate.value = true

        feed(speechFrame, count = 30)
        feed(silentFrame, count = 30)

        voiceActivityDetector.framesSeen shouldBe 0
        state.isInUtterance shouldBe false
        state.frames shouldHaveSize 0
        state.preRoll shouldHaveSize 0
    }

    @Test
    fun `a gated frame shows the microphone at rest`() = runTest {
        engine.captureGate = gate
        feed(speechFrame)
        gate.value = true

        feed(speechFrame)

        engine.inputLevel.value shouldBe 0f
        engine.isSpeechDetected.value shouldBe false
    }

    @Test
    fun `the pre-roll is cleared when the gate opens again`() = runTest {
        engine.captureGate = gate
        feed(silentFrame, count = 5)
        state.preRoll shouldHaveSize 5
        gate.value = true
        feed(silentFrame, count = 3)

        gate.value = false
        feed(silentFrame)

        state.preRoll shouldHaveSize 1
    }

    @Test
    fun `an utterance spans the gate and its trailing silence stands still while the gate is closed`() = runTest(UnconfinedTestDispatcher()) {
        val utterances = mutableListOf<CapturedUtterance>()
        val collecting = launch {
            engine.events.collect { event -> if (event is VoiceCaptureEvent.UtteranceCaptured) utterances += event.utterance }
        }
        engine.captureGate = gate
        feed(speechFrame, count = SPEECH_FRAMES_BEFORE_GATE)
        gate.value = true

        feed(silentFrame, count = FRAMES_LONGER_THAN_END_SILENCE)
        feed(speechFrame, count = FRAMES_LONGER_THAN_END_SILENCE)
        gate.value = false
        feed(speechFrame, count = SPEECH_FRAMES_AFTER_GATE)

        utterances shouldHaveSize 0
        state.isInUtterance shouldBe true
        voiceActivityDetector.framesSeen shouldBe SPEECH_FRAMES_BEFORE_GATE + SPEECH_FRAMES_AFTER_GATE

        feed(silentFrame, count = FRAMES_LONGER_THAN_END_SILENCE)

        utterances shouldHaveSize 1
        utterances.single().durationMs shouldBe
            (SPEECH_FRAMES_BEFORE_GATE + SPEECH_FRAMES_AFTER_GATE + GAP_PAD_FRAMES) * VoiceCaptureEngine.FRAME_DURATION_MS
        collecting.cancel()
    }

    @Test
    fun `an always-open gate leaves capture unchanged`() = runTest {
        feed(speechFrame, count = SPEECH_FRAMES_BEFORE_GATE)

        voiceActivityDetector.framesSeen shouldBe SPEECH_FRAMES_BEFORE_GATE
        state.isInUtterance shouldBe true
        state.frames shouldHaveSize SPEECH_FRAMES_BEFORE_GATE
    }

    private class CountingVoiceActivityDetector : VoiceActivityDetector {
        var framesSeen = 0
            private set

        override fun isSpeech(frame: ShortArray): Boolean {
            framesSeen++
            return frame[0] != 0.toShort()
        }

        override fun reset() = Unit
    }

    private class IdentityVoiceObfuscator : VoiceObfuscator {
        override fun randomizeSessionShift() = Unit

        override fun obfuscate(pcm: ShortArray): ShortArray = pcm.copyOf()
    }

    private companion object {
        const val SPEECH_SAMPLE: Short = 4000
        const val MAX_UTTERANCE_FRAMES = 1500
        const val SPEECH_FRAMES_BEFORE_GATE = 20
        const val SPEECH_FRAMES_AFTER_GATE = 20
        const val FRAMES_LONGER_THAN_END_SILENCE = 200

        // Frames of silence kept after the last speech frame, before the silence is trimmed.
        const val GAP_PAD_FRAMES = 8
    }
}
