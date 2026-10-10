package com.rossomak.flashcards.feature.study.summary

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Every value the payoff animates, frozen at one instant. A payoff starts from one of these:
 * [start] is frame 0 of the sequence, [settled] is the screen's final state, and a preview can build
 * any frame in between.
 */
internal data class SummaryFrame(
    val runningTotal: Float,
    val visibleLineCount: Int,
    val isListShown: Boolean,
    val cardProgress: Float,
    val pour: LevelPosition,
    val showsXpCount: Boolean,
    val sheetProgress: Float,
    val appBarProgress: Float,
    val isSettled: Boolean,
) {
    companion object {
        fun start(before: LevelPosition) = SummaryFrame(
            runningTotal = 0f,
            visibleLineCount = 0,
            isListShown = true,
            cardProgress = 0f,
            pour = before,
            showsXpCount = true,
            sheetProgress = 0f,
            appBarProgress = 0f,
            isSettled = false,
        )

        fun settled(xpTotal: Int, lineCount: Int, after: LevelPosition) = SummaryFrame(
            runningTotal = xpTotal.toFloat(),
            visibleLineCount = lineCount,
            isListShown = false,
            cardProgress = 1f,
            pour = after,
            showsXpCount = true,
            sheetProgress = 1f,
            appBarProgress = 1f,
            isSettled = true,
        )
    }
}

/**
 * The Summary's scripted payoff: the XP tiles, the running total, the Level card and its pour, the
 * sheet and the app bar, played once and ending on the settled screen.
 *
 * This is presentation-only motion, so it lives with the composables and changes no session or screen
 * state. Animated values are `Animatable`s read in draw or layout lambdas where practical; a value
 * the layout depends on (a count, a flag) is Compose state. A payoff built [isSettled] never moves:
 * every value already holds its final reading, which is how reduced motion, a restore and a rotation
 * show the Summary.
 *
 * [play] runs the sequence and returns once it has settled, however it ended. [skip] jumps to the end
 * from anywhere in it. The confetti is deliberately not part of the sequence: [confettiOrigin] only
 * says where a burst starts, and a burst already under way is never cut short by [skip].
 */
@Stable
internal class SummaryPayoff(
    val lines: List<XpBreakdownLine>,
    val xpTotal: Int,
    private val before: LevelPosition,
    private val after: LevelPosition,
    levelsCrossed: List<Int>,
    frame: SummaryFrame,
) {
    private val pourPlan: List<PourSegment> = planPour(before, after, levelsCrossed, xpTotal)
    private val skipRequests = Channel<Unit>(Channel.CONFLATED)

    val runningTotal = Animatable(frame.runningTotal)
    val listAlpha = Animatable(if (frame.isListShown) 1f else 0f)
    val cardProgress = Animatable(frame.cardProgress)
    val sheetProgress = Animatable(frame.sheetProgress)
    val appBarProgress = Animatable(frame.appBarProgress)
    val levelPulse = Animatable(1f)
    val pourFraction = Animatable(frame.pour.fraction)
    val pourXpIntoLevel = Animatable(frame.pour.xpIntoLevel.toFloat())

    var visibleLineCount by mutableIntStateOf(frame.visibleLineCount)
        private set
    var isListShown by mutableStateOf(frame.isListShown)
        private set
    var pourLevel by mutableIntStateOf(frame.pour.level)
        private set
    var pourXpForNextLevel by mutableLongStateOf(frame.pour.xpForNextLevel)
        private set
    var showsXpCount by mutableStateOf(frame.showsXpCount)
        private set

    /** The headline, pills and Level card are on their way in or in: they are read out from here on. */
    var isRevealed by mutableStateOf(frame.cardProgress > 0f)
        private set

    /** The sheet is on its way up or up: Skip is gone and back leaves the screen. */
    var isSheetRaised by mutableStateOf(frame.sheetProgress > 0f)
        private set

    /** Every value is final. The XP breakdown button and the TalkBack announcement wait for this. */
    var isSettled by mutableStateOf(frame.isSettled)
        private set

    /** Where the confetti bursts from, in window coordinates; `null` until, and unless, it fires. */
    var confettiOrigin by mutableStateOf<Offset?>(null)
        private set

    /** The total's text, read live at the moment of the burst: its on-screen position moves as the card drops in. */
    var totalTextCoordinates: LayoutCoordinates? = null

    /** Skip, a tap and the first back all end here. False once the sheet starts to rise, or when settled. */
    val isSkippable: Boolean get() = !isSheetRaised

    fun skip() {
        skipRequests.trySend(Unit)
    }

    /** Plays the sequence, or stops it early on [skip], and leaves every value final. */
    suspend fun play() {
        coroutineScope {
            val sequence = launch { runSequence() }
            val skipWatcher = launch {
                skipRequests.receive()
                sequence.cancel(CancellationException("Summary payoff skipped"))
            }
            sequence.join()
            skipWatcher.cancel()
        }
        snapToSettled()
    }

    private suspend fun runSequence() = coroutineScope {
        if (lines.isNotEmpty()) delay(FIRST_TILE_DELAY)
        var running = 0
        lines.forEachIndexed { index, line ->
            visibleLineCount = index + 1
            running += line.amount
            val runningAfterTile = running
            launch {
                delay(TILE_COUNT_DELAY)
                runningTotal.animateTo(runningAfterTile.toFloat(), tween(TILE_COUNT_DURATION.toMillisInt(), easing = CountEasing))
            }
            delay(TILE_INTERVAL)
        }
        delay(LIST_PAUSE)

        if (xpTotal > 0) confettiOrigin = totalTextCoordinates?.takeIf { it.isAttached }?.boundsInWindow()?.center
        isRevealed = true
        launch {
            listAlpha.animateTo(0f, tween(LIST_FADE.toMillisInt()))
            isListShown = false
        }
        launch { cardProgress.animateTo(1f, tween(CARD_ENTER.toMillisInt(), easing = SlideEasing)) }
        launch {
            delay(POUR_START)
            pour()
        }
        launch {
            delay(SHEET_START)
            isSheetRaised = true
            launch { appBarProgress.animateTo(1f, tween(APP_BAR_FADE.toMillisInt(), easing = SlideEasing)) }
            sheetProgress.animateTo(1f, tween(SHEET_RISE.toMillisInt(), easing = SlideEasing))
        }
    }

    /** Pulses run beside the next segment, so a cut never waits for its own pulse. */
    private suspend fun pour() = coroutineScope {
        pourPlan.forEachIndexed { index, segment ->
            if (index > 0) launch { pulseLevel() }
            pourLevel = segment.level
            showsXpCount = segment.showXpCount
            pourXpForNextLevel = segment.xpForNextLevel
            pourFraction.snapTo(segment.fromFraction)
            pourXpIntoLevel.snapTo(segment.xpFrom.toFloat())
            pourSegment(segment)
        }
    }

    private suspend fun pourSegment(segment: PourSegment) {
        if (segment.duration <= Duration.ZERO) {
            pourFraction.snapTo(segment.toFraction)
            pourXpIntoLevel.snapTo(segment.xpTo.toFloat())
            return
        }
        val spec = tween<Float>(segment.duration.toMillisInt(), easing = CountEasing)
        coroutineScope {
            launch { pourFraction.animateTo(segment.toFraction, spec) }
            launch { pourXpIntoLevel.animateTo(segment.xpTo.toFloat(), spec) }
        }
    }

    private suspend fun pulseLevel() {
        val half = tween<Float>((LEVEL_PULSE / 2).toMillisInt())
        levelPulse.animateTo(LEVEL_PULSE_SCALE, half)
        levelPulse.animateTo(1f, half)
    }

    private suspend fun snapToSettled() {
        runningTotal.snapTo(xpTotal.toFloat())
        listAlpha.snapTo(0f)
        cardProgress.snapTo(1f)
        levelPulse.snapTo(1f)
        pourFraction.snapTo(after.fraction)
        pourXpIntoLevel.snapTo(after.xpIntoLevel.toFloat())
        sheetProgress.snapTo(1f)
        appBarProgress.snapTo(1f)
        visibleLineCount = lines.size
        isListShown = false
        pourLevel = after.level
        pourXpForNextLevel = after.xpForNextLevel
        showsXpCount = true
        isRevealed = true
        isSheetRaised = true
        isSettled = true
    }

    companion object {
        /** A payoff with nothing to play, for a Summary that has no score or that opens already settled. */
        fun settled(
            lines: List<XpBreakdownLine>,
            xpTotal: Int,
            before: LevelPosition,
            after: LevelPosition,
            levelsCrossed: List<Int>,
        ) = SummaryPayoff(lines, xpTotal, before, after, levelsCrossed, SummaryFrame.settled(xpTotal, lines.size, after))

        /** A payoff at frame 0, ready for [play]. */
        fun unplayed(
            lines: List<XpBreakdownLine>,
            xpTotal: Int,
            before: LevelPosition,
            after: LevelPosition,
            levelsCrossed: List<Int>,
        ) = SummaryPayoff(lines, xpTotal, before, after, levelsCrossed, SummaryFrame.start(before))
    }
}
