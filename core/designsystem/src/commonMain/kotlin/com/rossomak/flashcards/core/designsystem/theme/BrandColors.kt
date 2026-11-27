package com.rossomak.flashcards.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The brand gradients and the colors that exist only because a gradient is behind them. A themed
 * neutral surface belongs in the Material `ColorScheme`, not here.
 */
data class BrandColors(
    val screenGradient: Brush,
    val screenGradientBase: Color,
    val topBarGradient: Brush,
    val ctaButtonGradient: Brush,
    /** The opaque pill of a filled button on a gradient: white in light, an ink pill in dark. */
    val onGradientFilledContainer: Color,
    val onGradientFilled: Color,
    val progressBarFillOnGradient: Color,
    val progressBarTrackOnGradient: Color,
    val onGradientContainer: Color,
    val onGradientBorder: Color,
    /** Text and icons on any brand gradient or on the glass over it, including the CTA button's. */
    val onGradientContent: Color,
    val onGradientLoss: Color,
    val onGradientLossContainer: Color,
    val onGradientLossBorder: Color,
    val codeBlockBackground: Color,
    val onCodeBlock: Color,
    val streakFlame: Color,
)

/**
 * The brand gradient behind top bars and hero headers. Deliberately identical in both themes —
 * it is a brand asset, not a surface, so it does not follow light/dark.
 */
private val brandTopBarGradient = Brush.horizontalGradient(
    colors = listOf(primaryContainerLight, secondaryLight),
)

/**
 * Content colour for every brand gradient (screen, top bar, CTA button) and for the glass on it;
 * fixed, because no gradient flips with the theme.
 */
private val onBrandGradient = Color.White

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
 * Container and content colours for a [com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton]
 * rendered on top of [brandTopBarGradient]: a white pill with the brand violet as text/icon colour.
 * These are the light theme's; dark uses an ink pill, [onGradientFilledContainerDark], with
 * [onSurfaceDark] content.
 */
private val onGradientFilledContainerLight = Color.White
private val onGradientFilledLight = primaryLight

/**
 * Fill/track colours for the
 * [com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnGradient] style.
 * The fill is white in both themes. The track is the light theme's white glass; dark uses the same
 * dark glass as [BrandColors.onGradientContainer], [onGradientContainerDark].
 */
private val progressBarFillOnGradientColor = Color.White

/** Alpha of the recessed track against an on-gradient background. */
private const val PROGRESS_BAR_TRACK_ON_GRADIENT_ALPHA = 0.20f
private val progressBarTrackOnGradientLight = Color.White.copy(alpha = PROGRESS_BAR_TRACK_ON_GRADIENT_ALPHA)

/**
 * The translucent glass every filled component uses on a brand gradient — the pill behind a
 * [com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTonalButton], a
 * [com.rossomak.flashcards.core.ui.composables.FlashcardsMetadataBadge], a
 * [com.rossomak.flashcards.core.ui.composables.banners.FlashcardsInfoBanner]. One fill and one
 * border for all of them, buttons included, so the same glass reads the same on every component.
 *
 * These are the light theme's white glass. Dark uses dark glass instead, [onGradientContainerDark]
 * with the faint white edge [onGradientBorderDark], so on-gradient components sit with the dark
 * surfaces around them instead of glowing white against them.
 */
private const val ON_GRADIENT_CONTAINER_ALPHA = 0.24f
private const val ON_GRADIENT_BORDER_ALPHA = 0.35f

private val onGradientContainerLight = Color.White.copy(alpha = ON_GRADIENT_CONTAINER_ALPHA)
private val onGradientBorderLight = Color.White.copy(alpha = ON_GRADIENT_BORDER_ALPHA)

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

/**
 * The fill and border of a `Loss` surface on the brand gradient, [errorContainerDark] at an alpha and
 * pinned in both themes like [onGradientLossColor]. Deliberately not `SemanticColors.negativeContainer`:
 * that pair is tuned for a themed surface and reads too washed-out on the gradient.
 */
private const val ON_GRADIENT_LOSS_CONTAINER_ALPHA = 0.33f
private const val ON_GRADIENT_LOSS_BORDER_ALPHA = 0.5f

private val onGradientLossContainerColor = errorContainerDark.copy(alpha = ON_GRADIENT_LOSS_CONTAINER_ALPHA)
private val onGradientLossBorderColor = errorContainerDark.copy(alpha = ON_GRADIENT_LOSS_BORDER_ALPHA)

/**
 * Fixed palette for [com.rossomak.flashcards.core.ui.composables.SyntaxCodeBlock]. The code
 * block always renders `SyntaxTheme.DefaultDark` regardless of the app's light/dark theme,
 * so both themes pin the same theme-independent values.
 */
private val codeBlockBackgroundColor = Color(0xFF1E1E1E)
private val onCodeBlockColor = Color(0xFFFFFFFF)

/** The Streak card's flame; flips with the theme because it sits on a themed card, not a gradient. */
private val streakFlameLight = Color(0xFFE8590C)
private val streakFlameDark = Color(0xFFFF9F43)

internal val lightBrandColors = BrandColors(
    screenGradient = brandScreenGradient,
    screenGradientBase = brandScreenGradientBase,
    topBarGradient = brandTopBarGradient,
    ctaButtonGradient = brandCtaButtonGradient,
    onGradientFilledContainer = onGradientFilledContainerLight,
    onGradientFilled = onGradientFilledLight,
    progressBarFillOnGradient = progressBarFillOnGradientColor,
    progressBarTrackOnGradient = progressBarTrackOnGradientLight,
    onGradientContainer = onGradientContainerLight,
    onGradientBorder = onGradientBorderLight,
    onGradientContent = onBrandGradient,
    onGradientLoss = onGradientLossColor,
    onGradientLossContainer = onGradientLossContainerColor,
    onGradientLossBorder = onGradientLossBorderColor,
    codeBlockBackground = codeBlockBackgroundColor,
    onCodeBlock = onCodeBlockColor,
    streakFlame = streakFlameLight,
)

internal val darkBrandColors = BrandColors(
    screenGradient = brandScreenGradient,
    screenGradientBase = brandScreenGradientBase,
    topBarGradient = brandTopBarGradient,
    ctaButtonGradient = brandCtaButtonGradient,
    onGradientFilledContainer = onGradientFilledContainerDark,
    onGradientFilled = onSurfaceDark,
    progressBarFillOnGradient = progressBarFillOnGradientColor,
    progressBarTrackOnGradient = onGradientContainerDark,
    onGradientContainer = onGradientContainerDark,
    onGradientBorder = onGradientBorderDark,
    onGradientContent = onBrandGradient,
    onGradientLoss = onGradientLossColor,
    onGradientLossContainer = onGradientLossContainerColor,
    onGradientLossBorder = onGradientLossBorderColor,
    codeBlockBackground = codeBlockBackgroundColor,
    onCodeBlock = onCodeBlockColor,
    streakFlame = streakFlameDark,
)

val LocalBrandColors = staticCompositionLocalOf { lightBrandColors }

val MaterialTheme.brandColors: BrandColors
    @Composable
    @ReadOnlyComposable
    get() = LocalBrandColors.current
