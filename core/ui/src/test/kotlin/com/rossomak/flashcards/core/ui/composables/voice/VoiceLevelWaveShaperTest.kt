package com.rossomak.flashcards.core.ui.composables.voice

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

    private val interval = FlashcardsVoiceCaptureIndicatorDefaults.LEVEL_INTERVAL_MILLIS.milliseconds
    private val level = MutableStateFlow(0f)

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
        val emissions = collect(VoiceLevelWaveShaper().shape(level))

        level.value = 0.2f
        level.value = 0.9f
        level.value = 0.3f
        advanceBy(interval)

        emissions shouldBe listOf(listOf(0.9f, 0f, 0f, 0f, 0f))
    }

    @Test
    fun `history shifts outward one slot per interval`() = runTest {
        val emissions = collect(VoiceLevelWaveShaper().shape(level))

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
        val emissions = collect(VoiceLevelWaveShaper().shape(level))

        level.value = 0.6f
        advanceBy(interval)
        advanceBy(interval)

        emissions.last() shouldBe listOf(0.6f, 0.6f, 0f, 0f, 0f)
    }

    @Test
    fun `a level dropping to zero decays to all zeros, then emits nothing more`() = runTest {
        val emissions = collect(VoiceLevelWaveShaper().shape(level))
        level.value = 0.8f
        advanceBy(interval)

        level.value = 0f
        advanceBy(interval * 10)

        emissions shouldBe listOf(
            listOf(0.8f, 0f, 0f, 0f, 0f),
            listOf(0.8f, 0.8f, 0f, 0f, 0f),
            listOf(0f, 0.8f, 0.8f, 0f, 0f),
            listOf(0f, 0f, 0.8f, 0.8f, 0f),
            listOf(0f, 0f, 0f, 0.8f, 0.8f),
            listOf(0f, 0f, 0f, 0f, 0.8f),
            listOf(0f, 0f, 0f, 0f, 0f),
        )
    }

    @Test
    fun `a silent level emits all zeros once, then nothing more`() = runTest {
        val emissions = collect(VoiceLevelWaveShaper().shape(level))

        advanceBy(interval * 10)

        emissions shouldBe listOf(listOf(0f, 0f, 0f, 0f, 0f))
    }

    @Test
    fun `produces nothing and subscribes to nothing without a collector`() = runTest {
        VoiceLevelWaveShaper().shape(level)

        level.value = 0.7f
        advanceBy(interval * 10)

        level.subscriptionCount.value shouldBe 0
    }

    @Test
    fun `respects a custom bar count and interval`() = runTest {
        val customInterval = 100.milliseconds
        val emissions = collect(VoiceLevelWaveShaper(barCount = 3, interval = customInterval).shape(level))

        level.value = 0.5f
        advanceBy(interval)
        emissions.shouldBeEmpty()
        advanceBy(customInterval - interval)

        emissions shouldBe listOf(listOf(0.5f, 0f, 0f))
    }
}
