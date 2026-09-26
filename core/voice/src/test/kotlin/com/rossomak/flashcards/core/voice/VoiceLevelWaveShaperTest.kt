package com.rossomak.flashcards.core.voice

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceLevelWaveShaperTest {

    private val interval = VoiceLevelWaveShaper.DEFAULT_INTERVAL
    private val level = MutableStateFlow(0f)
    private val isListening = MutableStateFlow(true)

    private fun TestScope.collect(flow: Flow<List<Float>>): List<List<Float>> {
        val emissions = mutableListOf<List<Float>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.toList(emissions) }
        return emissions
    }

    private fun TestScope.advanceBy(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    @Test
    fun `emits the window maximum rather than the last sample`() = runTest {
        val emissions = collect(VoiceLevelWaveShaper().shape(level, isListening))

        level.value = 0.2f
        level.value = 0.9f
        level.value = 0.3f
        advanceBy(interval)

        emissions shouldBe listOf(listOf(0.9f, 0f, 0f, 0f, 0f))
    }

    @Test
    fun `history shifts outward one slot per interval`() = runTest {
        val emissions = collect(VoiceLevelWaveShaper().shape(level, isListening))

        level.value = 0.9f
        advanceBy(interval)
        level.value = 0.4f
        advanceBy(interval)
        level.value = 0.1f
        advanceBy(interval)

        emissions shouldBe listOf(
            listOf(0.9f, 0f, 0f, 0f, 0f),
            listOf(0.9f, 0.9f, 0f, 0f, 0f),
            listOf(0.4f, 0.9f, 0.9f, 0f, 0f),
        )
    }

    @Test
    fun `a level held across a window with no new sample keeps its value`() = runTest {
        val emissions = collect(VoiceLevelWaveShaper().shape(level, isListening))

        level.value = 0.6f
        advanceBy(interval)
        advanceBy(interval)

        emissions.last() shouldBe listOf(0.6f, 0.6f, 0f, 0f, 0f)
    }

    @Test
    fun `emits an all-zero list when listening stops, then nothing more`() = runTest {
        val emissions = collect(VoiceLevelWaveShaper().shape(level, isListening))
        level.value = 0.8f
        advanceBy(interval)

        isListening.value = false
        advanceBy(interval * 5)

        emissions shouldBe listOf(
            listOf(0.8f, 0f, 0f, 0f, 0f),
            listOf(0f, 0f, 0f, 0f, 0f),
        )
    }

    @Test
    fun `restarting listening starts a fresh history`() = runTest {
        val emissions = collect(VoiceLevelWaveShaper().shape(level, isListening))
        level.value = 0.8f
        advanceBy(interval)
        isListening.value = false
        level.value = 0f

        isListening.value = true
        level.value = 0.5f
        advanceBy(interval)

        emissions.last() shouldBe listOf(0.5f, 0f, 0f, 0f, 0f)
    }

    @Test
    fun `produces nothing and subscribes to nothing without a collector`() = runTest {
        VoiceLevelWaveShaper().shape(level, isListening)

        level.value = 0.7f
        advanceBy(interval * 10)

        level.subscriptionCount.value shouldBe 0
        isListening.subscriptionCount.value shouldBe 0
    }

    @Test
    fun `respects a custom bar count and interval`() = runTest {
        val customInterval = 100.milliseconds
        val emissions = collect(VoiceLevelWaveShaper(barCount = 3, interval = customInterval).shape(level, isListening))

        level.value = 0.5f
        advanceBy(interval)
        emissions.shouldBeEmpty()
        advanceBy(customInterval - interval)

        emissions shouldBe listOf(listOf(0.5f, 0f, 0f))
    }
}
