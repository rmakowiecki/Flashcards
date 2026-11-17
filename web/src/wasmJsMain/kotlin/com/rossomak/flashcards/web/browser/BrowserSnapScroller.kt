// Smooth scrolling and the `scroll` listener are Wasm–JS interop.
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.rossomak.flashcards.web.browser

import kotlinx.browser.document
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
    private val scrollListener: (Event) -> Unit = { onScroll(position()) }

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

    fun position(): Float = if (body.clientHeight > 0) (body.scrollTop / body.clientHeight).toFloat() else 0f

    fun scrollToPage(page: Int) {
        smoothScrollTo(body, page * body.clientHeight.toDouble())
    }

    /** Sets `scroll-snap-stop`: with [always], one fling stops at the next page. */
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
