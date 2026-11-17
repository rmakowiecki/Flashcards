package com.rossomak.flashcards.web.browser

/** Where a vertical `wheel` event goes ([WindowWheelHandler]). */
enum class WheelTarget {
    /** The browser scrolls the page with it; Compose never sees it. */
    Browser,

    /** Compose handles it, as over a scrolling pane. */
    Compose,
}
