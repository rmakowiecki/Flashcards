// Smooth scrolling and the `scroll` listener are Wasm–JS interop.
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.rossomak.flashcards.web.browser

import kotlinx.browser.document
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

/**
 * Lets the browser scroll and snap the landing page's pages itself, so a trackpad snap waits for the
 * fingers to lift, which only the browser knows; a page cannot see a trackpad's fingers. While installed, the page's body is a scroll container,
 * [pageCount] window-tall stops long, with mandatory CSS scroll snap; the Compose canvas stays pinned at
 * the top of it (`styles.css`), so the browser's scrolling only moves the scroll position, which
 * [onScroll] receives in pages from the top. The wheel and touch must reach the body unprevented.
 */
class BrowserSnapScroller(pageCount: Int, private val onScroll: (positionPages: Float) -> Unit) {
    private val body: HTMLElement = requireNotNull(document.body) { "index.html has no body to scroll" }
    private val stops: List<HTMLDivElement> = List(pageCount) { index ->
        (document.createElement("div") as HTMLDivElement).apply {
            className = STOP_CLASS
            style.top = "${index * PERCENT}%"
        }
    }
    private val scrollListener: (Event) -> Unit = { onScroll(position()) }

    /** Turns the body into the snapping scroller, scrolled to [startPage]. */
    fun install(startPage: Int) {
        body.classList.add(BODY_CLASS)
        stops.forEach { stop -> body.appendChild(stop) }
        body.scrollTop = startPage * body.clientHeight.toDouble()
        body.addEventListener(SCROLL_EVENT, scrollListener)
    }

    fun uninstall() {
        body.removeEventListener(SCROLL_EVENT, scrollListener)
        stops.forEach { stop -> stop.remove() }
        body.classList.remove(BODY_CLASS)
        body.scrollTop = 0.0
    }

    /** The scroll position in pages from the top. */
    fun position(): Float = if (body.clientHeight > 0) (body.scrollTop / body.clientHeight).toFloat() else 0f

    /** Scrolls smoothly to [page]; the browser's snap keeps it there. */
    fun scrollToPage(page: Int) {
        smoothScrollTo(body, page * body.clientHeight.toDouble())
    }

    /**
     * With [always], every stop holds a scroll (`scroll-snap-stop: always`), so one fling stops at the next
     * page, though a trackpad's leftover momentum may start another scroll; otherwise a fast fling may pass
     * several.
     */
    fun setStopAtEveryPage(always: Boolean) {
        stops.forEach { stop -> stop.style.setProperty(SNAP_STOP_PROPERTY, if (always) SNAP_STOP_ALWAYS else SNAP_STOP_NORMAL) }
    }

    private companion object {
        const val BODY_CLASS = "browser-snap"
        const val STOP_CLASS = "browser-snap-stop"
        const val SCROLL_EVENT = "scroll"
        const val SNAP_STOP_PROPERTY = "scroll-snap-stop"
        const val SNAP_STOP_ALWAYS = "always"
        const val SNAP_STOP_NORMAL = "normal"
        const val PERCENT = 100
    }
}

// The parameters are read by name inside the JavaScript body, which detekt cannot see.
@Suppress("UnusedParameter")
private fun smoothScrollTo(element: HTMLElement, top: Double): Unit = js("element.scrollTo({ top: top, behavior: 'smooth' })")
