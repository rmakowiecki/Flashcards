package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.AppVersion
import com.rossomak.flashcards.core.domain.model.BugReport
import com.rossomak.flashcards.core.domain.model.BugReportFailureReason.InvalidDescription
import com.rossomak.flashcards.core.domain.model.BugReportFailureReason.NoConnection
import com.rossomak.flashcards.core.domain.model.BugReportFailureReason.ServiceError
import com.rossomak.flashcards.core.domain.model.BugReportSeverity
import com.rossomak.flashcards.core.domain.model.BugReportSubmissionResult
import com.rossomak.flashcards.core.domain.model.DeviceInfo
import com.rossomak.flashcards.core.domain.model.InstallationInfo
import com.rossomak.flashcards.core.domain.repository.BugReportRepository
import com.rossomak.flashcards.core.domain.repository.InstallationInfoRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SubmitBugReportUseCaseTest {

    private val bugReportRepository: BugReportRepository = mockk()
    private val installationInfoRepository: InstallationInfoRepository = mockk()
    private val useCase = SubmitBugReportUseCase(bugReportRepository, installationInfoRepository)

    private val appVersion = AppVersion(name = "1.4.142", code = 142L)
    private val deviceInfo = DeviceInfo(model = "Google Pixel 8", systemVersion = 35)
    private val validDescription = "The timer freezes after a rotation."

    private fun stubInstallationInfo() {
        coEvery { installationInfoRepository.getAppVersion() } returns appVersion
        coEvery { installationInfoRepository.getDeviceInfo() } returns deviceInfo
    }

    private fun params(description: String, severity: BugReportSeverity = BugReportSeverity.Minor) =
        SubmitBugReportUseCase.Params(description, severity)

    private fun assertRejectedWithoutAnyCall(description: String) = runTest {
        useCase(params(description)) shouldBe BugReportSubmissionResult.Failed(InvalidDescription)

        coVerify(exactly = 0) { installationInfoRepository.getAppVersion() }
        coVerify(exactly = 0) { installationInfoRepository.getDeviceInfo() }
        coVerify(exactly = 0) { bugReportRepository.submitBugReport(any()) }
    }

    @Test
    fun `a description shorter than the minimum after trimming fails as invalid without any call`() =
        assertRejectedWithoutAnyCall("a".repeat(BugReport.MIN_DESCRIPTION_LENGTH - 1))

    @Test
    fun `a description longer than the maximum fails as invalid without any call`() =
        assertRejectedWithoutAnyCall("a".repeat(BugReport.MAX_DESCRIPTION_LENGTH + 1))

    @Test
    fun `whitespace padding does not count towards the minimum`() =
        assertRejectedWithoutAnyCall("  " + "a".repeat(BugReport.MIN_DESCRIPTION_LENGTH - 1) + "\n\n")

    @Test
    fun `a blank description fails as invalid without any call`() = assertRejectedWithoutAnyCall("   \n ")

    @Test
    fun `whitespace padding does not count towards the maximum`() = runTest {
        stubInstallationInfo()
        coEvery { bugReportRepository.submitBugReport(any()) } returns BugReportSubmissionResult.Sent

        useCase(params("  " + "a".repeat(BugReport.MAX_DESCRIPTION_LENGTH) + "\n")) shouldBe BugReportSubmissionResult.Sent

        coVerify(exactly = 1) { bugReportRepository.submitBugReport(any()) }
    }

    @Test
    fun `a description of exactly the minimum length is accepted`() = runTest {
        stubInstallationInfo()
        coEvery { bugReportRepository.submitBugReport(any()) } returns BugReportSubmissionResult.Sent

        useCase(params("a".repeat(BugReport.MIN_DESCRIPTION_LENGTH))) shouldBe BugReportSubmissionResult.Sent

        coVerify(exactly = 1) { bugReportRepository.submitBugReport(any()) }
    }

    @Test
    fun `a description of exactly the maximum length is accepted`() = runTest {
        stubInstallationInfo()
        coEvery { bugReportRepository.submitBugReport(any()) } returns BugReportSubmissionResult.Sent

        useCase(params("a".repeat(BugReport.MAX_DESCRIPTION_LENGTH))) shouldBe BugReportSubmissionResult.Sent

        coVerify(exactly = 1) { bugReportRepository.submitBugReport(any()) }
    }

    @Test
    fun `passes the trimmed text, the severity and both installation reads to the repository`() = runTest {
        stubInstallationInfo()
        coEvery { bugReportRepository.submitBugReport(any()) } returns BugReportSubmissionResult.Sent

        useCase(params("  $validDescription \n", BugReportSeverity.Blocker))

        coVerify(exactly = 1) { installationInfoRepository.getAppVersion() }
        coVerify(exactly = 1) { installationInfoRepository.getDeviceInfo() }
        coVerify(exactly = 1) {
            bugReportRepository.submitBugReport(
                BugReport(
                    description = validDescription,
                    severity = BugReportSeverity.Blocker,
                    installationInfo = InstallationInfo(appVersion, deviceInfo),
                ),
            )
        }
    }

    @Test
    fun `returns the repository's NoConnection failure unchanged`() = runTest {
        stubInstallationInfo()
        coEvery { bugReportRepository.submitBugReport(any()) } returns BugReportSubmissionResult.Failed(NoConnection)

        useCase(params(validDescription)) shouldBe BugReportSubmissionResult.Failed(NoConnection)

        coVerify(exactly = 1) { bugReportRepository.submitBugReport(any()) }
    }

    @Test
    fun `returns the repository's ServiceError failure unchanged`() = runTest {
        stubInstallationInfo()
        coEvery { bugReportRepository.submitBugReport(any()) } returns BugReportSubmissionResult.Failed(ServiceError)

        useCase(params(validDescription)) shouldBe BugReportSubmissionResult.Failed(ServiceError)

        coVerify(exactly = 1) { bugReportRepository.submitBugReport(any()) }
    }
}
