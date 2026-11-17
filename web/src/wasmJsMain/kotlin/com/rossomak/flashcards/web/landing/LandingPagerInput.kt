package com.rossomak.flashcards.web.landing

import com.rossomak.flashcards.web.browser.BrowserSnapScroller
import kotlin.math.abs
import kotlin.math.roundToInt

/** Keys, page dots and the scroll hint, which smooth-scroll the browser's [scroller] to a page. */
class LandingPagerInput(private val pageCount: Int) {
    /** Null until installed; moves are dropped until then. */
    var scroller: BrowserSnapScroller? = null

    /** The last move's target, so quick steps add up. */
    private var targetPage: Int? = null

    /** False for a non-navigation key, so the browser keeps it. */
    fun onKey(key: String): Boolean {
        val step = PageStep.forKey(key) ?: return false
        step(step)
        return true
    }

    fun step(step: PageStep) {
        val position = scroller?.position() ?: return
        // Mid-scroll, step from the page being scrolled to.
        val from = targetPage?.takeIf { page -> abs(position - page) < 1f } ?: position.roundToInt()
        goTo(step.from(from, pageCount))
    }

    fun goTo(page: Int) {
        val scroller = scroller ?: return
        targetPage = page
        scroller.scrollToPage(page)
    }
}
