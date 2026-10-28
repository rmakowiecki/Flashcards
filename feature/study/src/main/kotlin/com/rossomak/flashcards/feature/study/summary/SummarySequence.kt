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

/** The Summary's sequence steps, in play order. */
enum class SummaryPhase {
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
 * Presentation-only script: holds the [phase] and shown tile count, and each element animates itself from
 * those. A settled sequence never moves (reduced motion, restore, rotation). After a [skip], [wasSkipped]
 * lets the Level card show its after position without a pour.
 */
@Stable
class SummarySequence(
    val lines: List<XpBreakdownLine>,
    val xpTotal: Int,
    phase: SummaryPhase,
    visibleLineCount: Int = if (phase == SummaryPhase.Counting) 0 else lines.size,
) {
    var phase by mutableStateOf(phase)
        private set

    var visibleLineCount by mutableIntStateOf(visibleLineCount)
        private set

    var wasSkipped by mutableStateOf(false)
        private set

    /** Where the confetti bursts from, in window coordinates; `null` until, and unless, it fires. */
    var confettiOrigin by mutableStateOf<Offset?>(null)
        private set

    /** The total's text, for the burst position. Plain field despite [Stable]: no composition reads it. */
    var totalTextCoordinates: LayoutCoordinates? = null

    val runningTotal: Int get() = lines.take(visibleLineCount).sumOf { it.amount }

    val isSettled: Boolean get() = phase == SummaryPhase.Settled

    val showsLevelAfter: Boolean get() = phase >= SummaryPhase.Pouring

    fun skip() {
        if (isSettled) return
        wasSkipped = true
        visibleLineCount = lines.size
        phase = SummaryPhase.Settled
    }

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
        fun settled(lines: List<XpBreakdownLine>, xpTotal: Int) = SummarySequence(lines, xpTotal, SummaryPhase.Settled)

        fun unplayed(lines: List<XpBreakdownLine>, xpTotal: Int) = SummarySequence(lines, xpTotal, SummaryPhase.Counting)
    }
}
