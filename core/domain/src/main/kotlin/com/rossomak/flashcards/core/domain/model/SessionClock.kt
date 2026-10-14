package com.rossomak.flashcards.core.domain.model

import java.time.Duration
import java.time.Instant

/**
 * A Study Session's elapsed-time accumulator — an immutable snapshot, in the same style as
 * [RatedSessionState]: [startClock], [stopClock] and [elapsedSeconds] are top-level pure functions
 * that take a snapshot in and return the next one (or, for [elapsedSeconds], a value), so a
 * ViewModel holds a `var` and reassigns it.
 *
 * Every operation takes the current time as a parameter; this type never reads a system clock
 * itself, which is what makes it testable with no dispatcher, fake clock or rule.
 *
 * The *policies* — starting at first card shown rather than route entry, never pausing until
 * termination, excluding time spent on the Summary screen — are wiring
 * decisions the two session ViewModels own. This type only has to make them expressible: call
 * [stopClock] whenever a policy says time should stop accruing, [startClock] when it should resume.
 *
 * @param accumulated total elapsed time across every completed running span, kept as a [Duration]
 * rather than rounded to seconds until [elapsedSeconds] reports it — rounding happens exactly once,
 * there, so two callers reporting two different spans can never round differently.
 * @param runningSince the instant the current running span began; `null` while stopped.
 */
data class SessionClock(
    val accumulated: Duration = Duration.ZERO,
    val runningSince: Instant? = null,
) {
    val isRunning: Boolean get() = runningSince != null
}

/** Starts [clock] running at [at]. A no-op — not a double-count — if it is already running. */
fun startClock(clock: SessionClock, at: Instant): SessionClock =
    if (clock.isRunning) clock else clock.copy(runningSince = at)

/**
 * Stops [clock] at [at], folding the just-finished running span into [SessionClock.accumulated]. A
 * no-op if it is already stopped. The gap between this call and the next [startClock] does not
 * accrue — that is the whole point of stopping.
 */
fun stopClock(clock: SessionClock, at: Instant): SessionClock {
    val since = clock.runningSince ?: return clock
    return SessionClock(accumulated = clock.accumulated + spanSince(since, at))
}

/**
 * [clock]'s total elapsed time as of [at], in whole seconds. Includes the current running span when
 * [clock] is running; excludes it when [clock] is stopped.
 */
fun elapsedSeconds(clock: SessionClock, at: Instant): Int {
    val running = clock.runningSince?.let { spanSince(it, at) } ?: Duration.ZERO
    return (clock.accumulated + running).seconds.toInt()
}

/**
 * The span from [start] to [at], clamped to zero rather than negative — a caller passing a
 * non-monotonic instant (a system clock that jumped backward) must never make elapsed time go
 * negative.
 */
private fun spanSince(start: Instant, at: Instant): Duration {
    val span = Duration.between(start, at)
    return if (span.isNegative) Duration.ZERO else span
}
