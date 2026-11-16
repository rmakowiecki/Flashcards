package com.rossomak.flashcards.web.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.designsystem.theme.LocalBrandColors
import com.rossomak.flashcards.core.designsystem.theme.LocalRatingColors
import com.rossomak.flashcards.core.designsystem.theme.LocalSemanticColors
import com.rossomak.flashcards.core.designsystem.theme.appTypography
import com.rossomak.flashcards.core.designsystem.theme.darkFlashcardsColors

/**
 * The website's theme: the app's shared tokens, fixed to the dark scheme because every page sits on
 * the brand screen gradient, which never flips. Text defaults to the on-gradient white.
 */
@Composable
fun WebTheme(content: @Composable () -> Unit) {
    val colors = darkFlashcardsColors
    CompositionLocalProvider(
        LocalBrandColors provides colors.brand,
        LocalSemanticColors provides colors.semantic,
        LocalRatingColors provides colors.rating,
    ) {
        MaterialTheme(colorScheme = colors.colorScheme, typography = appTypography()) {
            CompositionLocalProvider(LocalContentColor provides colors.brand.onGradientContent, content = content)
        }
    }
}

/** Layout values that exist only on the website. */
object WebDimensions {
    /** Widest a page's text column grows, so lines stay readable on a desktop screen. */
    val contentMaxWidth: Dp = 720.dp

    /** Widest the hero's one-line description runs before it wraps. */
    val heroDescriptionMaxWidth: Dp = 760.dp

    /** How far the hero rises as it fades up on first load. */
    val entranceRise: Dp = 24.dp

    val scrollHintChevronSize: Dp = 20.dp

    /** How far the scroll hint's chevron bobs. */
    val scrollHintBob: Dp = 4.dp

    /** Widest a side-by-side feature page's text and phone row grows on a wide window. */
    val featureRowMaxWidth: Dp = 1680.dp

    /** Keeps a side-by-side feature page clear of the window edges and the page dots. */
    val featureSidePadding: Dp = 112.dp

    /** Between the text and the phone on a side-by-side feature page. */
    val featureGap: Dp = 96.dp

    val featureTextMaxWidth: Dp = 600.dp

    /** Tallest a screenshot is drawn, on a large desktop window. */
    val phoneMaxHeight: Dp = 860.dp

    /** Keeps a stacked feature page clear of the page dots. */
    val stackedEndPadding: Dp = 40.dp

    /** The legal pages' logo, at the top of their scrolling column: a share of the window's width, within bounds. */
    const val SUBPAGE_LOGO_WIDTH_SHARE = 0.28f
    val subpageLogoMinWidth: Dp = 240.dp
    val subpageLogoMaxWidth: Dp = 560.dp

    val pageDotSize: Dp = 6.dp
    val pageDotCurrentSize: Dp = 10.dp
    val pageDotTouchTarget: Dp = 24.dp
    val pageDotsEndPadding: Dp = 8.dp
}

/** Colors that exist only on the website. */
object WebColors {
    /** The soft glow behind landing text, which keeps it legible over the dense graph. */
    val textScrim: Color = Color(0xFF1A1C5C).copy(alpha = 0.62f)
}

/** Alpha of secondary text (subtitles, dates) against the on-gradient white. */
const val WEB_MUTED_CONTENT_ALPHA = 0.8f

/** Alpha of tertiary text (the landing page's detail paragraphs), quieter than [WEB_MUTED_CONTENT_ALPHA]. */
const val WEB_FAINT_CONTENT_ALPHA = 0.66f
