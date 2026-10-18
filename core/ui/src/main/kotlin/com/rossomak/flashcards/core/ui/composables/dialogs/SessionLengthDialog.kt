package com.rossomak.flashcards.core.ui.composables.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.core.ui.R
import com.rossomak.flashcards.core.ui.composables.FlashcardsStepper

/**
 * Picks how many cards the session draws.
 *
 * Deferred commit — the stepper edits [draft] and nothing is applied until [onConfirm]. That is
 * what makes a stepper safe here: under live commit each tap would re-run card selection and, once
 * persistence lands, write a preference per tap.
 *
 * Pass [keepAsDefault] as non-null where the choice is session-scoped and can optionally be
 * promoted to a permanent default (the Preview Study Session screen); pass `null` on the Settings
 * screen, where the change is permanent by definition.
 */
@Composable
fun SessionLengthDialog(
    draft: Int,
    range: IntRange,
    step: Int,
    onDraftChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    keepAsDefault: Boolean? = null,
    onKeepAsDefaultChange: (Boolean) -> Unit = {},
) {
    FlashcardsSingleActionDialog(
        title = stringResource(R.string.session_length_dialog_title),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier,
        supportingText = stringResource(R.string.session_length_dialog_message),
        keepAsDefault = keepAsDefault,
        onKeepAsDefaultChange = onKeepAsDefaultChange,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MaterialTheme.spacing.medium),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlashcardsStepper(
                value = draft,
                onDecrement = { onDraftChange((draft - step).coerceAtLeast(range.first)) },
                onIncrement = { onDraftChange((draft + step).coerceAtMost(range.last)) },
                decrementContentDescription = stringResource(R.string.session_length_decrement_cd),
                incrementContentDescription = stringResource(R.string.session_length_increment_cd),
                valueLabel = pluralStringResource(R.plurals.session_length_cards_label, draft, draft),
                decrementEnabled = draft > range.first,
                incrementEnabled = draft < range.last,
            )
        }
    }
}

@Preview
@Composable
private fun SessionLengthDialogPreview() {
    SessionLengthDialog(
        draft = 20,
        range = 10..50,
        step = 5,
        onDraftChange = {},
        onConfirm = {},
        onDismiss = {},
    )
}

@Preview
@Composable
private fun SessionLengthDialogSessionPreview() {
    SessionLengthDialog(
        draft = 20,
        range = 10..50,
        step = 5,
        onDraftChange = {},
        onConfirm = {},
        onDismiss = {},
        keepAsDefault = false,
    )
}
