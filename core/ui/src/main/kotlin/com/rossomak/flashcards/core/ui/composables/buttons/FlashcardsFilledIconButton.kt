package com.rossomak.flashcards.core.ui.composables.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.spacing

/**
 * The family's gradient-filled icon-only type — a circular affordance filled with
 * [com.rossomak.flashcards.core.ui.theme.BrandColors.ctaButtonGradient], for icon-only actions that
 * carry the same visual weight as [FlashcardsFilledButton] (e.g. a session's voice play/pause
 * transport control). Color scheme mirrors [FlashcardsFilledButton] exactly via the shared
 * [filledButtonColorsFor] helper. See [FlashcardsTonalIconButton] for the tonal counterpart.
 */
@Composable
fun FlashcardsFilledIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: FlashcardsComponentSize = FlashcardsComponentSize.Normal,
    enabled: Boolean = true,
    style: FlashcardsComponentStyle = FlashcardsComponentStyle.OnSurface,
) {
    val onGradient = style == FlashcardsComponentStyle.OnGradient
    val metrics = size.metrics()
    val colors = filledButtonColorsFor(style)
    val gradientModifier = if (onGradient) {
        Modifier
    } else {
        Modifier.background(
            brush = MaterialTheme.brandColors.ctaButtonGradient,
            shape = CircleShape,
            alpha = if (enabled) 1f else DISABLED_GRADIENT_ALPHA,
        )
    }

    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(metrics.height).then(gradientModifier),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = colors.containerColor,
            contentColor = colors.contentColor,
            disabledContainerColor = colors.disabledContainerColor,
            disabledContentColor = colors.disabledContentColor,
        ),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

@ShowkaseComposable(name = "Icon — filled", group = "Buttons")
@Composable
fun FlashcardsFilledIconButtonShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsFilledIconButton(icon = Icons.Default.PlayArrow, contentDescription = "Play", onClick = {})
        }
    }
}

@ShowkaseComposable(name = "Icon — filled, small", group = "Buttons")
@Composable
fun FlashcardsFilledIconButtonSmallShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsFilledIconButton(
                icon = Icons.Default.PlayArrow,
                contentDescription = "Play",
                onClick = {},
                size = FlashcardsComponentSize.Small,
            )
        }
    }
}

@ShowkaseComposable(name = "Icon — filled, disabled", group = "Buttons")
@Composable
fun FlashcardsFilledIconButtonDisabledShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsFilledIconButton(icon = Icons.Default.PlayArrow, contentDescription = "Play", onClick = {}, enabled = false)
        }
    }
}

@ShowkaseComposable(name = "Icon — filled, on gradient", group = "Buttons")
@Preview
@Composable
fun FlashcardsFilledIconButtonOnGradientShowcase() {
    FlashcardsTheme {
        Box(
            modifier = Modifier
                .background(MaterialTheme.brandColors.topBarGradient)
                .padding(MaterialTheme.spacing.small),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall)) {
                FlashcardsFilledIconButton(
                    icon = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    onClick = {},
                    style = FlashcardsComponentStyle.OnGradient,
                )
                FlashcardsFilledIconButton(
                    icon = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    onClick = {},
                    style = FlashcardsComponentStyle.OnGradient,
                    enabled = false,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsFilledIconButtonPreview() {
    FlashcardsTheme {
        Surface {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
            ) {
                FlashcardsFilledIconButton(icon = Icons.Default.PlayArrow, contentDescription = "Play", onClick = {})
                FlashcardsFilledIconButton(icon = Icons.Default.PlayArrow, contentDescription = "Play", onClick = {}, enabled = false)
            }
        }
    }
}
