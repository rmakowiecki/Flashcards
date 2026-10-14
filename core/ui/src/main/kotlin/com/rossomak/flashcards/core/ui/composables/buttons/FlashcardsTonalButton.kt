package com.rossomak.flashcards.core.ui.composables.buttons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.backdrop.blurBackdrop
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing

/**
 * The secondary-emphasis button — a tinted fill without the [FlashcardsFilledButton]'s gradient
 * weight ("Edit", "Save changes"). Use where an action matters but shouldn't compete with the
 * screen's primary CTA.
 */
@Composable
fun FlashcardsTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: FlashcardsComponentSize = FlashcardsComponentSize.Normal,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconPosition: FlashcardsButtonIconPosition = FlashcardsButtonIconPosition.Leading,
    style: FlashcardsComponentStyle = FlashcardsComponentStyle.OnSurface,
    blursBackdrop: Boolean = style == FlashcardsComponentStyle.OnGradient,
) {
    val onGradient = style == FlashcardsComponentStyle.OnGradient
    val metrics = size.metrics()
    val shape = RoundedCornerShape(MaterialTheme.cornerRadius.full)

    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
            .height(metrics.height)
            .blurBackdrop(shape = shape, enabled = blursBackdrop),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = enabledButtonContainerColorFor(style, MaterialTheme.brandColors.tonalButtonContainer),
            contentColor = enabledButtonContentColorFor(style, MaterialTheme.brandColors.onTonalButtonContainer),
            disabledContainerColor = disabledButtonContainerColorFor(style),
            disabledContentColor = disabledButtonContentColorFor(style),
        ),
        border = when {
            !onGradient -> null
            !enabled -> BorderStroke(MaterialTheme.sizes.onGradientButtonBorder, disabledButtonContentColorFor(style))
            else -> BorderStroke(MaterialTheme.sizes.onGradientButtonBorder, MaterialTheme.brandColors.onGradientBorder)
        },
        contentPadding = PaddingValues(horizontal = metrics.horizontalPadding, vertical = MaterialTheme.spacing.none),
    ) {
        FlashcardsButtonContent(text = text, icon = icon, iconPosition = iconPosition, metrics = metrics)
    }
}

@ShowkaseComposable(name = "Tonal", group = "Buttons")
@Composable
fun FlashcardsTonalButtonShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsTonalButton(text = "Edit", onClick = {}, icon = Icons.Default.Edit)
        }
    }
}

@ShowkaseComposable(name = "Tonal — small", group = "Buttons")
@Composable
fun FlashcardsTonalButtonSmallShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsTonalButton(text = "Edit", onClick = {}, size = FlashcardsComponentSize.Small, icon = Icons.Default.Edit)
        }
    }
}

@ShowkaseComposable(name = "Tonal — disabled", group = "Buttons")
@Composable
fun FlashcardsTonalButtonDisabledShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsTonalButton(text = "Edit", onClick = {}, enabled = false)
        }
    }
}

@ShowkaseComposable(name = "Tonal — on gradient", group = "Buttons")
@Preview
@Composable
fun FlashcardsTonalButtonOnGradientShowcase() {
    FlashcardsTheme {
        Box(
            modifier = Modifier
                .background(MaterialTheme.brandColors.topBarGradient)
                .padding(MaterialTheme.spacing.small),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall)) {
                FlashcardsTonalButton(
                    text = "Play audio",
                    onClick = {},
                    icon = Icons.Default.PlayArrow,
                    style = FlashcardsComponentStyle.OnGradient,
                )
                FlashcardsTonalButton(
                    text = "Play audio",
                    onClick = {},
                    icon = Icons.Default.PlayArrow,
                    style = FlashcardsComponentStyle.OnGradient,
                    enabled = false,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsTonalButtonPreview() {
    FlashcardsTheme {
        Surface {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
            ) {
                FlashcardsTonalButton(text = "Edit", onClick = {}, icon = Icons.Default.Edit)
                FlashcardsTonalButton(text = "Edit", onClick = {}, icon = Icons.Default.Edit, enabled = false)
            }
        }
    }
}
