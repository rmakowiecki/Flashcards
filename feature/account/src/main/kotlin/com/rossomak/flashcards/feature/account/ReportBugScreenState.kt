package com.rossomak.flashcards.feature.account

import com.rossomak.flashcards.core.domain.model.BugReport
import com.rossomak.flashcards.core.domain.model.BugReportSeverity

data class ReportBugScreenState(
    val draftText: String = "",
    val severity: BugReportSeverity? = null,
    val isSending: Boolean = false,
    val isSent: Boolean = false,
    val activeDialog: ReportBugDialog? = null,
) {
    /** True while a send is in flight or done, so the inputs stop reacting. */
    val isLocked: Boolean
        get() = isSending || isSent

    /** Also the guard on the send path, so the disabled Send button is not the only barrier. */
    val canSend: Boolean
        get() = severity != null && !isLocked &&
            BugReport.descriptionLength(draftText) in BugReport.MIN_DESCRIPTION_LENGTH..BugReport.MAX_DESCRIPTION_LENGTH
}
