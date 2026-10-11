package com.rossomak.flashcards.feature.study.summary

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import kotlin.time.Duration
import kotlinx.coroutines.delay

/** The steps of the Summary's sequence, in the order they play. Every element animates itself from the step it is in. */
internal enum class SummaryPhase {
    /** The XP tiles appear one by one and the total counts up with them. */
    Counting,

    /** Confetti, the tiles leave, and the Level card, headline and pills come in. */
    Revealing,

    /** The Level card switches to the position after the session and pours it. */
    Pouring,

    /** The sheet and the app bar come in, and every value is final. */
    Settled,
}

/**
 * The Summary's scripted sequence, played once and ending on the settled screen. It holds only the step
 * the sequence is in and how many tiles are shown; each element animates itself from those, and the
 * timings live in `SummaryTimeline.kt`. Presentation-only, so it lives with the
 * composables and changes no session or screen state.
 *
 * A sequence built [isSettled] never moves, which is how reduced motion, a restore and a rotation show the
 * Summary. [skip] jumps to the end from anywhere before it: the elements finish with their own short
 * transitions, and [wasSkipped] tells the screen to give the Level card a fresh identity, so it shows the
 * after position without a pour. The confetti is not part of the sequence: [confettiOrigin] only says
 * where a burst starts, and a skip never cuts one short.
 */
@Stable
internal class SummarySequence(
    val lines: List<XpBreakdownLine>,
    val xpTotal: Int,
    phase: SummaryPhase,
    visibleLineCount: Int = if (phase == SummaryPhase.Counting) 0 else lines.size,
) {
    var phase by mutableStateOf(phase)
        private set

    var visibleLineCount by mutableIntStateOf(visibleLineCount)
        private set

    /** The sequence was skipped rather than played to its end. */
    var wasSkipped by mutableStateOf(false)
        private set

    /** Where the confetti bursts from, in window coordinates; `null` until, and unless, it fires. */
    var confettiOrigin by mutableStateOf<Offset?>(null)
        private set

    /**
     * The total's text, read at the moment of the burst. A plain field, not snapshot state, despite
     * [Stable]: only the sequence reads it, never a composition.
     */
    var totalTextCoordinates: LayoutCoordinates? = null

    /** The total the shown tiles add up to. */
    val runningTotal: Int get() = lines.take(visibleLineCount).sumOf { it.amount }

    val isSettled: Boolean get() = phase == SummaryPhase.Settled

    /** The Level card shows the position after the session; before it, the position the session started from. */
    val showsLevelAfter: Boolean get() = phase >= SummaryPhase.Pouring

    fun skip() {
        if (isSettled) return
        wasSkipped = true
        visibleLineCount = lines.size
        phase = SummaryPhase.Settled
    }

    /** Plays the sequence to [SummaryPhase.Settled]. A [skip] makes every remaining step a no-op. */
    suspend fun play() {
        for (index in lines.indices) {
            if (!waitUnskipped(TILE_INTERVAL)) return
            visibleLineCount = index + 1
        }
        if (!waitUnskipped(LIST_PAUSE)) return
        if (xpTotal > 0) confettiOrigin = totalTextCoordinates?.takeIf { it.isAttached }?.boundsInWindow()?.center
        phase = SummaryPhase.Revealing
        if (!waitUnskipped(POUR_START)) return
        phase = SummaryPhase.Pouring
        if (!waitUnskipped(SHEET_START)) return
        phase = SummaryPhase.Settled
    }

    private suspend fun waitUnskipped(duration: Duration): Boolean {
        delay(duration)
        return !isSettled
    }

    companion object {
        /** A sequence with nothing to play, for a Summary that has no score or that opens already settled. */
        fun settled(lines: List<XpBreakdownLine>, xpTotal: Int) = SummarySequence(lines, xpTotal, SummaryPhase.Settled)

        /** A sequence before its first tile, ready for [play]. */
        fun unplayed(lines: List<XpBreakdownLine>, xpTotal: Int) = SummarySequence(lines, xpTotal, SummaryPhase.Counting)
    }
}
