package com.rossomak.flashcards.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Status colors — the green / amber / red container+content pairs of every component that encodes
 * a status on a themed surface: [com.rossomak.flashcards.core.ui.composables.banners.FlashcardsInfoBanner],
 * the error tone of [com.rossomak.flashcards.core.ui.composables.FlashcardsEmptyState] and the met
 * Daily Goal. A Rating has its own palette, [RatingColors], so the two can differ.
 *
 * Unlike [BrandColors]' on-gradient colors these **do** flip light/dark: they sit on a themed
 * surface, not on the fixed brand gradient.
 *
 * Every container is opaque, never a translucent tint, so the same banner reads the same on `surface`,
 * inside a card or in a dialog. The light containers are authored hex, more saturated than any
 * uniform tint of their content color (`onNegativeContainerLight` over white at 12% gives `#F8EAEA`,
 * not `#F6D9DA`). The dark containers are each content hue mixed at 14% over the dark card tone,
 * frozen to an opaque color.
 */
data class SemanticColors(
    val positiveContainer: Color,
    val onPositiveContainer: Color,
    val neutralContainer: Color,
    val onNeutralContainer: Color,
    val negativeContainer: Color,
    val onNegativeContainer: Color,
)

val lightSemanticColors = SemanticColors(
    positiveContainer = positiveContainerLight,
    onPositiveContainer = onPositiveContainerLight,
    neutralContainer = neutralContainerLight,
    onNeutralContainer = onNeutralContainerLight,
    negativeContainer = negativeContainerLight,
    onNegativeContainer = onNegativeContainerLight,
)

val darkSemanticColors = SemanticColors(
    positiveContainer = positiveContainerDark,
    onPositiveContainer = onPositiveContainerDark,
    neutralContainer = neutralContainerDark,
    onNeutralContainer = onNeutralContainerDark,
    negativeContainer = negativeContainerDark,
    onNegativeContainer = onNegativeContainerDark,
)

val LocalSemanticColors = staticCompositionLocalOf { lightSemanticColors }

val MaterialTheme.semanticColors: SemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSemanticColors.current
