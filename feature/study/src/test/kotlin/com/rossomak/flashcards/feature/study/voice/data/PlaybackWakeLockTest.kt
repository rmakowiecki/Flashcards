package com.rossomak.flashcards.feature.study.voice.data

import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackWakeLockTest {

    private val renewInterval = 5.minutes
    private var acquireCount = 0
    private var releaseCount = 0

    @Test
    fun `the lock is acquired at once when playing starts`() = runTest {
        val isPlaying = MutableStateFlow(true)
        backgroundScope.launch { isPlaying.renewWhileTrue(renewInterval, ::onAcquire, ::onRelease) }

        runCurrent()

        acquireCount shouldBe 1
    }

    @Test
    fun `the lock is renewed every interval while play continues without a new state`() = runTest {
        val isPlaying = MutableStateFlow(true)
        backgroundScope.launch { isPlaying.renewWhileTrue(renewInterval, ::onAcquire, ::onRelease) }

        runCurrent()
        advanceTimeBy(renewInterval * 3 + 1.minutes)

        acquireCount shouldBe 4
        releaseCount shouldBe 0
    }

    @Test
    fun `the lock is released when playing stops and no longer renewed`() = runTest {
        val isPlaying = MutableStateFlow(true)
        backgroundScope.launch { isPlaying.renewWhileTrue(renewInterval, ::onAcquire, ::onRelease) }
        runCurrent()

        isPlaying.value = false
        runCurrent()
        advanceTimeBy(renewInterval * 3)

        releaseCount shouldBe 1
        acquireCount shouldBe 1
    }

    @Test
    fun `the lock is released at once when the flow starts out not playing`() = runTest {
        val isPlaying = MutableStateFlow(false)
        backgroundScope.launch { isPlaying.renewWhileTrue(renewInterval, ::onAcquire, ::onRelease) }

        runCurrent()

        releaseCount shouldBe 1
        acquireCount shouldBe 0
    }

    private fun onAcquire() {
        acquireCount++
    }

    private fun onRelease() {
        releaseCount++
    }
}
