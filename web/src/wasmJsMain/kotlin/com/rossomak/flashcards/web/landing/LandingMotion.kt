package com.rossomak.flashcards.web.landing

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** How a feature page lays out its text and phone, by the window's shape. */
enum class FeatureLayout {
    /** Text and phone next to each other, on alternating sides. */
    SideBySide,

    /** Text on top, phone below: portrait windows clearly narrower than square, and phones. */
    Stacked,
    ;

    companion object {
        /**
         * A window at least this much wider than tall lays a feature page out side by side; a narrower one
         * stacks. The phone is sized by the window's height, so only the shape, never the size alone, tells
         * whether a text column still fits beside it: a half-width desktop window or a portrait monitor stacks.
         */
        const val SIDE_BY_SIDE_MIN_ASPECT_RATIO = 0.9f

        fun forWindow(width: Dp, height: Dp, sideBySideMinAspectRatio: Float = SIDE_BY_SIDE_MIN_ASPECT_RATIO): FeatureLayout =
            if (height > 0.dp && width / height >= sideBySideMinAspectRatio) SideBySide else Stacked
    }
}

/** Sizes on the landing page that follow the window. */
object LandingScale {
    /** At this window size and below the type scale is used as is; it grows on larger windows. */
    private val referenceWidth: Dp = 1280.dp
    private const val MAX_FONT_SCALE = 1.45f

    /** The widest shape whose width counts in full for the type scale; a taller window counts its height instead. */
    private const val REFERENCE_ASPECT_RATIO = 16f / 9f

    /**
     * How much the landing page's text grows over the type scale: 1 up to [referenceWidth], larger on larger
     * windows, capped. A window narrower than 16:9 counts its height as if it were 16:9 wide, the smaller of
     * the two, so a tall window's text never outgrows its width.
     */
    fun fontScale(windowWidth: Dp, windowHeight: Dp): Float =
        (minOf(windowWidth, windowHeight * REFERENCE_ASPECT_RATIO) / referenceWidth).coerceIn(1f, MAX_FONT_SCALE)

    private const val HERO_LOGO_WIDTH_SHARE = 0.46f
    private const val HERO_LOGO_HEIGHT_SHARE = 0.8f
    private val heroLogoMinWidth: Dp = 240.dp
    private val heroLogoMaxWidth: Dp = 960.dp
    private val narrowWindowMargin: Dp = 48.dp

    /** The logo on the hero and the closing page: about half the window's width, never too tall for the window or too wide for a phone. */
    fun heroLogoWidth(windowWidth: Dp, windowHeight: Dp): Dp {
        val byWidth = (windowWidth * HERO_LOGO_WIDTH_SHARE).coerceIn(heroLogoMinWidth, heroLogoMaxWidth)
        return minOf(byWidth, windowHeight * HERO_LOGO_HEIGHT_SHARE, windowWidth - narrowWindowMargin)
    }

    /** The small logo at the top of the window on every page but the hero and the closing page: a seventh of the window, within bounds. */
    fun headerLogoWidth(windowWidth: Dp): Dp = (windowWidth * HEADER_LOGO_WIDTH_SHARE).coerceIn(headerLogoMinWidth, headerLogoMaxWidth)

    private const val HEADER_LOGO_WIDTH_SHARE = 0.14f
    private val headerLogoMinWidth: Dp = 150.dp
    private val headerLogoMaxWidth: Dp = 300.dp

    /**
     * How far the header logo has slid in, from 0 to 1, at pager [position]: 0 (off screen) on the hero
     * and on the closing page, 1 on every feature page, and in between while the pages scroll, so it
     * follows the scroll exactly.
     */
    fun headerProgress(position: Float, pageCount: Int): Float = minOf(position, (pageCount - 1) - position).coerceIn(0f, 1f)
}

/**
 * The scroll motion of a page's text. [pageOffset] is how far the page is from the one on screen, in
 * pages: 0 on screen, positive once scrolled past (above), negative while still below.
 */
object TextParallax {
    /** Share of the page's travel the text lags behind, so it moves slightly slower than the page. */
    const val LAG = 0.3f

    /** How fast the text fades as its page leaves: fully gone at 1 / FADE_RATE pages away. */
    private const val FADE_RATE = 1.4f

    /** The text's offset from its page, in page heights, down the window. */
    fun translationFraction(pageOffset: Float): Float = pageOffset * LAG

    fun alpha(pageOffset: Float): Float = (1f - abs(pageOffset) * FADE_RATE).coerceIn(0f, 1f)
}

/** Timing that exists only on the website. */
object LandingTimings {
    /** The graph never crosses from one page's shape to the next faster than this. */
    val minMorphDuration: Duration = 1.seconds

    /** Whether a swipe's snap stops at the next page (`scroll-snap-stop: always`); on, so a fling cannot skip pages. */
    const val STOP_AT_EVERY_PAGE = true

    /** First load: the graph fades in over this, then the hero's components reveal one after another. */
    val entranceGraphFade: Duration = 300.milliseconds
}

/**
 * When a landing page's components reveal ([com.rossomak.flashcards.web.components.RevealSequence]):
 * as soon as the pages head for it, and again on every later arrival, once it has left the window
 * entirely in between.
 */
object PageReveal {
    /** A page at least this many pages from the position on screen is out of the window. */
    private const val OUT_OF_WINDOW_DISTANCE = 0.999f

    /**
     * Whether a page's components should now be shown (true) or hidden for their next reveal (false),
     * or null to keep them as they are: [isTarget] when the pages head for it, [distancePages] its
     * distance from the position on screen.
     */
    fun shown(isTarget: Boolean, distancePages: Float): Boolean? = when {
        isTarget -> true
        distancePages >= OUT_OF_WINDOW_DISTANCE -> false
        else -> null
    }
}

/** The hero's components, by the slot in which they reveal ([com.rossomak.flashcards.web.components.RevealSequence]). */
object HeroSlots {
    const val LOGO = 0

    /**
     * The tagline and the description. They reveal in this slot on first load only ([HeroPage]'s
     * `textEntrance`), since their page's scroll motion already moves them on every later arrival.
     */
    const val TEXT = 1
    const val HINT = 2
    const val LINKS = 3
    const val COUNT = 4
}

/** The closing page's components, by the slot in which they reveal. */
object ClosingSlots {
    const val LOGO = 0
    const val LINKS = 1
    const val COUNT = 2
}
