package com.rossomak.flashcards.core.ui.composables.dialogs

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.window.DialogProperties
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.R
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsDestructiveButton
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTonalButton
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme

/**
 * A two-way fork: a discard path and a commit path, both explicit.
 *
 * **Deferred commit,** like [FlashcardsSingleActionDialog] — the draft is applied only by
 * [onConfirm].
 *
 * **Dismissal:** a tap outside is **inert** (`dismissOnClickOutside = false`), because the choice
 * is deliberate and shouldn't be resolved by a stray tap. A back press *does* dismiss, and it is
 * wired to [onCancel] — the same path as the Cancel button, so there is exactly one discard
 * callback and no separate `onDismiss`. Back is not made inert: an unswipeable modal reads as a
 * bug, and discard is the safe side of the fork.
 *
 * [confirmLabel] has no default on purpose. The design language uses a specific verb ("Exit",
 * "Submit"); a default would quietly invite "OK".
 *
 * Irreversible forks use the same CTA gradient as everything else, per the design language. The one
 * exception is [isDestructive], for actions that permanently delete the User's data, such as deleting
 * the account: its confirm is a [FlashcardsDestructiveButton].
 *
 * @param confirmEnabled gates the commit action only. Compute it in the ViewModel from the draft
 * (e.g. "at least one row checked"), where it is unit-testable.
 */
@Composable
fun FlashcardsDecisionDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supportingText: String? = null,
    cancelLabel: String = stringResource(R.string.common_cancel_button),
    confirmEnabled: Boolean = true,
    isDestructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    FlashcardsDialog(
        title = title,
        // Back press only — dismissOnClickOutside is off, so the scrim never reaches this.
        onDismissRequest = onCancel,
        confirmButton = {
            if (isDestructive) {
                FlashcardsDestructiveButton(text = confirmLabel, onClick = onConfirm, enabled = confirmEnabled)
            } else {
                FlashcardsFilledButton(text = confirmLabel, onClick = onConfirm, enabled = confirmEnabled)
            }
        },
        modifier = modifier,
        icon = icon,
        supportingText = supportingText,
        dismissButton = { FlashcardsTonalButton(text = cancelLabel, onClick = onCancel) },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
        content = content,
    )
}

@ShowkaseComposable(name = "Decision dialog", group = "Dialogs")
@Composable
fun FlashcardsDecisionDialogShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsDecisionDialog(
                title = "Exit session?",
                confirmLabel = "Exit",
                onConfirm = {},
                onCancel = {},
                icon = Icons.AutoMirrored.Filled.Logout,
                supportingText = "All progress for this session will be lost. This can't be undone.",
            )
        }
    }
}

@ShowkaseComposable(name = "Decision dialog — destructive", group = "Dialogs")
@Composable
fun FlashcardsDestructiveDecisionDialogShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsDecisionDialog(
                title = "Delete account?",
                confirmLabel = "Delete",
                onConfirm = {},
                onCancel = {},
                icon = Icons.Default.DeleteForever,
                supportingText = "Your progress will be permanently deleted. This can't be undone.",
                isDestructive = true,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsDecisionDialogPreview() {
    FlashcardsDecisionDialogShowcase()
}

@PreviewLightDark
@Composable
private fun FlashcardsDestructiveDecisionDialogPreview() {
    FlashcardsDestructiveDecisionDialogShowcase()
}
