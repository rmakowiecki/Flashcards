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
    val contentMaxWidth: Dp = 720.dp
    val heroDescriptionMaxWidth: Dp = 760.dp
    val scrollHintChevronSize: Dp = 20.dp
    val scrollHintBob: Dp = 4.dp
    val featureRowMaxWidth: Dp = 1680.dp

    /** Keeps a side-by-side feature page clear of the window edges and the page dots. */
    val featureSidePadding: Dp = 112.dp
    val featureGap: Dp = 96.dp
    val featureTextMaxWidth: Dp = 600.dp

    /** A compact side-by-side feature page packs its text next to the phone. */
    val compactFeatureTextMaxWidth: Dp = 380.dp
    val phoneMaxHeight: Dp = 860.dp
    val phoneShadowElevation: Dp = 24.dp

    /** The legal pages' logo: a share of the window's width, within bounds. */
    const val SUBPAGE_LOGO_WIDTH_SHARE = 0.28f
    val subpageLogoMinWidth: Dp = 240.dp
    val subpageLogoMaxWidth: Dp = 560.dp

    val pageDotsBlurRadius: Dp = 12.dp

    /** How far the glow behind the hero's scroll hint and links reaches past them. */
    val bottomLinksScrimReach: Dp = 160.dp
}

/** Colors that exist only on the website. */
object WebColors {
    /** The page dots' frosted pill. */
    val frostedFill: Color = Color.White.copy(alpha = 0.1f)
    val frostedBorder: Color = Color.White.copy(alpha = 0.18f)

    /** The glow behind landing text. */
    val textScrim: Color = Color(0xFF1A1C5C).copy(alpha = 0.62f)
}

/** Alpha of secondary text. */
const val WEB_MUTED_CONTENT_ALPHA = 0.8f
