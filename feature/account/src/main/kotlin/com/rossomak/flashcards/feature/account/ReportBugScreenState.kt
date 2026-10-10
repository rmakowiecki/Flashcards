package com.rossomak.flashcards.feature.account

import com.rossomak.flashcards.core.domain.model.BugReport
import com.rossomak.flashcards.core.domain.model.BugReportSeverity
import com.rossomak.flashcards.feature.account.ReportBugSubmissionStatus.Idle

data class ReportBugScreenState(
    val draftText: String = "",
    val severity: BugReportSeverity? = null,
    val submissionStatus: ReportBugSubmissionStatus = Idle,
    val activeDialog: ReportBugDialog? = null,
) {
    val isLocked: Boolean
        get() = submissionStatus != Idle

    /** Also guards the send path, not just the Send button. */
    val canSend: Boolean
        get() = severity != null &&
            !isLocked &&
            BugReport.descriptionLength(draftText) in BugReport.MIN_DESCRIPTION_LENGTH..BugReport.MAX_DESCRIPTION_LENGTH
}
