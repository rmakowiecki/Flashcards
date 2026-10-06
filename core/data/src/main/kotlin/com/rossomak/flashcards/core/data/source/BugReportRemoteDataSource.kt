package com.rossomak.flashcards.core.data.source

import com.rossomak.flashcards.core.domain.model.BugReport

interface BugReportRemoteDataSource {

    /** Sets no timeout of its own. */
    suspend fun submitBugReport(report: BugReport): Result<Unit>
}
