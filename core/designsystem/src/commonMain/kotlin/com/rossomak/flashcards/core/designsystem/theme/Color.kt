package com.rossomak.flashcards.core.designsystem.theme

import androidx.compose.ui.graphics.Color

val primaryLight = Color(0xFF7D3AC8)
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFF2A2E8F)
val onPrimaryContainerLight = Color(0xFF989DFF)
val secondaryLight = Color(0xFF520F87)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFF0E8FA)
val onSecondaryContainerLight = Color(0xFF7D3AC8)
val tertiaryLight = Color(0xFF854B72)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFFFB6E3)
val onTertiaryContainerLight = Color(0xFF7C4369)
val errorLight = Color(0xFF890023)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFB50031)
val onErrorContainerLight = Color(0xFFFFC1C3)
val backgroundLight = Color(0xFFF5F2FB)
val onBackgroundLight = Color(0xFF1B1B21)
val surfaceLight = Color(0xFFFBF8FF)
val onSurfaceLight = Color(0xFF1B1B21)
val surfaceVariantLight = Color(0xFFE3E1F0)
val onSurfaceVariantLight = Color(0xFF62627D)
val outlineLight = Color(0xFF767683)
val outlineVariantLight = Color(0xFFC7C5D4)
val scrimLight = Color(0xFF000000)
val inverseSurfaceLight = Color(0xFF303036)
val inverseOnSurfaceLight = Color(0xFFF2EFF8)
val inversePrimaryLight = Color(0xFFBFC1FF)
val surfaceDimLight = Color(0xFFDBD9E1)
val surfaceBrightLight = Color(0xFFFBF8FF)
val surfaceContainerLowestLight = Color(0xFFFFFFFF)
val surfaceContainerLowLight = Color(0xFFF5F2FB)
val surfaceContainerLight = Color(0xFFF0ECF5)
val surfaceContainerHighLight = Color(0xFFEAE7F0)
val surfaceContainerHighestLight = Color(0xFFE4E1EA)

// Hue-corrected: the previous values (0xFFBFC1FF / 0xFF1D2184) sat at ~238° hue — matching
// primaryContainerDark's indigo, not primaryLight's ~268° violet — so colorScheme.primary read
// blue in dark mode (visible in Checkbox/RadioButton's checked fill) despite reading purple in
// light mode. Retuned to primaryLight's violet hue at the same saturation/lightness.
val primaryDark = Color(0xFFDDBFFF)
val onPrimaryDark = Color(0xFF4E1D84)
val primaryContainerDark = Color(0xFF2A2E8F)
val onPrimaryContainerDark = Color(0xFF989DFF)
val secondaryDark = Color(0xFFDEB7FF)
val onSecondaryDark = Color(0xFF4A007F)

// Dark-tuned like SemanticColors' dark pairs (see that section below): a muted, near-surface
// purple rather than a punchy mid-tone accent, so full-opacity consumers (FlashcardsTagChip's
// selected fill, FlashcardsIconTile, FlashcardsEmptyState's Info tone, …) read as a subtle tint
// on dark surfaces the same way secondaryContainerLight reads as a subtle tint on light ones,
// instead of a solid saturated color-block. onSecondaryContainerDark is unchanged — already
// bright/contrasty enough against this darker container.
val secondaryContainerDark = Color(0xFF33204A)
val onSecondaryContainerDark = Color(0xFFDAAFFF)
val tertiaryDark = Color(0xFFFFE0F0)
val onTertiaryDark = Color(0xFF501D42)
val tertiaryContainerDark = Color(0xFFFFB6E3)
val onTertiaryContainerDark = Color(0xFF7C4369)
val errorDark = Color(0xFFFFB3B5)
val onErrorDark = Color(0xFF680018)
val errorContainerDark = Color(0xFFB50031)
val onErrorContainerDark = Color(0xFFFFC1C3)
val backgroundDark = Color(0xFF131319)
val onBackgroundDark = Color(0xFFE4E1EA)
val surfaceDark = Color(0xFF131319)
val onSurfaceDark = Color(0xFFE4E1EA)
val surfaceVariantDark = Color(0xFF464652)
val onSurfaceVariantDark = Color(0xFFC7C5D4)
val outlineDark = Color(0xFF908F9D)
val outlineVariantDark = Color(0xFF464652)
val scrimDark = Color(0xFF000000)
val inverseSurfaceDark = Color(0xFFE4E1EA)
val inverseOnSurfaceDark = Color(0xFF303036)
val inversePrimaryDark = Color(0xFF4F54B4)
val surfaceDimDark = Color(0xFF131319)
val surfaceBrightDark = Color(0xFF39383F)
val surfaceContainerLowestDark = Color(0xFF0E0E14)
val surfaceContainerLowDark = Color(0xFF1B1B21)
val surfaceContainerDark = Color(0xFF1F1F25)
val surfaceContainerHighDark = Color(0xFF2A2930)
val surfaceContainerHighestDark = Color(0xFF34343B)

/**
 * Fixed palette for [com.rossomak.flashcards.core.ui.composables.SyntaxCodeBlock]. The code
 * block always renders `SyntaxTheme.DefaultDark` regardless of the app's light/dark theme,
 * so these are intentionally theme-independent constants rather than `colorScheme` tokens.
 */
object CodeBlockColors {
    val background = Color(0xFF1E1E1E)
    val foreground = Color(0xFFFFFFFF)
}

// Valence palette backing [SemanticColors] — see that file for why these are hand-authored
// container/content pairs rather than one accent tinted at an alpha. The light values are the
// study screen's shipped rating-button palette, promoted to tokens; the dark values are new.
//
// onXContainerLight are darkened from the original rating-button palette (3E9556/C98F2B/C94F4F):
// those only hit 2.3:1-3.4:1 against their containers, well under the 4.5:1 WCAG AA text minimum
// FlashcardsInfoBanner's body text needs. Same hue/saturation, lower lightness — now 4.8:1+.
val positiveContainerLight = Color(0xFFD3EBD6)
val onPositiveContainerLight = Color(0xFF2E6E40)
val neutralContainerLight = Color(0xFFF6E8C8)
val onNeutralContainerLight = Color(0xFF845E1C)
val negativeContainerLight = Color(0xFFF6D9DA)
val onNegativeContainerLight = Color(0xFFAB3434)

val positiveContainerDark = Color(0xFF1E3524)
val onPositiveContainerDark = Color(0xFF7BD98A)
val neutralContainerDark = Color(0xFF3A2F16)
val onNeutralContainerDark = Color(0xFFE8B84B)
val negativeContainerDark = Color(0xFF3A1F20)
val onNegativeContainerDark = Color(0xFFE88C8C)
