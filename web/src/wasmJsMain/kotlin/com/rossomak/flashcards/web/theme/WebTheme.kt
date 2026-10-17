package com.rossomak.flashcards.web.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.designsystem.theme.FlashcardsDarkColorScheme
import com.rossomak.flashcards.core.designsystem.theme.LocalBrandColors
import com.rossomak.flashcards.core.designsystem.theme.LocalSemanticColors
import com.rossomak.flashcards.core.designsystem.theme.appTypography
import com.rossomak.flashcards.core.designsystem.theme.darkBrandColors
import com.rossomak.flashcards.core.designsystem.theme.darkSemanticColors

/**
 * The website's theme: the app's shared tokens, fixed to the dark scheme because every page sits on
 * the brand screen gradient, which never flips. Text defaults to the on-gradient white.
 */
@Composable
fun WebTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBrandColors provides darkBrandColors,
        LocalSemanticColors provides darkSemanticColors,
    ) {
        MaterialTheme(colorScheme = FlashcardsDarkColorScheme, typography = appTypography()) {
            CompositionLocalProvider(LocalContentColor provides darkBrandColors.onGradientContent, content = content)
        }
    }
}

/** Layout values that exist only on the website. */
object WebDimensions {
    /** Widest a page's text column grows, so lines stay readable on a desktop screen. */
    val contentMaxWidth: Dp = 720.dp

    /** Width of the logo on the home page. */
    val homeLogoWidth: Dp = 280.dp
}

/** Alpha of secondary text (subtitles, dates) against the on-gradient white. */
const val WEB_MUTED_CONTENT_ALPHA = 0.72f
