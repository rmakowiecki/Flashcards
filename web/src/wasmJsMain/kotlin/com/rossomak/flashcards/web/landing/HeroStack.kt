package com.rossomak.flashcards.web.landing

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.designsystem.theme.spacing
import kotlin.math.roundToInt

/** The large logo never shrinks below this height. */
private val heroLogoMinHeight = 56.dp

/**
 * The hero's and closing page's layout: the large [logo] above [body], centered, and [footer] at the bottom edge. The
 * logo sits at [LandingFrame.heroLogoTop] when everything fits. Otherwise the block moves up, and then the logo
 * shrinks, so the block never runs into the footer or past the window's bottom. [textModifier] moves and halos the
 * logo and body as one block.
 */
@Composable
fun HeroStack(
    modifier: Modifier = Modifier,
    frame: LandingFrame,
    textModifier: Modifier = Modifier,
    logo: @Composable () -> Unit,
    body: @Composable () -> Unit,
    footer: @Composable () -> Unit = {},
) {
    val minTop = if (frame.isCompactHeight) MaterialTheme.spacing.normal else MaterialTheme.spacing.large
    val bottomGap = if (frame.isCompactHeight) MaterialTheme.spacing.normal else MaterialTheme.spacing.large
    Layout(
        modifier = modifier,
        contents = listOf({ Box(modifier = textModifier) { LogoOverBody(frame = frame, logo = logo, body = body) } }, footer),
    ) { (blockMeasurables, footerMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val footerPlaceables = footerMeasurables.map { it.measure(loose) }
        val blockBottom = constraints.maxHeight - (footerPlaceables.maxOfOrNull { it.height } ?: 0) - bottomGap.roundToPx()
        val minTopPx = minTop.roundToPx()
        val block = blockMeasurables.single().measure(loose.copy(maxHeight = (blockBottom - minTopPx).coerceAtLeast(0)))
        val top = frame.heroLogoTop.roundToPx().coerceAtMost(blockBottom - block.height).coerceAtLeast(minTopPx)
        layout(constraints.maxWidth, constraints.maxHeight) {
            // Above the footer, so the footer's halo never dims the hint. Placed first, so it comes first in keyboard order.
            block.place(x = (constraints.maxWidth - block.width) / 2, y = top, zIndex = 1f)
            footerPlaceables.forEach { it.place(x = (constraints.maxWidth - it.width) / 2, y = constraints.maxHeight - it.height) }
        }
    }
}

/** The logo at its full size, or smaller where the height given leaves it less room above [body]. */
@Composable
private fun LogoOverBody(frame: LandingFrame, logo: @Composable () -> Unit, body: @Composable () -> Unit) {
    Layout(contents = listOf(logo, body)) { (logoMeasurables, bodyMeasurables), constraints ->
        val bodyPlaceable = bodyMeasurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val roomForLogo = if (constraints.hasBoundedHeight) {
            (constraints.maxHeight - bodyPlaceable.height) / (1f - HERO_LOGO_TEXT_OVERLAP_SHARE)
        } else {
            Float.POSITIVE_INFINITY
        }
        val logoHeight = minOf(frame.heroLogoHeight.toPx(), roomForLogo, constraints.maxWidth / LOGO_ASPECT_RATIO).coerceAtLeast(heroLogoMinHeight.toPx())
        val logoPlaceable = logoMeasurables.single().measure(Constraints.fixed((logoHeight * LOGO_ASPECT_RATIO).roundToInt(), logoHeight.roundToInt()))
        // The body tucks into the logo's own empty margin.
        val bodyTop = (logoPlaceable.height * (1f - HERO_LOGO_TEXT_OVERLAP_SHARE)).roundToInt()
        val width = maxOf(logoPlaceable.width, bodyPlaceable.width)
        layout(width, bodyTop + bodyPlaceable.height) {
            logoPlaceable.place(x = (width - logoPlaceable.width) / 2, y = 0)
            bodyPlaceable.place(x = (width - bodyPlaceable.width) / 2, y = bodyTop)
        }
    }
}
