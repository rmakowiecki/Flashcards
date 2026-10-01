package com.rossomak.flashcards.core.data.time

import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds
import org.junit.Test

class ElapsedRealtimeTimeSourceTest {

    private var nowNanos = 5_000_000_000L
    private val timeSource = ElapsedRealtimeTimeSource { nowNanos }

    @Test
    fun `a mark measures the time the clock advanced`() {
        val mark = timeSource.markNow()

        nowNanos += 90.seconds.inWholeNanoseconds

        mark.elapsedNow() shouldBe 90.seconds
    }

    @Test
    fun `marks compare and subtract`() {
        val earlier = timeSource.markNow()
        nowNanos += 61.seconds.inWholeNanoseconds
        val later = timeSource.markNow()

        (later > earlier) shouldBe true
        (later - earlier) shouldBe 61.seconds
    }
}
