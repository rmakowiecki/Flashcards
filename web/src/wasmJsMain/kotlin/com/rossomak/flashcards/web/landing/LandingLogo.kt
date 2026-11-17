package com.rossomak.flashcards.web.landing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.rossomak.flashcards.web.resources.Res
import com.rossomak.flashcards.web.resources.flashcards_logo
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.vectorResource

/** Width over height of the logo artwork. */
private const val LOGO_ASPECT_RATIO = 1.8f

/** Share of the window's height at which the large logo's center sits on the hero and the closing page. */
private const val HERO_LOGO_CENTER_SHARE = 0.32f

/** Where things sit on a landing window of [width] by [height]; see [FeatureLayout.forWindow] for [sideBySideMinAspectRatio]. */
class LandingFrame(val width: Dp, val height: Dp, sideBySideMinAspectRatio: Float = FeatureLayout.SIDE_BY_SIDE_MIN_ASPECT_RATIO) {
    val layout: FeatureLayout = FeatureLayout.forWindow(width, height, sideBySideMinAspectRatio)
    val fontScale: Float = LandingScale.fontScale(width, height)

    val heroLogoWidth: Dp = LandingScale.heroLogoWidth(width, height)
    val heroLogoHeight: Dp = heroLogoWidth / LOGO_ASPECT_RATIO

    /** Where the large logo, and the text under it, start on the hero and the closing page. */
    val heroLogoTop: Dp = height * HERO_LOGO_CENTER_SHARE - heroLogoHeight / 2

    val headerLogoWidth: Dp = LandingScale.headerLogoWidth(width)
    val headerMargin: Dp = if (layout == FeatureLayout.SideBySide) 28.dp else 12.dp

    /** How far a feature page's content stays from the top, clear of the header logo. */
    val headerClearance: Dp = headerMargin * 2 + headerLogoWidth / LOGO_ASPECT_RATIO
}

/** The large logo at the top of the hero's and the closing page's text, moving and fading with it. */
@Composable
fun HeroLogo(modifier: Modifier = Modifier, frame: LandingFrame) {
    Image(
        imageVector = vectorResource(Res.drawable.flashcards_logo),
        contentDescription = "Flashcards",
        modifier = modifier.size(frame.heroLogoWidth, frame.heroLogoHeight),
    )
}

/**
 * The small logo pinned at the top start corner of every feature page, outside the pager. It slides in
 * from the window's start edge as [headerProgress] goes from 0 (the hero and the closing page, where it
 * is off screen) to 1, following the scroll.
 */
@Composable
fun HeaderLogo(modifier: Modifier = Modifier, frame: LandingFrame, headerProgress: () -> Float, alpha: () -> Float) {
    Image(
        imageVector = vectorResource(Res.drawable.flashcards_logo),
        contentDescription = "Flashcards",
        modifier = modifier
            .size(frame.headerLogoWidth, frame.headerLogoWidth / LOGO_ASPECT_RATIO)
            .offset {
                IntOffset(
                    x = lerp(-frame.headerLogoWidth.toPx(), frame.headerMargin.toPx(), headerProgress()).roundToInt(),
                    y = frame.headerMargin.roundToPx(),
                )
            }
            .graphicsLayer { this.alpha = alpha() },
    )
}
