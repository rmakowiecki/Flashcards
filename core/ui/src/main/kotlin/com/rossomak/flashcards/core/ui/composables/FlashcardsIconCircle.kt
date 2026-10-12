package com.rossomak.flashcards.core.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnGradient
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnSurface
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.sizes

private val ICON_CIRCLE_SIZE = 72.dp

/**
 * Circular, tinted icon badge — the single-glyph marker used at the end or head of a flow (onboarding's
 * "All set" step; reused wherever else a screen needs the same tinted-circle-plus-icon treatment with
 * a different glyph). [style] follows the shared on-surface/on-gradient axis every `Flashcards*`
 * component family uses (ADR-0034):
 * - [OnSurface] is a flat tinted fill, matching [FlashcardsIconTile]'s default tint.
 * - [OnGradient] is the translucent white fill plus hairline border every on-gradient component
 *   shares (same tokens as [FlashcardsMetadataBadge]'s `OnGradient` style), for a screen painted with
 *   [com.rossomak.flashcards.core.ui.theme.BrandColors.screenGradient].
 */
@Composable
fun FlashcardsIconCircle(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    style: FlashcardsComponentStyle = OnSurface,
) = FlashcardsIconCircle(modifier = modifier, style = style) {
    Icon(imageVector = icon, contentDescription = contentDescription)
}

/** [FlashcardsIconCircle] with any centered [content], drawn in the circle's content color. */
@Composable
fun FlashcardsIconCircle(
    modifier: Modifier = Modifier,
    style: FlashcardsComponentStyle = OnSurface,
    content: @Composable () -> Unit,
) {
    val brandColors = MaterialTheme.brandColors
    val containerColor = when (style) {
        OnSurface -> MaterialTheme.colorScheme.secondaryContainer
        OnGradient -> brandColors.onGradientContainer
    }
    val contentColor = when (style) {
        OnSurface -> MaterialTheme.colorScheme.onSecondaryContainer
        OnGradient -> brandColors.onGradientContent
    }
    val border = when (style) {
        OnSurface -> null
        OnGradient -> BorderStroke(width = MaterialTheme.sizes.hairline, color = brandColors.onGradientBorder)
    }

    Surface(
        modifier = modifier.size(ICON_CIRCLE_SIZE),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@ShowkaseComposable(name = "Icon circle — on surface", group = "Icons")
@Composable
fun FlashcardsIconCircleOnSurfaceShowcase() {
    FlashcardsTheme {
        Surface {
            Box(modifier = Modifier.size(ICON_CIRCLE_SIZE), contentAlignment = Alignment.Center) {
                FlashcardsIconCircle(icon = Icons.Default.Celebration, contentDescription = null)
            }
        }
    }
}

@ShowkaseComposable(name = "Icon circle — on gradient", group = "Icons")
@Preview
@Composable
fun FlashcardsIconCircleOnGradientShowcase() {
    FlashcardsTheme {
        Surface(color = MaterialTheme.brandColors.screenGradientBase) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.brandColors.screenGradient)
                    .size(ICON_CIRCLE_SIZE),
                contentAlignment = Alignment.Center,
            ) {
                FlashcardsIconCircle(
                    icon = Icons.Default.Celebration,
                    contentDescription = null,
                    style = OnGradient,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsIconCirclePreview() {
    FlashcardsTheme {
        Surface {
            Box(modifier = Modifier.size(ICON_CIRCLE_SIZE), contentAlignment = Alignment.Center) {
                FlashcardsIconCircle(icon = Icons.Default.Celebration, contentDescription = null)
            }
        }
    }
}
