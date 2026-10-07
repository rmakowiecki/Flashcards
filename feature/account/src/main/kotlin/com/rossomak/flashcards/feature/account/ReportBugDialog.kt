package com.rossomak.flashcards.feature.account

import com.rossomak.flashcards.core.domain.model.BugReportSeverity
import com.rossomak.flashcards.core.ui.dialog.DialogEvent

typealias ReportBugDialogEvent = DialogEvent<ReportBugDialog>

sealed interface ReportBugDialog {

    data object DiscardReport : ReportBugDialog

    /** `draftState` is the card ticked in the dialog; `null` until the first pick when no severity was chosen yet. */
    data class Severity(val draftState: BugReportSeverity?) : ReportBugDialog
}
