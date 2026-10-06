package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.BugReport
import com.rossomak.flashcards.core.domain.model.BugReportSubmissionResult

interface BugReportRepository {

    /** Never throws except on cancellation. A failed send is not queued: the caller retries. */
    suspend fun submitBugReport(report: BugReport): BugReportSubmissionResult
}
