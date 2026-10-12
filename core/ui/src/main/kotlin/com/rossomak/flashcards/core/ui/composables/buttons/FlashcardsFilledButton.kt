package com.rossomak.flashcards.core.ui.composables.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.spacing

/**
 * The primary CTA button — filled with [com.rossomak.flashcards.core.ui.theme.BrandColors.ctaButtonGradient].
 * One per screen, reserved for the single most important action ("Start studying", "New deck").
 */
@Composable
fun FlashcardsFilledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: FlashcardsComponentSize = FlashcardsComponentSize.Normal,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconPosition: FlashcardsButtonIconPosition = FlashcardsButtonIconPosition.Leading,
    style: FlashcardsComponentStyle = FlashcardsComponentStyle.OnSurface,
) {
    val onGradient = style == FlashcardsComponentStyle.OnGradient
    val metrics = size.metrics()
    val shape = RoundedCornerShape(MaterialTheme.cornerRadius.full)
    val colors = filledButtonColorsFor(style)
    val gradientModifier = if (onGradient) {
        Modifier
    } else {
        Modifier.background(
            brush = MaterialTheme.brandColors.ctaButtonGradient,
            shape = shape,
            alpha = if (enabled) 1f else DISABLED_BUTTON_ALPHA,
        )
    }

    Button(
        onClick = onClick,
        modifier = modifier.height(metrics.height).then(gradientModifier),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.containerColor,
            contentColor = colors.contentColor,
            disabledContainerColor = colors.disabledContainerColor,
            disabledContentColor = colors.disabledContentColor,
        ),
        contentPadding = PaddingValues(horizontal = metrics.horizontalPadding, vertical = MaterialTheme.spacing.none),
    ) {
        FlashcardsButtonContent(text = text, icon = icon, iconPosition = iconPosition, metrics = metrics)
    }
}

@ShowkaseComposable(name = "Filled", group = "Buttons")
@Composable
fun FlashcardsFilledButtonShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsFilledButton(text = "Start studying", onClick = {})
        }
    }
}

@ShowkaseComposable(name = "Filled — with icon", group = "Buttons")
@Composable
fun FlashcardsFilledButtonWithIconShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsFilledButton(text = "New deck", onClick = {}, icon = Icons.Default.Add)
        }
    }
}

@ShowkaseComposable(name = "Filled — small", group = "Buttons")
@Composable
fun FlashcardsFilledButtonSmallShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsFilledButton(text = "Add card", onClick = {}, size = FlashcardsComponentSize.Small, icon = Icons.Default.Add)
        }
    }
}

@ShowkaseComposable(name = "Filled — disabled", group = "Buttons")
@Composable
fun FlashcardsFilledButtonDisabledShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsFilledButton(text = "Start studying", onClick = {}, enabled = false)
        }
    }
}

@ShowkaseComposable(name = "Filled — on gradient", group = "Buttons")
@Preview
@Composable
fun FlashcardsFilledButtonOnGradientShowcase() {
    FlashcardsTheme {
        Box(
            modifier = Modifier
                .background(MaterialTheme.brandColors.topBarGradient)
                .padding(MaterialTheme.spacing.small),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall)) {
                FlashcardsFilledButton(
                    text = "Study now",
                    onClick = {},
                    icon = Icons.Default.School,
                    style = FlashcardsComponentStyle.OnGradient,
                )
                FlashcardsFilledButton(
                    text = "Study now",
                    onClick = {},
                    icon = Icons.Default.School,
                    style = FlashcardsComponentStyle.OnGradient,
                    enabled = false,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsFilledButtonPreview() {
    FlashcardsTheme {
        Surface {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
            ) {
                FlashcardsFilledButton(text = "New deck", onClick = {}, icon = Icons.Default.Add)
                FlashcardsFilledButton(text = "New deck", onClick = {}, icon = Icons.Default.Add, enabled = false)
            }
        }
    }
}
