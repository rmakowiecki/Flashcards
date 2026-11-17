package com.rossomak.flashcards.web.landing

import com.rossomak.flashcards.web.browser.BrowserSnapScroller
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The landing page's own page moves: keys, the page dots and the hero's scroll hint. The browser scrolls
 * and snaps the pages for the wheel, the trackpad and touch ([BrowserSnap]); these moves scroll it
 * smoothly to a page through [scroller], so they land on the same snap.
 */
class LandingPagerInput(private val pageCount: Int) {
    /** The browser's scroller, while installed; moves before it is are dropped. */
    var scroller: BrowserSnapScroller? = null

    /** The page the last move headed for, so quick steps add up before the scroll arrives; null before any. */
    private var targetPage: Int? = null

    /** Steps for a navigation key; false for any other key, so the browser keeps it. */
    fun onKey(key: String): Boolean {
        val step = PageStep.forKey(key) ?: return false
        step(step)
        return true
    }

    fun step(step: PageStep) {
        val position = scroller?.position() ?: return
        // A step taken while the last one is still scrolling goes on from where that one is heading.
        val from = targetPage?.takeIf { page -> abs(position - page) < 1f } ?: position.roundToInt()
        goTo(step.from(from, pageCount))
    }

    fun goTo(page: Int) {
        val scroller = scroller ?: return
        targetPage = page
        scroller.scrollToPage(page)
    }
}
