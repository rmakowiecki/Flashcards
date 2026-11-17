package com.rossomak.flashcards.web.landing

/** A move between the landing pages that a key asks for. */
enum class PageStep {
    Previous,
    Next,
    First,
    Last,
    ;

    /** The page this step lands on from [page], kept within the [pageCount] pages. */
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
        /**
         * The step for a keyboard [key] (a DOM `KeyboardEvent.key`): the arrows and Page Up or Down
         * move one page, Home and End go to the hero and the closing page. Any other key is null, so
         * the browser keeps it.
         */
        fun forKey(key: String): PageStep? = when (key) {
            "ArrowUp", "PageUp" -> Previous
            "ArrowDown", "PageDown" -> Next
            "Home" -> First
            "End" -> Last
            else -> null
        }
    }
}
