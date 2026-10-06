package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.data.source.BugReportRemoteDataSource
import com.rossomak.flashcards.core.domain.model.BugReport
import com.rossomak.flashcards.core.domain.model.BugReportFailureReason
import com.rossomak.flashcards.core.domain.model.BugReportSubmissionResult
import com.rossomak.flashcards.core.domain.repository.BugReportRepository
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull

/**
 * No connectivity precheck: a callable fails fast offline. A request the server writes but whose
 * answer is lost, or that outlives [BUG_REPORT_TIME_BUDGET] (cancelling does not stop the SDK call),
 * can be retried into a duplicate report. The contract has no idempotency key and the server's
 * rate limit caps it, so it is accepted.
 */
class DefaultBugReportRepository @Inject constructor(
    private val bugReportRemoteDataSource: BugReportRemoteDataSource,
) : BugReportRepository {

    override suspend fun submitBugReport(report: BugReport): BugReportSubmissionResult {
        val result = withTimeoutOrNull(BUG_REPORT_TIME_BUDGET) {
            bugReportRemoteDataSource.submitBugReport(report)
        }
        if (result == null) {
            loge { "Bug report submission exceeded $BUG_REPORT_TIME_BUDGET" }
            return BugReportSubmissionResult.Failed(BugReportFailureReason.NoConnection)
        }
        return result.fold(
            onSuccess = { BugReportSubmissionResult.Sent },
            onFailure = { exception ->
                loge(exception) { "Bug report submission failed" }
                BugReportSubmissionResult.Failed(exception.toBugReportFailureReason())
            },
        )
    }

    companion object {
        /** A send still running then fails as [BugReportFailureReason.NoConnection]. The SDK's own default is 70 s. */
        val BUG_REPORT_TIME_BUDGET: Duration = 20.seconds
    }
}
