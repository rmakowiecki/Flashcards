package com.rossomak.flashcards.feature.account

import com.rossomak.flashcards.core.domain.model.BugReportSeverity
import com.rossomak.flashcards.core.ui.dialog.DialogEvent

typealias ReportBugDialogEvent = DialogEvent<ReportBugDialog>

sealed interface ReportBugDialog {

    data object DiscardReport : ReportBugDialog

    /** [draftState] is the ticked card, `null` until the first pick. */
    data class Severity(val draftState: BugReportSeverity?) : ReportBugDialog
}
