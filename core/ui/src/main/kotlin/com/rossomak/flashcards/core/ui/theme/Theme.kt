package com.rossomak.flashcards.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.rossomak.flashcards.core.designsystem.theme.FlashcardsColors
import com.rossomak.flashcards.core.designsystem.theme.LocalBrandColors
import com.rossomak.flashcards.core.designsystem.theme.LocalRatingColors
import com.rossomak.flashcards.core.designsystem.theme.LocalSemanticColors
import com.rossomak.flashcards.core.designsystem.theme.appTypography
import com.rossomak.flashcards.core.designsystem.theme.darkFlashcardsColors
import com.rossomak.flashcards.core.designsystem.theme.lightFlashcardsColors

@Composable
fun FlashcardsTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    FlashcardsTheme(colors = if (darkTheme) darkFlashcardsColors else lightFlashcardsColors, content = content)
}

/** The theme with explicit [colors]; the debug dark theme tuner uses it to apply its colors live. */
@Composable
fun FlashcardsTheme(colors: FlashcardsColors, content: @Composable () -> Unit) {
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
