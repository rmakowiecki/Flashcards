package com.rossomak.flashcards.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

data class BrandColors(
    val screenGradient: Brush,
    val screenGradientBase: Color,
    val topBarGradient: Brush,
    val onTopBarGradient: Color,
    val ctaButtonGradient: Brush,
    val onGradientFilled: Color,
    val tonalButtonContainer: Color,
    val onTonalButtonContainer: Color,
    val progressBarFillOnSurface: Color,
    val progressBarTrackOnSurface: Color,
    val progressBarFillOnGradient: Color,
    val progressBarTrackOnGradient: Color,
    val onGradientContainer: Color,
    val onGradientBorder: Color,
    val onGradientContent: Color,
    val onGradientLoss: Color,
)

/**
 * The brand gradient behind top bars and hero headers. Deliberately identical in both themes —
 * it is a brand asset, not a surface, so it does not follow light/dark.
 */
private val brandTopBarGradient = Brush.horizontalGradient(
    colors = listOf(primaryContainerLight, secondaryLight),
)

/** Content colour for [brandTopBarGradient]; fixed, because the gradient itself never flips. */
private val onBrandTopBarGradient = Color.White

/** Indigo stop of [brandCtaButtonGradient] — the brand's CTA gradient, fixed across themes. */
private val ctaGradientIndigo = Color(0xFF2235A8)

/** Purple stop of [brandCtaButtonGradient] — the brand's CTA gradient, fixed across themes. */
private val ctaGradientPurple = Color(0xFF7C3FC4)

/**
 * The brand gradient behind [com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton].
 * Diagonal (top-left to bottom-right, i.e. CSS `135deg`), via [Offset.Zero]/[Offset.Infinite] so
 * it spans the button regardless of its size. Fixed across themes like [brandTopBarGradient] — a
 * brand asset, not a surface.
 */
private val brandCtaButtonGradient = Brush.linearGradient(
    colors = listOf(ctaGradientIndigo, ctaGradientPurple),
    start = Offset.Zero,
    end = Offset.Infinite,
)

/** Blue stop of [brandScreenGradient] — distinct from [ctaGradientIndigo]; ground truth is splash's original hue. */
private val screenGradientBlue = Color(0xFF2A2E8F)

/** Purple stop of [brandScreenGradient] — distinct from [ctaGradientPurple]; ground truth is splash's original hue. */
private val screenGradientPurple = Color(0xFF6B2FA0)

/**
 * The full-bleed brand gradient behind an entire screen — splash, login, and onboarding. Splash is
 * the ground truth this was unified from: rotated (diagonal, top-left to bottom-right via
 * [Offset.Zero]/[Offset.Infinite]) and phase-shifted (stops at 0.25/0.99, not 0/1, so the blue holds
 * longer before the sweep into purple starts). Deliberately its own colours, not [brandCtaButtonGradient]'s
 * — the CTA gradient stays a separate brand asset for buttons. Fixed across themes like every other
 * gradient in this file.
 */
private val brandScreenGradient = Brush.linearGradient(
    colorStops = arrayOf(
        0.25f to screenGradientBlue,
        0.99f to screenGradientPurple,
    ),
    start = Offset.Zero,
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
)

/**
 * Flat fill matching [brandScreenGradient]'s leading colour — for `t < 0.25` the gradient is a
 * constant [screenGradientBlue], so a layer sitting *beneath* it (splash paints this first, then
 * slides the gradient in over it) must start from the same colour or the transition shows a seam.
 */
private val brandScreenGradientBase = screenGradientBlue

/**
 * Content colour for a [com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton]
 * rendered on top of [brandTopBarGradient] (white container, so this is the text/icon colour).
 * Fixed like [onBrandTopBarGradient], for the same reason.
 */
private val onGradientFilledButton = primaryLight

/**
 * Fill colour for every `Flashcards*Progress*` composable's
 * [com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnSurface] style.
 * Reuses [ctaGradientPurple] rather than `colorScheme.primary` and is fixed across themes — like
 * [brandCtaButtonGradient], the fill is a brand accent, not a surface, so both the Surface and
 * Gradient treatments read as the same purple identity in dark mode instead of drifting apart.
 */
private val progressBarFillOnSurfaceColor = ctaGradientPurple

/**
 * Track colour for the `OnSurface` style — **theme-flipping**, unlike [progressBarFillOnSurfaceColor].
 * A track is a recessed surface, not a brand asset, so it follows light/dark like
 * [BrandColors.tonalButtonContainer] does. Dark reuses [surfaceContainerHighDark] (the same token
 * `tonalButtonContainer` picks in dark) rather than `surfaceVariantDark`, for the same
 * hue-consistency reason documented on [darkBrandColors] below.
 */
private val progressBarTrackOnSurfaceLight = surfaceContainerHighLight
private val progressBarTrackOnSurfaceDark = surfaceContainerHighDark

/**
 * Fill/track colours for the
 * [com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnGradient] style.
 * Fixed across themes like every other on-gradient color in this file — the gradients they sit on
 * never flip either.
 */
private val progressBarFillOnGradientColor = Color.White

/** Alpha of the recessed track against an on-gradient background. */
private const val PROGRESS_BAR_TRACK_ON_GRADIENT_ALPHA = 0.20f
private val progressBarTrackOnGradientColor = Color.White.copy(alpha = PROGRESS_BAR_TRACK_ON_GRADIENT_ALPHA)

/**
 * The translucent white treatment every filled component uses on a brand gradient — the pill
 * behind a [com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTonalButton], a
 * [com.rossomak.flashcards.core.ui.composables.FlashcardsMetadataBadge], a
 * [com.rossomak.flashcards.core.ui.composables.banners.FlashcardsInfoBanner]. One fill and one
 * border for all of them, buttons included, so the same "white on gradient" surface reads the same
 * on every component.
 *
 * Fixed across themes like every other on-gradient color here — the gradients they sit on never
 * flip either.
 */
private const val ON_GRADIENT_CONTAINER_ALPHA = 0.24f
private const val ON_GRADIENT_BORDER_ALPHA = 0.35f

private val onGradientContainerColor = Color.White.copy(alpha = ON_GRADIENT_CONTAINER_ALPHA)
private val onGradientBorderColor = Color.White.copy(alpha = ON_GRADIENT_BORDER_ALPHA)

/**
 * The red a negative row uses on a brand gradient — currently
 * [com.rossomak.flashcards.core.ui.composables.banners.FlashcardsXpBreakdownRow]'s `Loss` tone.
 * Reuses the existing [errorDark] constant and pins it in **both** themes, because the gradient
 * behind it does not flip.
 *
 * Deliberately not [SemanticColors]' negative pair: that one is tuned for a themed surface and
 * measures roughly 3.3:1 against the gradient's mid purple, below the body-text floor, whereas
 * this reaches roughly 4.8:1. Two reds is the correct outcome — one for surfaces, one for the
 * gradient.
 */
private val onGradientLossColor = errorDark

val lightBrandColors = BrandColors(
    screenGradient = brandScreenGradient,
    screenGradientBase = brandScreenGradientBase,
    topBarGradient = brandTopBarGradient,
    onTopBarGradient = onBrandTopBarGradient,
    ctaButtonGradient = brandCtaButtonGradient,
    onGradientFilled = onGradientFilledButton,
    tonalButtonContainer = secondaryContainerLight,
    onTonalButtonContainer = onSecondaryContainerLight,
    progressBarFillOnSurface = progressBarFillOnSurfaceColor,
    progressBarTrackOnSurface = progressBarTrackOnSurfaceLight,
    progressBarFillOnGradient = progressBarFillOnGradientColor,
    progressBarTrackOnGradient = progressBarTrackOnGradientColor,
    onGradientContainer = onGradientContainerColor,
    onGradientBorder = onGradientBorderColor,
    onGradientContent = onBrandTopBarGradient,
    onGradientLoss = onGradientLossColor,
)

// Dark uses a neutral elevated-surface container (surfaceContainerHighDark) with primaryDark as
// content, not secondaryContainer/onSecondaryContainer — secondaryContainerDark (#6B2FA0, opaque
// purple) reads as a different hue family than the bluish primaryDark that
// FlashcardsOutlinedButton and FlashcardsTextButton already use for their dark-theme content, so
// a Tonal button next to either looked inconsistent. Reusing primaryDark directly (not
// onPrimaryContainerDark) keeps the content color pixel-identical to those two, not just
// same-family.
val darkBrandColors = BrandColors(
    screenGradient = brandScreenGradient,
    screenGradientBase = brandScreenGradientBase,
    topBarGradient = brandTopBarGradient,
    onTopBarGradient = onBrandTopBarGradient,
    ctaButtonGradient = brandCtaButtonGradient,
    onGradientFilled = onGradientFilledButton,
    tonalButtonContainer = surfaceContainerHighDark,
    onTonalButtonContainer = primaryDark,
    progressBarFillOnSurface = progressBarFillOnSurfaceColor,
    progressBarTrackOnSurface = progressBarTrackOnSurfaceDark,
    progressBarFillOnGradient = progressBarFillOnGradientColor,
    progressBarTrackOnGradient = progressBarTrackOnGradientColor,
    onGradientContainer = onGradientContainerColor,
    onGradientBorder = onGradientBorderColor,
    onGradientContent = onBrandTopBarGradient,
    onGradientLoss = onGradientLossColor,
)

val LocalBrandColors = staticCompositionLocalOf { lightBrandColors }

val MaterialTheme.brandColors: BrandColors
    @Composable
    @ReadOnlyComposable
    get() = LocalBrandColors.current
