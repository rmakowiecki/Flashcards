package com.rossomak.flashcards.web.landing

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** How a feature page lays out its text and phone, by window width. */
enum class FeatureLayout {
    /** Text and phone next to each other, on alternating sides. */
    SideBySide,

    /** Text on top, phone below: narrow windows and phones. */
    Stacked,
    ;

    companion object {
        /** Narrower than this, a feature page stacks; the Material expanded width class starts here. */
        val sideBySideMinWidth: Dp = 840.dp

        fun forWindowWidth(width: Dp): FeatureLayout = if (width >= sideBySideMinWidth) SideBySide else Stacked
    }
}

/** Sizes on the landing page that follow the window. */
object LandingScale {
    /** At this window width and below the type scale is used as is; it grows on wider windows. */
    private val referenceWidth: Dp = 1280.dp
    private const val MAX_FONT_SCALE = 1.45f

    /** How much the landing page's text grows over the type scale: 1 up to [referenceWidth], larger on wider windows, capped. */
    fun fontScale(windowWidth: Dp): Float = (windowWidth / referenceWidth).coerceIn(1f, MAX_FONT_SCALE)

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
     * How far the logo has moved from its large place to the header, from 0 to 1, at pager
     * [position]: 0 on the hero and on the closing page, 1 on every feature page, and in between while
     * the pages scroll, so it follows the scroll exactly.
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

    /** Scrolling counts as at rest, momentum included, after this long without a wheel event; then the snap assist settles. */
    val scrollRestDelay: Duration = 180.milliseconds

    /** How long one mouse-wheel notch eases its scroll over, as a browser's smooth scrolling does. */
    val notchScrollDuration: Duration = 140.milliseconds

    /** Every settle or step to a page: short and decisive, so the pages never lag behind the input. */
    val snapDuration: Duration = 300.milliseconds

    /** A strong ease-out (easeOutQuint): the page starts fast and settles without a bounce. */
    val snapEasing: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    /** The animation of every settle and step: the snap assist, keys, dots and touch flings alike. */
    fun <T> snapAnimationSpec(): AnimationSpec<T> = tween(snapDuration.inWholeMilliseconds.toInt(), easing = snapEasing)

    /** First load: the graph fades in, then the hero fades up; about 600ms together. */
    val entranceGraphFade: Duration = 300.milliseconds
    val entranceContentFade: Duration = 300.milliseconds
}
