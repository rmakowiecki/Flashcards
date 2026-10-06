package com.rossomak.flashcards.core.domain.model

/** Not a `Result<Unit>`: [BugReportFailureReason] is a plain sealed type, not a `Throwable`. */
sealed interface BugReportSubmissionResult {

    data object Sent : BugReportSubmissionResult

    data class Failed(val reason: BugReportFailureReason) : BugReportSubmissionResult
}
