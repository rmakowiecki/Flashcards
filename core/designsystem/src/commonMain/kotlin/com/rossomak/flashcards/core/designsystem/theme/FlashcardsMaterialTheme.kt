package com.rossomak.flashcards.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Applies one [FlashcardsColors] and the Roboto type scale: the Material theme plus the brand,
 * semantic and rating palettes. The app's `FlashcardsTheme` and the website's `WebTheme` both wrap
 * it, so a palette added to [FlashcardsColors] reaches both by being provided here once.
 */
@Composable
fun FlashcardsMaterialTheme(colors: FlashcardsColors, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBrandColors provides colors.brand,
        LocalSemanticColors provides colors.semantic,
        LocalRatingColors provides colors.rating,
    ) {
        MaterialTheme(
            colorScheme = colors.colorScheme,
            typography = appTypography(),
            content = content,
        )
    }
}
