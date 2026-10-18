package com.rossomak.flashcards.core.designsystem.theme

import androidx.compose.ui.graphics.Color

internal val primaryLight = Color(0xFF7D3AC8)
internal val onPrimaryLight = Color(0xFFFFFFFF)
internal val primaryContainerLight = Color(0xFF2A2E8F)
internal val onPrimaryContainerLight = Color(0xFF989DFF)
internal val secondaryLight = Color(0xFF520F87)
internal val onSecondaryLight = Color(0xFFFFFFFF)
internal val secondaryContainerLight = Color(0xFFF0E8FA)
internal val onSecondaryContainerLight = Color(0xFF7D3AC8)
internal val tertiaryLight = Color(0xFF854B72)
internal val onTertiaryLight = Color(0xFFFFFFFF)
internal val tertiaryContainerLight = Color(0xFFFFB6E3)
internal val onTertiaryContainerLight = Color(0xFF7C4369)
internal val errorLight = Color(0xFF890023)
internal val onErrorLight = Color(0xFFFFFFFF)
internal val errorContainerLight = Color(0xFFB50031)
internal val onErrorContainerLight = Color(0xFFFFC1C3)
internal val backgroundLight = Color(0xFFF5F2FB)
internal val onBackgroundLight = Color(0xFF1B1B21)
internal val surfaceLight = Color(0xFFFBF8FF)
internal val onSurfaceLight = Color(0xFF1B1B21)
internal val surfaceVariantLight = Color(0xFFE3E1F0)
internal val onSurfaceVariantLight = Color(0xFF62627D)
internal val outlineLight = Color(0xFF767683)
internal val outlineVariantLight = Color(0xFFC7C5D4)
internal val scrimLight = Color(0xFF000000)
internal val inverseSurfaceLight = Color(0xFF303036)
internal val inverseOnSurfaceLight = Color(0xFFF2EFF8)
internal val inversePrimaryLight = Color(0xFFBFC1FF)
internal val surfaceDimLight = Color(0xFFDBD9E1)
internal val surfaceBrightLight = Color(0xFFFBF8FF)
internal val surfaceContainerLowestLight = Color(0xFFFFFFFF)
internal val surfaceContainerLowLight = Color(0xFFF5F2FB)
internal val surfaceContainerLight = Color(0xFFF0ECF5)
internal val surfaceContainerHighLight = Color(0xFFEAE7F0)
internal val surfaceContainerHighestLight = Color(0xFFE4E1EA)

// Hue-corrected: the previous values (0xFFBFC1FF / 0xFF1D2184) sat at ~238° hue — matching
// primaryContainerDark's indigo, not primaryLight's ~268° violet — so colorScheme.primary read
// blue in dark mode (visible in Checkbox/RadioButton's checked fill) despite reading purple in
// light mode. Retuned to primaryLight's violet hue at the same saturation/lightness.
internal val primaryDark = Color(0xFFDDBFFF)
internal val onPrimaryDark = Color(0xFF4E1D84)
internal val primaryContainerDark = Color(0xFF2A2E8F)
internal val onPrimaryContainerDark = Color(0xFF989DFF)
internal val secondaryDark = Color(0xFFDEB7FF)
internal val onSecondaryDark = Color(0xFF4A007F)

// Dark-tuned like SemanticColors' dark pairs (see that section below): a muted, near-surface
// purple rather than a punchy mid-tone accent, so full-opacity consumers (FlashcardsTagChip's
// selected fill, FlashcardsIconTile, FlashcardsEmptyState's Info tone, …) read as a subtle tint
// on dark surfaces the same way secondaryContainerLight reads as a subtle tint on light ones,
// instead of a solid saturated color-block. onSecondaryContainerDark is unchanged — already
// bright/contrasty enough against this darker container.
internal val secondaryContainerDark = Color(0xFF33204A)
internal val onSecondaryContainerDark = Color(0xFFDAAFFF)
internal val tertiaryDark = Color(0xFFFFE0F0)
internal val onTertiaryDark = Color(0xFF501D42)
internal val tertiaryContainerDark = Color(0xFFFFB6E3)
internal val onTertiaryContainerDark = Color(0xFF7C4369)
internal val errorDark = Color(0xFFFFB3B5)
internal val onErrorDark = Color(0xFF680018)
internal val errorContainerDark = Color(0xFFB50031)
internal val onErrorContainerDark = Color(0xFFFFC1C3)
internal val backgroundDark = Color(0xFF131319)
internal val onBackgroundDark = Color(0xFFE4E1EA)
internal val surfaceDark = Color(0xFF131319)
internal val onSurfaceDark = Color(0xFFE4E1EA)
internal val surfaceVariantDark = Color(0xFF464652)
internal val onSurfaceVariantDark = Color(0xFFC7C5D4)
internal val outlineDark = Color(0xFF908F9D)
internal val outlineVariantDark = Color(0xFF464652)
internal val scrimDark = Color(0xFF000000)
internal val inverseSurfaceDark = Color(0xFFE4E1EA)
internal val inverseOnSurfaceDark = Color(0xFF303036)
internal val inversePrimaryDark = Color(0xFF4F54B4)
internal val surfaceDimDark = Color(0xFF131319)
internal val surfaceBrightDark = Color(0xFF39383F)
internal val surfaceContainerLowestDark = Color(0xFF0E0E14)
internal val surfaceContainerLowDark = Color(0xFF1B1B21)
internal val surfaceContainerDark = Color(0xFF1F1F25)
internal val surfaceContainerHighDark = Color(0xFF2A2930)
internal val surfaceContainerHighestDark = Color(0xFF34343B)

// Valence palette backing [SemanticColors] — see that file for why these are hand-authored
// container/content pairs rather than one accent tinted at an alpha. The light values are the
// study screen's shipped rating-button palette, promoted to tokens; the dark values are new.
//
// onXContainerLight are darkened from the original rating-button palette (3E9556/C98F2B/C94F4F):
// those only hit 2.3:1-3.4:1 against their containers, well under the 4.5:1 WCAG AA text minimum
// FlashcardsInfoBanner's body text needs. Same hue/saturation, lower lightness — now 4.8:1+.
internal val positiveContainerLight = Color(0xFFD3EBD6)
internal val onPositiveContainerLight = Color(0xFF2E6E40)
internal val neutralContainerLight = Color(0xFFF6E8C8)
internal val onNeutralContainerLight = Color(0xFF845E1C)
internal val negativeContainerLight = Color(0xFFF6D9DA)
internal val onNegativeContainerLight = Color(0xFFAB3434)

internal val positiveContainerDark = Color(0xFF1E3524)
internal val onPositiveContainerDark = Color(0xFF7BD98A)
internal val neutralContainerDark = Color(0xFF3A2F16)
internal val onNeutralContainerDark = Color(0xFFE8B84B)
internal val negativeContainerDark = Color(0xFF3A1F20)
internal val onNegativeContainerDark = Color(0xFFE88C8C)
