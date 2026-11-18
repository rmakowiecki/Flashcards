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
        /** Width over height from which a feature page goes side by side. The phone is sized by height, so shape decides, not width. */
        const val SIDE_BY_SIDE_MIN_ASPECT_RATIO = 0.9f

        fun forWindow(width: Dp, height: Dp, sideBySideMinAspectRatio: Float = SIDE_BY_SIDE_MIN_ASPECT_RATIO): FeatureLayout =
            if (height > 0.dp && width / height >= sideBySideMinAspectRatio) SideBySide else Stacked
    }
}

/** Sizes on the landing page that follow the window. */
object LandingScale {
    private val referenceWidth: Dp = 1280.dp
    private const val MAX_FONT_SCALE = 1.45f
    private const val REFERENCE_ASPECT_RATIO = 16f / 9f

    /** Text growth over the type scale: 1 up to [referenceWidth], then by min(width, height × 16:9), capped. */
    fun fontScale(windowWidth: Dp, windowHeight: Dp): Float =
        (minOf(windowWidth, windowHeight * REFERENCE_ASPECT_RATIO) / referenceWidth).coerceIn(1f, MAX_FONT_SCALE)

    private const val HERO_LOGO_WIDTH_SHARE = 0.46f
    private const val HERO_LOGO_HEIGHT_SHARE = 0.8f
    private val heroLogoMinWidth: Dp = 240.dp
    private val heroLogoMaxWidth: Dp = 960.dp
    private val narrowWindowMargin: Dp = 48.dp

    /** The hero's and closing page's logo: about half the window's width, within the window. */
    fun heroLogoWidth(windowWidth: Dp, windowHeight: Dp): Dp {
        val byWidth = (windowWidth * HERO_LOGO_WIDTH_SHARE).coerceIn(heroLogoMinWidth, heroLogoMaxWidth)
        return minOf(byWidth, windowHeight * HERO_LOGO_HEIGHT_SHARE, windowWidth - narrowWindowMargin)
    }

    /** The feature pages' pinned logo: a seventh of the window, within bounds. */
    fun headerLogoWidth(windowWidth: Dp): Dp = (windowWidth * HEADER_LOGO_WIDTH_SHARE).coerceIn(headerLogoMinWidth, headerLogoMaxWidth)

    private const val HEADER_LOGO_WIDTH_SHARE = 0.14f
    private val headerLogoMinWidth: Dp = 150.dp
    private val headerLogoMaxWidth: Dp = 300.dp

    /** How far the header logo has slid in at pager [position]: 0 on the hero and closing page, 1 on features. */
    fun headerProgress(position: Float, pageCount: Int): Float = minOf(position, (pageCount - 1) - position).coerceIn(0f, 1f)

    /** The hero's scroll hint and links: full on the hero, gone once the scroll is halfway to the next page. */
    fun heroFooterAlpha(position: Float): Float = (1f - position * 2f).coerceIn(0f, 1f)
}

/** A page's text motion. [pageOffset] is the page's distance from the screen in pages, positive once scrolled past. */
object TextParallax {
    /** Share of the page's travel the text lags behind. */
    const val LAG = 0.3f

    /** The text is gone at 1 / FADE_RATE pages away. */
    private const val FADE_RATE = 1.4f

    /** The text's offset from its page, in page heights. */
    fun translationFraction(pageOffset: Float): Float = pageOffset * LAG

    fun alpha(pageOffset: Float): Float = (1f - abs(pageOffset) * FADE_RATE).coerceIn(0f, 1f)
}

/** Timing that exists only on the website. */
object LandingTimings {
    /** The graph never crosses from one page's shape to the next faster than this. */
    val minMorphDuration: Duration = 1.seconds

    /** `scroll-snap-stop: always`, so a fling cannot skip pages. */
    const val STOP_AT_EVERY_PAGE = true

    /** First load: the graph's fade-in before the hero reveals. */
    val entranceGraphFade: Duration = 300.milliseconds
}

/** A page's components reveal when the pages head for it, and replay after it has fully left the window. */
object PageReveal {
    private const val OUT_OF_WINDOW_DISTANCE = 0.999f

    /** True to show, false to hide for the next reveal, null to keep as is. */
    fun shown(isTarget: Boolean, distancePages: Float): Boolean? = when {
        isTarget -> true
        distancePages >= OUT_OF_WINDOW_DISTANCE -> false
        else -> null
    }
}

/** The hero's reveal slots. */
object HeroSlots {
    const val LOGO = 0

    /** The tagline and description, revealed on first load only. */
    const val TEXT = 1
    const val HINT = 2
    const val LINKS = 3
    const val COUNT = 4
}

/** The closing page's reveal slots. */
object ClosingSlots {
    const val LOGO = 0

    /** The tagline and the call to action. */
    const val CALL_TO_ACTION = 1
    const val LINKS = 2
    const val COUNT = 3
}
