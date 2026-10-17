package com.rossomak.flashcards.core.ui.composables.dialogs

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.core.ui.R
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme

/**
 * A dialog with exactly one action and no discard button.
 *
 * **Deferred commit.** Nothing the user changes is applied until [onConfirm] fires. The dialog's
 * content is driven entirely by a hoisted draft; dismissing throws that draft away.
 *
 * **Dismissal:** a tap outside the dialog or a back press calls [onDismiss] and **discards**.
 * The single action button is the only commit path. There is deliberately no Cancel button —
 * the scrim already does that job — and the action is always enabled, since there is nothing to
 * validate when both exits are one tap away.
 *
 * **[keepAsDefault]** renders the "Keep as my default" checkbox as the last thing in the content
 * region, directly above the action. `null` hides the row entirely. It is a parameter rather than
 * something derived from the dialog type, so any call site can show or hide it independently; it
 * lives here rather than on the shared scaffold because a [FlashcardsDecisionDialog] has a discard
 * path, and "keep this permanently" has no coherent meaning next to a Cancel button.
 *
 * @param onConfirm applies the draft. Fired by the action button only.
 * @param onDismiss discards the draft. Fired by the scrim and the back press only.
 * @param confirmEnabled disables the action button while the draft is invalid — e.g.
 * [FlashcardFiltersDialog][com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardFiltersDialog]
 * disabling confirm once every tag has been deselected. Defaults to always-enabled, matching every
 * dialog that has nothing to validate.
 */
@Composable
fun FlashcardsSingleActionDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supportingText: String? = null,
    actionLabel: String = stringResource(R.string.common_done_button),
    confirmEnabled: Boolean = true,
    keepAsDefault: Boolean? = null,
    onKeepAsDefaultChange: (Boolean) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    FlashcardsDialog(
        title = title,
        onDismissRequest = onDismiss,
        confirmButton = {
            FlashcardsFilledButton(text = actionLabel, onClick = onConfirm, enabled = confirmEnabled)
        },
        modifier = modifier,
        icon = icon,
        supportingText = supportingText,
    ) {
        content()
        if (keepAsDefault != null) {
            KeepAsDefaultRow(checked = keepAsDefault, onCheckedChange = onKeepAsDefaultChange)
        }
    }
}

/**
 * The "Keep as my default" opt-in. Part of the draft like everything else — ticking it persists
 * nothing until the dialog's action button commits.
 *
 * Indication-free like [FlashcardsRadioRow]: a full-width press band under a secondary opt-in
 * would out-shout the dialog's action button.
 */
@Composable
private fun KeepAsDefaultRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(modifier = Modifier.width(MaterialTheme.spacing.xsmall))
        Text(
            text = stringResource(R.string.common_keep_as_default_label),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@ShowkaseComposable(name = "Single-action dialog", group = "Dialogs")
@Composable
fun FlashcardsSingleActionDialogShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsSingleActionDialog(
                title = "Sort by",
                onConfirm = {},
                onDismiss = {},
                keepAsDefault = false,
            ) {
                FlashcardsSingleSelectGroup {
                    FlashcardsRadioRow(label = "Default", selected = false, onSelect = {})
                    FlashcardsRadioRow(label = "Easiest first", selected = false, onSelect = {})
                    FlashcardsRadioRow(label = "Hardest first", selected = true, onSelect = {})
                }
            }
        }
    }
}

@ShowkaseComposable(name = "Single-action dialog — icon header", group = "Dialogs")
@Composable
fun FlashcardsSingleActionDialogIconShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsSingleActionDialog(
                title = "Session paused",
                onConfirm = {},
                onDismiss = {},
                icon = Icons.Default.Info,
                supportingText = "Your progress is saved. Pick up where you left off any time.",
                actionLabel = "Got it",
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsSingleActionDialogPreview() {
    FlashcardsSingleActionDialogShowcase()
}
