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
 * Picks how many times a card may be answered in a Rated session before it counts as failed.
 *
 * Deferred commit, and a stepper for the same reason [SessionLengthDialog] uses one — nothing is
 * applied until [onConfirm], so a tap costs nothing.
 *
 * Rated-only by construction: Fast mode has no rating step to retry (ADR-0025), so the screens
 * that offer this row only offer it there.
 *
 * Pass [keepAsDefault] as non-null where the choice is session-scoped and can optionally be
 * promoted to a permanent default (the Preview Study Session screen); pass `null` on the Settings
 * screen, where the change is permanent by definition.
 */
@Composable
fun RatedAttemptsDialog(
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
        title = stringResource(R.string.rated_attempts_dialog_title),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier,
        supportingText = stringResource(R.string.rated_attempts_dialog_message),
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
                decrementContentDescription = stringResource(R.string.rated_attempts_decrement_cd),
                incrementContentDescription = stringResource(R.string.rated_attempts_increment_cd),
                valueLabel = pluralStringResource(R.plurals.rated_attempts_label, draft, draft),
                decrementEnabled = draft > range.first,
                incrementEnabled = draft < range.last,
            )
        }
    }
}

@Preview
@Composable
private fun RatedAttemptsDialogPreview() {
    RatedAttemptsDialog(
        draft = 3,
        range = 1..5,
        step = 1,
        onDraftChange = {},
        onConfirm = {},
        onDismiss = {},
    )
}

@Preview
@Composable
private fun RatedAttemptsDialogSessionPreview() {
    RatedAttemptsDialog(
        draft = 3,
        range = 1..5,
        step = 1,
        onDraftChange = {},
        onConfirm = {},
        onDismiss = {},
        keepAsDefault = false,
    )
}
