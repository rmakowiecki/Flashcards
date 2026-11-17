package com.rossomak.flashcards.web.landing

/** A keyboard move between landing pages. */
enum class PageStep {
    Previous,
    Next,
    First,
    Last,
    ;

    /** The target page from [page], clamped. */
    fun from(page: Int, pageCount: Int): Int {
        val lastPage = pageCount - 1
        return when (this) {
            Previous -> page - 1
            Next -> page + 1
            First -> 0
            Last -> lastPage
        }.coerceIn(0, lastPage)
    }

    companion object {
        /** The step for a DOM `KeyboardEvent.key`, or null to leave it to the browser. */
        fun forKey(key: String): PageStep? = when (key) {
            "ArrowUp", "PageUp" -> Previous
            "ArrowDown", "PageDown" -> Next
            "Home" -> First
            "End" -> Last
            else -> null
        }
    }
}
