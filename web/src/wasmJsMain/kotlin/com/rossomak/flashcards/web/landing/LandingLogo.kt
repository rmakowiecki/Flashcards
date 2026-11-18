package com.rossomak.flashcards.web.landing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.rossomak.flashcards.web.components.SiteLinks
import com.rossomak.flashcards.web.navigation.OnNavigate
import com.rossomak.flashcards.web.resources.Res
import com.rossomak.flashcards.web.resources.flashcards_logo
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.vectorResource

private const val LOGO_ASPECT_RATIO = 1.8f

/** The large logo's center, as a share of the window's height. */
private const val HERO_LOGO_CENTER_SHARE = 0.32f

/** How much of the large logo's height the text below tucks into, to close the logo's own empty margin. */
private const val HERO_LOGO_TEXT_OVERLAP_SHARE = 0.1f

/** In a short window the large logo sits this share of the window's height from the top. */
private const val COMPACT_HEIGHT_HERO_LOGO_TOP_SHARE = 0.1f

/** Layout values for a landing window of [width] by [height]. */
class LandingFrame(val width: Dp, val height: Dp, sideBySideMinAspectRatio: Float = FeatureLayout.SIDE_BY_SIDE_MIN_ASPECT_RATIO) {
    val layout: FeatureLayout = FeatureLayout.forWindow(width, height, sideBySideMinAspectRatio)
    val fontScale: Float = LandingScale.fontScale(width, height)

    /** A phone in portrait. */
    val isCompactWidth: Boolean = LandingScale.isCompactWidth(width)

    /** A phone in landscape. */
    val isCompactHeight: Boolean = LandingScale.isCompactHeight(height)

    /** Small windows get smaller text, tighter gaps and a smaller page indicator. */
    val isCompact: Boolean = isCompactWidth || isCompactHeight

    val dots: PageDotMetrics = if (isCompact) PageDotMetrics.Compact else PageDotMetrics.Regular

    val heroLogoWidth: Dp = LandingScale.heroLogoWidth(width, height)
    val heroLogoHeight: Dp = heroLogoWidth / LOGO_ASPECT_RATIO
    val heroLogoTextOverlap: Dp = heroLogoHeight * HERO_LOGO_TEXT_OVERLAP_SHARE

    val heroLogoTop: Dp = if (isCompactHeight) height * COMPACT_HEIGHT_HERO_LOGO_TOP_SHARE else height * HERO_LOGO_CENTER_SHARE - heroLogoHeight / 2

    val headerLogoWidth: Dp = LandingScale.headerLogoWidth(width, height)
    val headerMargin: Dp = if (layout == FeatureLayout.SideBySide && !isCompactHeight) 28.dp else 12.dp

    /** A feature page's top inset, clear of the header logo. */
    val headerClearance: Dp = headerMargin * 2 + headerLogoWidth / LOGO_ASPECT_RATIO
}

/** The hero's and closing page's large logo. */
@Composable
fun HeroLogo(modifier: Modifier = Modifier, frame: LandingFrame) {
    Image(
        imageVector = vectorResource(Res.drawable.flashcards_logo),
        contentDescription = "Flashcards",
        modifier = modifier
            .size(frame.heroLogoWidth, frame.heroLogoHeight)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val overlap = frame.heroLogoTextOverlap.roundToPx()
                layout(placeable.width, placeable.height - overlap) { placeable.place(0, 0) }
            },
    )
}

/** The small logo pinned at the top start, outside the pager, sliding in and fading in as [headerProgress] goes 0 to 1. */
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
            .graphicsLayer { this.alpha = alpha() * headerProgress() },
    )
}

/** The privacy policy link pinned at the top end, outside the pager, sliding in and fading in from the end edge as [headerProgress] goes 0 to 1, level with [HeaderLogo]. */
@Composable
fun HeaderLinks(modifier: Modifier = Modifier, frame: LandingFrame, headerProgress: () -> Float, alpha: () -> Float, onNavigate: OnNavigate) {
    // Out of sight the link is left out, so a keyboard cannot reach it.
    val shown by remember { derivedStateOf { alpha() * headerProgress() > 0f } }
    if (!shown) return
    val logoHeight = frame.headerLogoWidth / LOGO_ASPECT_RATIO
    val marginPx = with(LocalDensity.current) { frame.headerMargin.toPx() }
    Box(
        modifier = modifier
            .padding(top = frame.headerMargin, end = frame.headerMargin)
            .height(logoHeight)
            .widthIn(max = (frame.width - frame.headerLogoWidth - frame.headerMargin * 3).coerceAtLeast(0.dp))
            .graphicsLayer {
                translationX = (1f - headerProgress()) * (size.width + marginPx)
                this.alpha = alpha() * headerProgress()
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        // The header sits outside the pager's text scaling, so it takes the same scale to match the other links' size.
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * frame.fontScale)) {
            SiteLinks(
                modifier = Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    // Inset from the end edge by the gap above the text, so both gaps to the window edges match.
                    val endInset = ((logoHeight.roundToPx() - placeable.height) / 2).coerceAtLeast(0)
                    layout(constraints.constrainWidth(placeable.width + endInset), placeable.height) { placeable.place(0, 0) }
                },
                showContact = false,
                showDeleteAccount = false,
                onNavigate = onNavigate,
            )
        }
    }
}
