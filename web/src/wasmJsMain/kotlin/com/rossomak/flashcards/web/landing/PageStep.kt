package com.rossomak.flashcards.web.landing

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** A move between the landing pages that a key or a mouse-wheel notch asks for. */
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

/** What a browser `wheel` event means for the pager: how far to scroll, and whether to ease there or move at once. */
sealed interface WheelInput {
    val deltaPx: Float

    /** One notch of a classic mouse wheel: the pages scroll [deltaPx] down the window, eased over a moment, as a browser scrolls. */
    data class Notch(override val deltaPx: Float) : WheelInput

    /** A trackpad's continuous scroll, momentum included: the pages move [deltaPx] at once, following the fingers. */
    data class Glide(override val deltaPx: Float) : WheelInput

    companion object {
        /**
         * Reads one `wheel` event. Browsers report no device, so this uses the usual heuristic:
         * a trackpad reports pixels whose legacy `wheelDelta` is exactly -3 times `deltaY`, while a
         * mouse notch reports lines (Firefox) or a `wheelDelta` of a fixed notch size (Chrome,
         * Safari). A delta in lines or pages is turned into pixels with [pageSizePx]. An event
         * without vertical movement is null.
         */
        fun from(deltaY: Double, deltaMode: Int, wheelDeltaY: Double, pageSizePx: Float): WheelInput? {
            if (deltaY == 0.0) return null
            val deltaPx = when (deltaMode) {
                DOM_DELTA_LINE -> deltaY.toFloat() * LINE_HEIGHT_PX
                DOM_DELTA_PAGE -> deltaY.toFloat() * pageSizePx
                else -> deltaY.toFloat()
            }
            val isTrackpad = deltaMode == DOM_DELTA_PIXEL &&
                (wheelDeltaY == 0.0 || abs(wheelDeltaY + TRACKPAD_WHEEL_DELTA_RATIO * deltaY) <= TRACKPAD_RATIO_TOLERANCE)
            return if (isTrackpad) Glide(deltaPx) else Notch(deltaPx)
        }

        private const val DOM_DELTA_PIXEL = 0
        private const val DOM_DELTA_LINE = 1
        private const val DOM_DELTA_PAGE = 2

        /** How far one line of a line-based wheel scrolls, as browsers commonly scroll it. */
        private const val LINE_HEIGHT_PX = 40f
        private const val TRACKPAD_WHEEL_DELTA_RATIO = 3.0
        private const val TRACKPAD_RATIO_TOLERANCE = 1.5
    }
}

/**
 * The snap assist: once the visitor's scrolling has come to rest anywhere between two pages, the
 * pages ease to the nearest one. The direction of the last movement leans the choice toward the page
 * it was heading for, so a few notches or a short swipe toward the next page land there instead of
 * bouncing back, while a small nudge settles back on the page it left.
 */
object SnapAssist {
    /** How far toward the page it was heading for a resting position is read, in pages. */
    const val DIRECTION_LEAN = 0.3f

    /** The page to settle on from [position] (page plus offset fraction), last moving in [direction] (-1 up, 1 down, 0 none). */
    fun settlePage(position: Float, direction: Int, pageCount: Int): Int =
        (position + direction.sign * DIRECTION_LEAN).roundToInt().coerceIn(0, pageCount - 1)
}
