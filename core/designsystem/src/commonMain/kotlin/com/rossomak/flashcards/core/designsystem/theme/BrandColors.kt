package com.rossomak.flashcards.core.designsystem.theme

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
    /**
     * The opaque pill of a filled button on a gradient: white in light; in dark an ink pill a few tones
     * above the cards, so it stays the strongest action without glaring white among the dark glass.
     */
    val onGradientFilledContainer: Color,
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
    val codeBlockBackground: Color,
    val onCodeBlock: Color,
    val streakFlame: Color,
    /**
     * Behind a grouped list's rows, showing as the hairline between them, and the fill of a selected
     * multi-select row. Only subtly off the row color, so a selection reads as a tint, not a block.
     */
    val listGroupContainer: Color,
    /**
     * A dialog's fill. White in light, the brightest surface on screen. In dark, a raised tone above
     * the cards and the sheet, so it stands off the dimmed, blurred screen behind it.
     */
    val dialogContainer: Color,
    /** A bottom sheet's fill. White in light; in dark a tone above the cards. */
    val sheetContainer: Color,
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
 * Container and content colours for a [com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton]
 * rendered on top of [brandTopBarGradient]: a white pill with the brand violet as text/icon colour.
 * These are the light theme's; dark uses an ink pill, [onGradientFilledContainerDark], with
 * [onSurfaceDark] content.
 */
private val onGradientFilledContainerLight = Color.White
private val onGradientFilledLight = primaryLight

/**
 * Light fill colour for every `Flashcards*Progress*` composable's
 * [com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnSurface] style.
 * Reuses [ctaGradientPurple], the brand accent. Dark uses [primaryDark] instead, the light accent
 * the progress rings and selected options use there, so a bar and a ring read as one indicator.
 */
private val progressBarFillOnSurfaceLight = ctaGradientPurple
private val progressBarFillOnSurfaceDark = primaryDark

/** Opacity of a progress track: its fill color, faint. The rings, the Daily Goal arc and the dark bar use it. */
const val PROGRESS_TRACK_ALPHA = 0.16f

/**
 * Track colour for the `OnSurface` style. Light is a recessed neutral surface; dark is the dark fill
 * at [PROGRESS_TRACK_ALPHA], matching the progress rings' track.
 */
private val progressBarTrackOnSurfaceLight = surfaceContainerHighLight
private val progressBarTrackOnSurfaceDark = progressBarFillOnSurfaceDark.copy(alpha = PROGRESS_TRACK_ALPHA)

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
 * Fixed palette for [com.rossomak.flashcards.core.ui.composables.SyntaxCodeBlock]. The code
 * block always renders `SyntaxTheme.DefaultDark` regardless of the app's light/dark theme,
 * so both themes pin the same theme-independent values.
 */
private val codeBlockBackgroundColor = Color(0xFF1E1E1E)
private val onCodeBlockColor = Color(0xFFFFFFFF)

/** The Streak card's flame; flips with the theme because it sits on a themed card, not a gradient. */
private val streakFlameLight = Color(0xFFE8590C)
private val streakFlameDark = Color(0xFFFF9F43)

val lightBrandColors = BrandColors(
    screenGradient = brandScreenGradient,
    screenGradientBase = brandScreenGradientBase,
    topBarGradient = brandTopBarGradient,
    onTopBarGradient = onBrandTopBarGradient,
    ctaButtonGradient = brandCtaButtonGradient,
    onGradientFilledContainer = onGradientFilledContainerLight,
    onGradientFilled = onGradientFilledLight,
    tonalButtonContainer = secondaryContainerLight,
    onTonalButtonContainer = onSecondaryContainerLight,
    progressBarFillOnSurface = progressBarFillOnSurfaceLight,
    progressBarTrackOnSurface = progressBarTrackOnSurfaceLight,
    progressBarFillOnGradient = progressBarFillOnGradientColor,
    progressBarTrackOnGradient = progressBarTrackOnGradientLight,
    onGradientContainer = onGradientContainerLight,
    onGradientBorder = onGradientBorderLight,
    onGradientContent = onBrandTopBarGradient,
    onGradientLoss = onGradientLossColor,
    codeBlockBackground = codeBlockBackgroundColor,
    onCodeBlock = onCodeBlockColor,
    streakFlame = streakFlameLight,
    listGroupContainer = secondaryContainerLight,
    dialogContainer = surfaceContainerLowestLight,
    sheetContainer = surfaceContainerLowestLight,
)

// The dark Tonal button is a neutral container with primaryDark content, the same content color
// FlashcardsOutlinedButton and FlashcardsTextButton use in dark, so the three read as one family.
val darkBrandColors = BrandColors(
    screenGradient = brandScreenGradient,
    screenGradientBase = brandScreenGradientBase,
    topBarGradient = brandTopBarGradient,
    onTopBarGradient = onBrandTopBarGradient,
    ctaButtonGradient = brandCtaButtonGradient,
    onGradientFilledContainer = onGradientFilledContainerDark,
    onGradientFilled = onSurfaceDark,
    tonalButtonContainer = tonalButtonContainerDark,
    onTonalButtonContainer = primaryDark,
    progressBarFillOnSurface = progressBarFillOnSurfaceDark,
    progressBarTrackOnSurface = progressBarTrackOnSurfaceDark,
    progressBarFillOnGradient = progressBarFillOnGradientColor,
    progressBarTrackOnGradient = onGradientContainerDark,
    onGradientContainer = onGradientContainerDark,
    onGradientBorder = onGradientBorderDark,
    onGradientContent = onBrandTopBarGradient,
    onGradientLoss = onGradientLossColor,
    codeBlockBackground = codeBlockBackgroundColor,
    onCodeBlock = onCodeBlockColor,
    streakFlame = streakFlameDark,
    listGroupContainer = listGroupContainerDark,
    dialogContainer = dialogContainerDark,
    sheetContainer = sheetContainerDark,
)

val LocalBrandColors = staticCompositionLocalOf { lightBrandColors }

val MaterialTheme.brandColors: BrandColors
    @Composable
    @ReadOnlyComposable
    get() = LocalBrandColors.current
