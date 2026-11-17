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

private const val LOGO_ASPECT_RATIO = 1.8f

/** The large logo's center, as a share of the window's height. */
private const val HERO_LOGO_CENTER_SHARE = 0.32f

/** Layout values for a landing window of [width] by [height]. */
class LandingFrame(val width: Dp, val height: Dp, sideBySideMinAspectRatio: Float = FeatureLayout.SIDE_BY_SIDE_MIN_ASPECT_RATIO) {
    val layout: FeatureLayout = FeatureLayout.forWindow(width, height, sideBySideMinAspectRatio)
    val fontScale: Float = LandingScale.fontScale(width, height)

    val heroLogoWidth: Dp = LandingScale.heroLogoWidth(width, height)
    val heroLogoHeight: Dp = heroLogoWidth / LOGO_ASPECT_RATIO

    val heroLogoTop: Dp = height * HERO_LOGO_CENTER_SHARE - heroLogoHeight / 2

    val headerLogoWidth: Dp = LandingScale.headerLogoWidth(width)
    val headerMargin: Dp = if (layout == FeatureLayout.SideBySide) 28.dp else 12.dp

    /** A feature page's top inset, clear of the header logo. */
    val headerClearance: Dp = headerMargin * 2 + headerLogoWidth / LOGO_ASPECT_RATIO
}

/** The hero's and closing page's large logo. */
@Composable
fun HeroLogo(modifier: Modifier = Modifier, frame: LandingFrame) {
    Image(
        imageVector = vectorResource(Res.drawable.flashcards_logo),
        contentDescription = "Flashcards",
        modifier = modifier.size(frame.heroLogoWidth, frame.heroLogoHeight),
    )
}

/** The small logo pinned at the top start, outside the pager, sliding in as [headerProgress] goes 0 to 1. */
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
