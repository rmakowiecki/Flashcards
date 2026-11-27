package com.rossomak.flashcards.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.rossomak.flashcards.core.designsystem.theme.FlashcardsColors
import com.rossomak.flashcards.core.designsystem.theme.FlashcardsMaterialTheme
import com.rossomak.flashcards.core.designsystem.theme.darkFlashcardsColors
import com.rossomak.flashcards.core.designsystem.theme.lightFlashcardsColors

/** The app's theme: the light or dark [FlashcardsColors] by the system setting. There is no dynamic color (ADR-0078). */
@Composable
fun FlashcardsTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    FlashcardsTheme(colors = if (darkTheme) darkFlashcardsColors else lightFlashcardsColors, content = content)
}

/** The theme with explicit [colors]; the debug dark theme tuner uses it to apply its colors live. */
@Composable
fun FlashcardsTheme(colors: FlashcardsColors, content: @Composable () -> Unit) {
    FlashcardsMaterialTheme(colors = colors, content = content)
}
