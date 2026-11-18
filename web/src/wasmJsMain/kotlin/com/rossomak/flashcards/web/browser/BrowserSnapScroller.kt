// Smooth scrolling and the `scroll` listener are Wasm–JS interop.
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.rossomak.flashcards.web.browser

import kotlin.math.abs
import kotlin.math.round
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

/**
 * Makes the body a CSS scroll-snap container with [pageCount] window-tall stops, so the browser snaps
 * and waits for lifted fingers. The canvas stays pinned (`styles.css`). [onScroll] gets the position in pages.
 */
class BrowserSnapScroller(pageCount: Int, private val onScroll: (positionPages: Float) -> Unit) {
    private val body: HTMLElement = requireNotNull(document.body) { "index.html has no body to scroll" }
    private val stops: List<HTMLDivElement> = List(pageCount) { index ->
        (document.createElement("div") as HTMLDivElement).apply {
            className = STOP_CLASS
            style.top = "${index * PERCENT}%"
        }
    }
    private val scrollListener: (Event) -> Unit = {
        onScroll(position())
        // A re-align waits for the scrolling it follows a resize with.
        if (realignTimer != null) scheduleRealign()
    }
    private val resizeListener: (Event) -> Unit = { scheduleRealign() }
    private val touchStartListener: (Event) -> Unit = { fingerDown = true }
    private val touchEndListener: (Event) -> Unit = { fingerDown = false }
    private var fingerDown = false
    private var realignTimer: Int? = null

    fun install(startPage: Int) {
        body.classList.add(BODY_CLASS)
        stops.forEach { stop -> body.appendChild(stop) }
        body.scrollTop = startPage * body.clientHeight.toDouble()
        body.addEventListener(SCROLL_EVENT, scrollListener)
        window.addEventListener(RESIZE_EVENT, resizeListener)
        body.addEventListener(TOUCH_START_EVENT, touchStartListener)
        body.addEventListener(TOUCH_END_EVENT, touchEndListener)
        body.addEventListener(TOUCH_CANCEL_EVENT, touchEndListener)
    }

    fun uninstall() {
        realignTimer?.let { timer -> window.clearTimeout(timer) }
        body.removeEventListener(SCROLL_EVENT, scrollListener)
        window.removeEventListener(RESIZE_EVENT, resizeListener)
        body.removeEventListener(TOUCH_START_EVENT, touchStartListener)
        body.removeEventListener(TOUCH_END_EVENT, touchEndListener)
        body.removeEventListener(TOUCH_CANCEL_EVENT, touchEndListener)
        stops.forEach { stop -> stop.remove() }
        body.classList.remove(BODY_CLASS)
        body.scrollTop = 0.0
    }

    fun position(): Float = if (body.clientHeight > 0) (body.scrollTop / body.clientHeight).toFloat() else 0f

    fun scrollToPage(page: Int) {
        smoothScrollTo(body, page * body.clientHeight.toDouble())
    }

    /** Sets `scroll-snap-stop`: with [always], one fling stops at the next page. */
    fun setStopAtEveryPage(always: Boolean) {
        stops.forEach { stop -> stop.style.setProperty(SNAP_STOP_PROPERTY, if (always) SNAP_STOP_ALWAYS else SNAP_STOP_NORMAL) }
    }

    /**
     * A mobile browser's address bar changes the window's height as it hides or shows, so the scroll offset
     * in pixels no longer sits on a page. Once resizing and scrolling are idle and no finger is down, jump to
     * the nearest page: the browser's own snap alone can leave the last page off by the bar's height.
     */
    private fun scheduleRealign() {
        realignTimer?.let { timer -> window.clearTimeout(timer) }
        realignTimer = window.setTimeout({
            realignTimer = null
            realign()
            null
        }, REALIGN_DELAY_MS)
    }

    private fun realign() {
        if (fingerDown) {
            scheduleRealign()
            return
        }
        val height = body.clientHeight.toDouble()
        if (height <= 0.0) return
        val target = round(body.scrollTop / height) * height
        if (abs(body.scrollTop - target) > REALIGN_TOLERANCE_PX) body.scrollTop = target
    }

    private companion object {
        const val BODY_CLASS = "browser-snap"
        const val STOP_CLASS = "browser-snap-stop"
        const val SCROLL_EVENT = "scroll"
        const val RESIZE_EVENT = "resize"
        const val TOUCH_START_EVENT = "touchstart"
        const val TOUCH_END_EVENT = "touchend"
        const val TOUCH_CANCEL_EVENT = "touchcancel"
        const val REALIGN_DELAY_MS = 150
        const val REALIGN_TOLERANCE_PX = 0.5
        const val SNAP_STOP_PROPERTY = "scroll-snap-stop"
        const val SNAP_STOP_ALWAYS = "always"
        const val SNAP_STOP_NORMAL = "normal"
        const val PERCENT = 100
    }
}

// The parameters are read by name inside the JavaScript body, which detekt cannot see.
@Suppress("UnusedParameter")
private fun smoothScrollTo(element: HTMLElement, top: Double): Unit = js("element.scrollTo({ top: top, behavior: 'smooth' })")
