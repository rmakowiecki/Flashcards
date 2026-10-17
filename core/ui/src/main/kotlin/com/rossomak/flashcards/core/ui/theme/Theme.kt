package com.rossomak.flashcards.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.rossomak.flashcards.core.designsystem.theme.FlashcardsDarkColorScheme
import com.rossomak.flashcards.core.designsystem.theme.FlashcardsLightColorScheme
import com.rossomak.flashcards.core.designsystem.theme.LocalBrandColors
import com.rossomak.flashcards.core.designsystem.theme.LocalSemanticColors
import com.rossomak.flashcards.core.designsystem.theme.appTypography
import com.rossomak.flashcards.core.designsystem.theme.darkBrandColors
import com.rossomak.flashcards.core.designsystem.theme.darkSemanticColors
import com.rossomak.flashcards.core.designsystem.theme.lightBrandColors
import com.rossomak.flashcards.core.designsystem.theme.lightSemanticColors

@Composable
fun FlashcardsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> FlashcardsDarkColorScheme
        else -> FlashcardsLightColorScheme
    }

    val brandColors = if (darkTheme) darkBrandColors else lightBrandColors
    val semanticColors = if (darkTheme) darkSemanticColors else lightSemanticColors

    CompositionLocalProvider(
        LocalBrandColors provides brandColors,
        LocalSemanticColors provides semanticColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = appTypography(),
            content = content,
        )
    }
}
