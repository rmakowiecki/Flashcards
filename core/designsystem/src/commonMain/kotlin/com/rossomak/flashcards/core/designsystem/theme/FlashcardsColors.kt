package com.rossomak.flashcards.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable

/**
 * Every color a theme provides: the Material [colorScheme] and the app's own [brand], [semantic] and
 * [rating] palettes. Light and dark are one pair of fixed values, [lightFlashcardsColors] and
 * [darkFlashcardsColors], applied the same way; only the debug dark theme tuner replaces the dark one
 * at runtime.
 */
@Immutable
data class FlashcardsColors(
    val colorScheme: ColorScheme,
    val brand: BrandColors,
    val semantic: SemanticColors,
    val rating: RatingColors,
)

val lightFlashcardsColors = FlashcardsColors(
    colorScheme = FlashcardsLightColorScheme,
    brand = lightBrandColors,
    semantic = lightSemanticColors,
    rating = lightRatingColors,
)

val darkFlashcardsColors = FlashcardsColors(
    colorScheme = FlashcardsDarkColorScheme,
    brand = darkBrandColors,
    semantic = darkSemanticColors,
    rating = darkRatingColors,
)
