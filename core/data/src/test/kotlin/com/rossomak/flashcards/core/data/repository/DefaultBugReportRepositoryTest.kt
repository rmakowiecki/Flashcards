package com.rossomak.flashcards.core.data.repository

import com.google.firebase.functions.FirebaseFunctionsException
import com.rossomak.flashcards.core.data.source.BugReportRemoteDataSource
import com.rossomak.flashcards.core.domain.model.AppVersion
import com.rossomak.flashcards.core.domain.model.BugReport
import com.rossomak.flashcards.core.domain.model.BugReportFailureReason.NoConnection
import com.rossomak.flashcards.core.domain.model.BugReportFailureReason.ServiceError
import com.rossomak.flashcards.core.domain.model.BugReportSeverity
import com.rossomak.flashcards.core.domain.model.BugReportSubmissionResult
import com.rossomak.flashcards.core.domain.model.DeviceInfo
import com.rossomak.flashcards.core.domain.model.InstallationInfo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultBugReportRepositoryTest {

    private val bugReportRemoteDataSource: BugReportRemoteDataSource = mockk()
    private val repository = DefaultBugReportRepository(bugReportRemoteDataSource)

    private val report = BugReport(
        description = "The timer freezes after a rotation.",
        severity = BugReportSeverity.Minor,
        installationInfo = InstallationInfo(
            appVersion = AppVersion(name = "1.4.142", code = 142L),
            deviceInfo = DeviceInfo(model = "Google Pixel 8", systemVersion = 35),
        ),
    )

    // A strict mock: the classifier walks `cause`, so both properties must be stubbed.
    private fun functionsException(code: FirebaseFunctionsException.Code, cause: Throwable? = null): FirebaseFunctionsException {
        val exception: FirebaseFunctionsException = mockk()
        every { exception.code } returns code
        every { exception.cause } returns cause
        return exception
    }

    private fun stubFailure(exception: Throwable) {
        coEvery { bugReportRemoteDataSource.submitBugReport(report) } returns Result.failure(exception)
    }

    @Test
    fun `a successful send is Sent`() = runTest {
        coEvery { bugReportRemoteDataSource.submitBugReport(report) } returns Result.success(Unit)

        repository.submitBugReport(report) shouldBe BugReportSubmissionResult.Sent

        coVerify(exactly = 1) { bugReportRemoteDataSource.submitBugReport(report) }
    }

    @Test
    fun `a bare IOException is NoConnection`() = runTest {
        stubFailure(IOException("connection reset"))

        repository.submitBugReport(report) shouldBe BugReportSubmissionResult.Failed(NoConnection)

        coVerify(exactly = 1) { bugReportRemoteDataSource.submitBugReport(report) }
    }

    @Test
    fun `INTERNAL caused by an UnknownHostException is NoConnection`() = runTest {
        stubFailure(functionsException(FirebaseFunctionsException.Code.INTERNAL, UnknownHostException("no network")))

        repository.submitBugReport(report) shouldBe BugReportSubmissionResult.Failed(NoConnection)

        coVerify(exactly = 1) { bugReportRemoteDataSource.submitBugReport(report) }
    }

    @Test
    fun `a server INTERNAL without an IOException in its chain is ServiceError`() = runTest {
        stubFailure(functionsException(FirebaseFunctionsException.Code.INTERNAL))

        repository.submitBugReport(report) shouldBe BugReportSubmissionResult.Failed(ServiceError)

        coVerify(exactly = 1) { bugReportRemoteDataSource.submitBugReport(report) }
    }

    @Test
    fun `a rate limit rejection is ServiceError`() = runTest {
        stubFailure(functionsException(FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED))

        repository.submitBugReport(report) shouldBe BugReportSubmissionResult.Failed(ServiceError)

        coVerify(exactly = 1) { bugReportRemoteDataSource.submitBugReport(report) }
    }

    @Test
    fun `a non-Functions exception is ServiceError`() = runTest {
        stubFailure(IllegalStateException("unexpected"))

        repository.submitBugReport(report) shouldBe BugReportSubmissionResult.Failed(ServiceError)

        coVerify(exactly = 1) { bugReportRemoteDataSource.submitBugReport(report) }
    }

    @Test
    fun `a send that never completes is NoConnection once the time budget passes`() = runTest {
        coEvery { bugReportRemoteDataSource.submitBugReport(report) } coAnswers { awaitCancellation() }

        repository.submitBugReport(report) shouldBe BugReportSubmissionResult.Failed(NoConnection)

        currentTime shouldBe DefaultBugReportRepository.BUG_REPORT_TIME_BUDGET.inWholeMilliseconds
        coVerify(exactly = 1) { bugReportRemoteDataSource.submitBugReport(report) }
    }

    @Test
    fun `cancellation is not mapped to a failure`() = runTest {
        coEvery { bugReportRemoteDataSource.submitBugReport(report) } throws CancellationException("cancelled")

        shouldThrow<CancellationException> { repository.submitBugReport(report) }

        coVerify(exactly = 1) { bugReportRemoteDataSource.submitBugReport(report) }
    }
}
