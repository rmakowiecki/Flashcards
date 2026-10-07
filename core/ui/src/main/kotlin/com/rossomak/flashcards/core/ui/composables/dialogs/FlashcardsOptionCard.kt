package com.rossomak.flashcards.core.ui.composables.dialogs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.composables.FlashcardsIconTile
import com.rossomak.flashcards.core.ui.composables.common.disabledAlpha
import com.rossomak.flashcards.core.ui.theme.FlashcardsMotion
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing

/** Opacity of the primary container used as the selected card's fill. */
private const val SELECTED_TINT_ALPHA = 0.10f

/**
 * A single-select option that has to be *explained*, not just named: a leading icon tile, a title,
 * a description, and a trailing radio inside a bordered card that tints and outlines when selected.
 *
 * Use this instead of [FlashcardsRadioRow] whenever the difference between the options isn't
 * obvious from their labels alone ("Voice answering: on / off", "Study mode: rated / fast").
 *
 * The whole card is one selectable node with `Role.RadioButton`, so the inner [RadioButton] takes
 * `onClick = null`. Place inside a [FlashcardsSingleSelectGroup].
 */
@Composable
fun FlashcardsOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = SELECTED_TINT_ALPHA)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        animationSpec = tween(FlashcardsMotion.DURATION_SHORT_MS, easing = FlashcardsMotion.StandardEasing),
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(FlashcardsMotion.DURATION_SHORT_MS, easing = FlashcardsMotion.StandardEasing),
    )

    val shape = RoundedCornerShape(MaterialTheme.cornerRadius.card)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .disabledAlpha(enabled)
            // Clipped before `selectable`, not by the Surface: the Surface only clips what it
            // draws, while the indication belongs to the modifier node above it and would
            // otherwise ripple as a rectangle over the card's rounded corners.
            .clip(shape)
            .selectable(selected = selected, enabled = enabled, onClick = onSelect, role = Role.RadioButton),
        shape = shape,
        color = containerColor,
        border = BorderStroke(
            width = if (selected) MaterialTheme.sizes.tagChipBorder else MaterialTheme.sizes.hairline,
            color = borderColor,
        ),
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlashcardsIconTile(
                icon = icon,
                contentDescription = null,
                contentColor = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = MaterialTheme.spacing.small),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@ShowkaseComposable(name = "Option card", group = "Dialogs")
@Composable
fun FlashcardsOptionCardShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsSingleSelectGroup(modifier = Modifier.padding(MaterialTheme.spacing.small)) {
                FlashcardsOptionCard(
                    icon = Icons.Default.Mic,
                    title = "On",
                    description = "Speak your answer aloud; it's graded hands-free.",
                    selected = true,
                    onSelect = {},
                )
                FlashcardsOptionCard(
                    icon = Icons.Default.TouchApp,
                    title = "Off",
                    description = "Reveal and rate each card by tapping.",
                    selected = false,
                    onSelect = {},
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsOptionCardPreview() {
    FlashcardsOptionCardShowcase()
}
