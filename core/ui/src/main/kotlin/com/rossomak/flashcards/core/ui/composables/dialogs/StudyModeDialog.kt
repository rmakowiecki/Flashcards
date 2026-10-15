package com.rossomak.flashcards.core.ui.composables.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.ui.R
import com.rossomak.flashcards.core.ui.composables.icon

/**
 * Picks how cards are answered during the session. Uses option cards rather than plain radio rows
 * because the two modes differ in interaction model, not just name.
 *
 * Deferred commit — [draft] is applied only by [onConfirm].
 */
@Composable
fun StudyModeDialog(
    draft: StudyMode,
    onDraftChange: (StudyMode) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    keepAsDefault: Boolean? = null,
    onKeepAsDefaultChange: (Boolean) -> Unit = {},
) {
    FlashcardsSingleActionDialog(
        title = stringResource(R.string.study_mode_dialog_title),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier,
        keepAsDefault = keepAsDefault,
        onKeepAsDefaultChange = onKeepAsDefaultChange,
    ) {
        FlashcardsSingleSelectGroup {
            FlashcardsOptionCard(
                icon = StudyMode.Rated.icon,
                title = stringResource(R.string.study_mode_rated_label),
                description = stringResource(R.string.study_mode_rated_message),
                selected = draft == StudyMode.Rated,
                onSelect = { onDraftChange(StudyMode.Rated) },
            )
            FlashcardsOptionCard(
                icon = StudyMode.Fast.icon,
                title = stringResource(R.string.study_mode_fast_label),
                description = stringResource(R.string.study_mode_fast_message),
                selected = draft == StudyMode.Fast,
                onSelect = { onDraftChange(StudyMode.Fast) },
            )
        }
    }
}

@Preview
@Composable
private fun StudyModeDialogPreview() {
    StudyModeDialog(
        draft = StudyMode.Rated,
        onDraftChange = {},
        onConfirm = {},
        onDismiss = {},
        keepAsDefault = false,
    )
}
