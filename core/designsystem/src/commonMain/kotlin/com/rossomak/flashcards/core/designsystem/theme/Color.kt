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

/** The bottom sheet's fill, flat white. */
internal val surfaceContainerLowLight = Color(0xFFFFFFFF)
internal val surfaceContainerLight = Color(0xFFF0ECF5)

/** The dialog's fill, flat white. */
internal val surfaceContainerHighLight = Color(0xFFFFFFFF)

/** The tonal button's fill, the faint lavender of secondaryContainerLight. */
internal val surfaceContainerHighestLight = Color(0xFFF0E8FA)

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

/**
 * A faint lavender tint off the card, as secondaryContainerLight is off white: secondaryDark at 10%
 * over the card. A grouped list's backdrop and a selected row fill with it, and so does every other
 * secondaryContainer consumer.
 */
internal val secondaryContainerDark = Color(0xFF2F2A3B)
internal val onSecondaryContainerDark = Color(0xFFDAAFFF)
internal val tertiaryDark = Color(0xFFFFE0F0)
internal val onTertiaryDark = Color(0xFF501D42)
internal val tertiaryContainerDark = Color(0xFFFFB6E3)
internal val onTertiaryContainerDark = Color(0xFF7C4369)
internal val errorDark = Color(0xFFFFB3B5)
internal val onErrorDark = Color(0xFF680018)
internal val errorContainerDark = Color(0xFFB50031)
internal val onErrorContainerDark = Color(0xFFFFC1C3)
internal val onBackgroundDark = Color(0xFFE4E1EA)
internal val onSurfaceDark = Color(0xFFE4E1EA)
internal val onSurfaceVariantDark = Color(0xFFC7C5D4)
internal val outlineDark = Color(0xFF908F9D)
internal val scrimDark = Color(0xFF000000)
internal val inverseSurfaceDark = Color(0xFFE4E1EA)
internal val inverseOnSurfaceDark = Color(0xFF303036)
internal val inversePrimaryDark = Color(0xFF4F54B4)

// The dark surfaces: HCT tones of one neutral palette, hue 290 and chroma 10, so only the tone moves
// and the steps look evenly spaced. Unlike stock Material dark, the card (the surfaceContainerLowest
// slot, the app's card color in both themes) sits above the background, so cards lift off the screen
// as white cards do in light. The containers above the card keep Material's dark spacing from it:
// Low +1, Container +2, High +3, Highest +12 and Bright +14 tones. The debug dark theme tuner derives
// the same colors from the same tones, and a test keeps the two equal.

/** The screen: background, surface and surfaceDim. Tone 6. */
internal val backgroundDark = Color(0xFF13121C)

/** Cards and list rows: the surfaceContainerLowest slot. Tone 10. */
internal val surfaceContainerLowestDark = Color(0xFF1B1A25)

/** A bottom sheet's fill. Tone 11. */
internal val surfaceContainerLowDark = Color(0xFF1E1C27)

/** Tone 12. */
internal val surfaceContainerDark = Color(0xFF201E29)

/** A dialog's fill. Tone 13, above the sheet so a dialog stands off the dimmed screen behind it. */
internal val surfaceContainerHighDark = Color(0xFF22202B)

/** A tonal button's fill. Tone 22, above the dialog so a tonal Cancel still reads as a button. */
internal val surfaceContainerHighestDark = Color(0xFF35333F)

/** Tone 24. */
internal val surfaceBrightDark = Color(0xFF393843)

/** surfaceVariant and outlineVariant: the option card ring and dividers. Tone 30. */
internal val outlineVariantDark = Color(0xFF474551)

/** The opaque ink pill of a filled button on a gradient. Tone 17. */
internal val onGradientFilledContainerDark = Color(0xFF2A2933)

/** The on-gradient glass and its progress track: tone 12 at 30%, the one translucent dark surface. */
internal val onGradientContainerDark = Color(0x4D201E29)

/** The glass's faint white edge: white at 12%. */
internal val onGradientBorderDark = Color(0x1FFFFFFF)

// Valence palette backing [SemanticColors] — see that file for why these are hand-authored
// container/content pairs rather than one accent tinted at an alpha. The light values are the
// study screen's shipped rating-button palette, promoted to tokens.
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

// The dark status containers mix each content hue at 14% over the card tone (surfaceContainerLowestDark)
// into one opaque color, so a banner reads the same on a card, a sheet or a dialog.

/** onPositiveContainerDark at 14% over the card. */
internal val positiveContainerDark = Color(0xFF293533)
internal val onPositiveContainerDark = Color(0xFF7BD98A)

/** onNeutralContainerDark at 14% over the card. */
internal val neutralContainerDark = Color(0xFF38302A)
internal val onNeutralContainerDark = Color(0xFFE8B84B)

/** onNegativeContainerDark at 14% over the card. */
internal val negativeContainerDark = Color(0xFF382A34)
internal val onNegativeContainerDark = Color(0xFFE88C8C)
