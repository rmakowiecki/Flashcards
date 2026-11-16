package com.rossomak.flashcards.web.landing

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.pager.PagerState
import com.rossomak.flashcards.web.browser.WheelReading
import com.rossomak.flashcards.web.landing.WheelInput.Glide
import com.rossomak.flashcards.web.landing.WheelInput.Notch
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Turns window input into pager moves. The wheel scrolls the pages like any page: a trackpad moves
 * them by its own delta in the same event, momentum included, and a mouse-wheel notch eases them by
 * its delta. Nothing holds the pages to a page while input continues; once it has rested for
 * [LandingTimings.scrollRestDelay], [SnapAssist] settles them on the nearest page. Keys and the page
 * dots step page by page. Every settle and step runs [LandingTimings.snapAnimationSpec] and starts in
 * the event that asked for it; the graph behind follows on its own. Touch drags are the pager's own.
 */
class LandingPagerInput(private val pagerState: PagerState, private val scope: CoroutineScope) {
    private var motionJob: Job? = null
    private var restJob: Job? = null

    /** Where the notches still easing are taking the pages, in pixels from the top, so quick notches add up; null when none is. */
    private var notchTargetPx: Float? = null
    private var lastDirection = 0

    fun onWheel(reading: WheelReading) {
        val pageSizePx = (pagerState.layoutInfo.pageSize + pagerState.layoutInfo.pageSpacing).toFloat()
        if (pageSizePx <= 0f) return
        val input = WheelInput.from(deltaY = reading.deltaY, deltaMode = reading.deltaMode, wheelDeltaY = reading.wheelDeltaY, pageSizePx = pageSizePx) ?: return
        restJob?.cancel()
        lastDirection = input.deltaPx.sign.toInt()
        when (input) {
            is Glide -> {
                // Stops a settle still running and moves the pages in this very event, 1:1 with the trackpad.
                motionJob?.cancel()
                notchTargetPx = null
                pagerState.dispatchRawDelta(input.deltaPx)
            }
            is Notch -> {
                val target = (notchTargetPx ?: (pagerState.position() * pageSizePx)) + input.deltaPx
                notchTargetPx = target
                motionJob?.cancel()
                motionJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    val distance = target - pagerState.position() * pageSizePx
                    pagerState.animateScrollBy(distance, tween(LandingTimings.notchScrollDuration.inWholeMilliseconds.toInt(), easing = LandingTimings.snapEasing))
                    notchTargetPx = null
                }
            }
        }
        restJob = scope.launch {
            delay(LandingTimings.scrollRestDelay)
            settle()
        }
    }

    /** Steps for a navigation key; false for any other key, so the browser keeps it. */
    fun onKey(key: String): Boolean {
        val step = PageStep.forKey(key) ?: return false
        step(step)
        return true
    }

    fun step(step: PageStep) {
        goTo(step.from(pagerState.targetPage, pagerState.pageCount))
    }

    fun goTo(page: Int) {
        restJob?.cancel()
        notchTargetPx = null
        animateTo(page)
    }

    private fun settle() {
        val page = SnapAssist.settlePage(pagerState.position(), lastDirection, pagerState.pageCount)
        if (pagerState.position() != page.toFloat()) animateTo(page)
    }

    /** Runs undispatched, so the pages start moving in the event that asked for it, not a frame later. */
    private fun animateTo(page: Int) {
        notchTargetPx = null
        motionJob?.cancel()
        motionJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            pagerState.animateScrollToPage(page, animationSpec = LandingTimings.snapAnimationSpec())
        }
    }

    private fun PagerState.position(): Float = currentPage + currentPageOffsetFraction
}
