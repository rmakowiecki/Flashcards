package com.rossomak.flashcards.core.ui.composables.dialogs

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.composables.common.disabledAlpha
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.spacing

/**
 * The plainest single-select row: a leading radio and a label, no container.
 *
 * Use when the options only need naming ("Default", "Easiest first"). When a choice needs
 * *explaining* rather than naming, use [FlashcardsOptionCard] instead.
 *
 * The whole row is one selectable node with `Role.RadioButton`, so the inner [RadioButton] takes
 * `onClick = null` and TalkBack announces the row as a single control. Place inside a
 * [FlashcardsSingleSelectGroup].
 *
 * Carries no press/hover indication: the row has no container to tint, so a background band the
 * width of the dialog would read as a much heavier control than this row is. The radio's own
 * state change is the feedback.
 */
@Composable
fun FlashcardsRadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .disabledAlpha(enabled)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
        )
        Spacer(modifier = Modifier.width(MaterialTheme.spacing.xsmall))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@ShowkaseComposable(name = "Radio row", group = "Dialogs")
@Composable
fun FlashcardsRadioRowShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsSingleSelectGroup {
                FlashcardsRadioRow(label = "Default", selected = false, onSelect = {})
                FlashcardsRadioRow(label = "Easiest first", selected = false, onSelect = {})
                FlashcardsRadioRow(label = "Hardest first", selected = true, onSelect = {})
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsRadioRowPreview() {
    FlashcardsRadioRowShowcase()
}
