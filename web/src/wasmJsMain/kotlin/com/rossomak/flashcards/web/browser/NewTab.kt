package com.rossomak.flashcards.web.browser

/** Opens [url] in a new tab, without giving that page a handle on this one. Call it from a click or key press, or the browser blocks it. */
fun openInNewTab(url: String) {
    openWindow(url)
}

// `url` is read by the JavaScript body, which detekt cannot see.
@Suppress("UnusedParameter")
private fun openWindow(url: String): Unit = js("{ window.open(url, '_blank', 'noopener'); }")
