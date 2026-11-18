package com.rossomak.flashcards.web.landing

import com.rossomak.flashcards.web.browser.BrowserSnapScroller
import kotlin.math.abs
import kotlin.math.roundToInt

/** Keys and the scroll hint, which scroll the browser's [scroller] to a page: smoothly, or at once with [reducedMotion]. */
class LandingPagerInput(private val pageCount: Int) {
    /** Null until installed; moves are dropped until then. */
    var scroller: BrowserSnapScroller? = null

    /** Jump to a page instead of scrolling to it. */
    var reducedMotion: Boolean = false

    /** The page being scrolled to, so quick steps add up. Null once it is reached or the visitor takes over. */
    private var targetPage: Int? = null

    /** False for a non-navigation key, so the browser keeps it. */
    fun onKey(key: String): Boolean {
        val step = PageStep.forKey(key) ?: return false
        step(step)
        return true
    }

    fun step(step: PageStep) {
        val position = scroller?.position() ?: return
        goTo(step.from(targetPage ?: position.roundToInt(), pageCount), position)
    }

    /** The browser scrolled to [position] pages: the target is reached once it is there. */
    fun onPosition(position: Float) {
        val target = targetPage ?: return
        if (abs(position - target) < REACHED_TOLERANCE_PAGES) targetPage = null
    }

    /** The visitor scrolled by hand, which ends the move in progress. */
    fun onUserScroll() {
        targetPage = null
    }

    private fun goTo(page: Int, position: Float) {
        val scroller = scroller ?: return
        targetPage = page.takeIf { abs(position - it) >= REACHED_TOLERANCE_PAGES }
        scroller.scrollToPage(page, smooth = !reducedMotion)
    }

    private companion object {
        const val REACHED_TOLERANCE_PAGES = 0.02f
    }
}
