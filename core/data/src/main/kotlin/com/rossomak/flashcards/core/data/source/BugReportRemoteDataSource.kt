package com.rossomak.flashcards.core.data.source

import com.rossomak.flashcards.core.domain.model.BugReport

interface BugReportRemoteDataSource {

    suspend fun submitBugReport(report: BugReport): Result<Unit>
}
