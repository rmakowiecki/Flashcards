package com.rossomak.flashcards.feature.account

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rossomak.flashcards.core.domain.model.BugReportSeverity
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardsDecisionDialog
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardsOptionCard
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardsSingleActionDialog
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardsSingleSelectGroup
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.DraftChange
import com.rossomak.flashcards.feature.account.ReportBugDialog.DiscardReport
import com.rossomak.flashcards.feature.account.ReportBugDialog.Severity

@Composable
internal fun ReportBugDialogHost(
    activeDialog: ReportBugDialog?,
    onDialogEvent: (ReportBugDialogEvent) -> Unit,
) {
    when (activeDialog) {
        null -> Unit
        // Back and Keep editing both return to the form, so the decision dialog's single onCancel is Dismiss.
        DiscardReport -> FlashcardsDecisionDialog(
            title = stringResource(R.string.report_bug_discard_title),
            supportingText = stringResource(R.string.report_bug_discard_message),
            confirmLabel = stringResource(R.string.report_bug_discard_button),
            onConfirm = { onDialogEvent(Confirm) },
            onCancel = { onDialogEvent(Dismiss) },
            cancelLabel = stringResource(R.string.report_bug_keep_editing_button),
        )
        is Severity -> SeverityDialog(
            dialog = activeDialog,
            onDialogEvent = onDialogEvent,
        )
    }
}

/** Deferred commit: a card only changes the draft, and Confirm, which needs a pick, applies it. */
@Composable
private fun SeverityDialog(
    dialog: Severity,
    onDialogEvent: (ReportBugDialogEvent) -> Unit,
) {
    FlashcardsSingleActionDialog(
        title = stringResource(R.string.report_bug_severity_title),
        onConfirm = { onDialogEvent(Confirm) },
        onDismiss = { onDialogEvent(Dismiss) },
        confirmEnabled = dialog.draftState != null,
    ) {
        FlashcardsSingleSelectGroup {
            BugReportSeverity.entries.forEach { severity ->
                val option = severity.option()
                FlashcardsOptionCard(
                    icon = option.icon,
                    title = stringResource(option.title),
                    description = stringResource(option.description),
                    selected = severity == dialog.draftState,
                    onSelect = { onDialogEvent(DraftChange(dialog.copy(draftState = severity))) },
                )
            }
        }
    }
}
