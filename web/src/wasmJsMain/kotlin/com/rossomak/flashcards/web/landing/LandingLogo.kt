package com.rossomak.flashcards.web.landing

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
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

/** Where things sit on a landing window of [width] by [height]. */
class LandingFrame(val width: Dp, val height: Dp) {
    val layout: FeatureLayout = FeatureLayout.forWindowWidth(width)
    val fontScale: Float = LandingScale.fontScale(width)

    val heroLogoWidth: Dp = LandingScale.heroLogoWidth(width, height)
    private val heroLogoHeight: Dp = heroLogoWidth / LOGO_ASPECT_RATIO
    val heroLogoLeft: Dp = (width - heroLogoWidth) / 2
    val heroLogoTop: Dp = height * HERO_LOGO_CENTER_SHARE - heroLogoHeight / 2

    /** Where the hero's and the closing page's text starts, just under the large logo. */
    val heroTextTop: Dp = heroLogoTop + heroLogoHeight

    val headerLogoWidth: Dp = LandingScale.headerLogoWidth(width)
    val headerMargin: Dp = if (layout == FeatureLayout.SideBySide) 28.dp else 12.dp

    /** How far a feature page's content stays from the top, clear of the header logo. */
    val headerClearance: Dp = headerMargin * 2 + headerLogoWidth / LOGO_ASPECT_RATIO
}

/**
 * The one Flashcards logo of the landing page, outside the pager: large above the hero's text and
 * the closing page's, small at the top of the window on every feature page. It moves and scales
 * between the two continuously as [headerProgress] goes from 0 to 1, following the scroll. A click
 * opens the hero.
 */
@Composable
fun LandingLogo(modifier: Modifier = Modifier, frame: LandingFrame, headerProgress: () -> Float, alpha: () -> Float, onClick: () -> Unit) {
    Image(
        imageVector = vectorResource(Res.drawable.flashcards_logo),
        contentDescription = "Flashcards",
        modifier = modifier
            .size(frame.heroLogoWidth, frame.heroLogoWidth / LOGO_ASPECT_RATIO)
            .offset {
                val progress = headerProgress()
                IntOffset(
                    x = lerp(frame.heroLogoLeft.toPx(), frame.headerMargin.toPx(), progress).roundToInt(),
                    y = lerp(frame.heroLogoTop.toPx(), frame.headerMargin.toPx(), progress).roundToInt(),
                )
            }
            .graphicsLayer {
                // Drawn at the large size and only ever scaled down, so the vector stays sharp.
                val scale = lerp(1f, frame.headerLogoWidth / frame.heroLogoWidth, headerProgress())
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
                this.alpha = alpha()
            }
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
    )
}
