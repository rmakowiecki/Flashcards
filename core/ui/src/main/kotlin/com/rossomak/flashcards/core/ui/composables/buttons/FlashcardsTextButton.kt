package com.rossomak.flashcards.core.ui.composables.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * The lowest-emphasis button — no fill, no border, text (+ optional icon) only ("Skip").
 * Use for a dismissive or auxiliary action that shouldn't draw the eye.
 *
 * Unlike the other three `Flashcards*Button`s, this doesn't override M3 [TextButton]'s
 * `contentPadding` — a text button has no visible container edge, so its own tighter default
 * spacing (rather than the shared [FlashcardsComponentSize] horizontal padding) is the right fit.
 *
 * [contentColor] overrides the label color for the rare action that must read as consequential —
 * Settings' "Sign out" is the only one today. It is deliberately only on this button: the filled
 * and tonal buttons carry the CTA gradient, and the design language has no destructive variant of
 * those (see [FlashcardsDecisionDialog][com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardsDecisionDialog]).
 * Ignored when [style] is `OnGradient`, which fixes its own contrast pairing.
 */
@Composable
fun FlashcardsTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: FlashcardsComponentSize = FlashcardsComponentSize.Normal,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconPosition: FlashcardsButtonIconPosition = FlashcardsButtonIconPosition.Leading,
    style: FlashcardsComponentStyle = FlashcardsComponentStyle.OnSurface,
    contentColor: Color? = null,
) {
    val onGradient = style == FlashcardsComponentStyle.OnGradient
    val metrics = size.metrics()

    TextButton(
        onClick = onClick,
        modifier = modifier.height(metrics.height),
        enabled = enabled,
        shape = RoundedCornerShape(MaterialTheme.cornerRadius.full),
        colors = ButtonDefaults.textButtonColors(
            contentColor = when {
                onGradient -> MaterialTheme.brandColors.onGradientContent
                else -> contentColor ?: MaterialTheme.colorScheme.primary
            },
            disabledContentColor = disabledButtonContentColorFor(style),
        ),
    ) {
        FlashcardsButtonContent(text = text, icon = icon, iconPosition = iconPosition, metrics = metrics)
    }
}

@ShowkaseComposable(name = "Text", group = "Buttons")
@Composable
fun FlashcardsTextButtonShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsTextButton(text = "Skip", onClick = {})
        }
    }
}

@ShowkaseComposable(name = "Text — with icon", group = "Buttons")
@Composable
fun FlashcardsTextButtonWithIconShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsTextButton(text = "Learn more", onClick = {}, icon = Icons.Default.Info)
        }
    }
}

@ShowkaseComposable(name = "Text — small", group = "Buttons")
@Composable
fun FlashcardsTextButtonSmallShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsTextButton(text = "Skip", onClick = {}, size = FlashcardsComponentSize.Small)
        }
    }
}

@ShowkaseComposable(name = "Text — disabled", group = "Buttons")
@Composable
fun FlashcardsTextButtonDisabledShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsTextButton(text = "Skip", onClick = {}, enabled = false)
        }
    }
}

@ShowkaseComposable(name = "Text — on gradient", group = "Buttons")
@Preview
@Composable
fun FlashcardsTextButtonOnGradientShowcase() {
    FlashcardsTheme {
        Box(
            modifier = Modifier
                .background(MaterialTheme.brandColors.topBarGradient)
                .padding(MaterialTheme.spacing.small),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall)) {
                FlashcardsTextButton(text = "Not sure", onClick = {}, style = FlashcardsComponentStyle.OnGradient)
                FlashcardsTextButton(text = "Not sure", onClick = {}, style = FlashcardsComponentStyle.OnGradient, enabled = false)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsTextButtonPreview() {
    FlashcardsTheme {
        Surface {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
            ) {
                FlashcardsTextButton(text = "Learn more", onClick = {}, icon = Icons.Default.Info)
                FlashcardsTextButton(text = "Learn more", onClick = {}, icon = Icons.Default.Info, enabled = false)
            }
        }
    }
}
