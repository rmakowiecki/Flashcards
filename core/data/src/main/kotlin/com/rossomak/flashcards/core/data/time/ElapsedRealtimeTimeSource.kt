package com.rossomak.flashcards.core.data.time

import android.os.SystemClock
import kotlin.time.AbstractLongTimeSource
import kotlin.time.DurationUnit

/**
 * A time source that keeps counting while the device sleeps. `TimeSource.Monotonic` reads
 * `System.nanoTime()`, which stops in deep sleep, and a locked phone in a pocket sleeps between the
 * events a voice session waits for. Marks from this source compare and subtract like any other.
 *
 * [readNanos] is the clock; tests replace it.
 */
internal class ElapsedRealtimeTimeSource(
    private val readNanos: () -> Long = SystemClock::elapsedRealtimeNanos,
) : AbstractLongTimeSource(DurationUnit.NANOSECONDS) {

    override fun read(): Long = readNanos()
}
