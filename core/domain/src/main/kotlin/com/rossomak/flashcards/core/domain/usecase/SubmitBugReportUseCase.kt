package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.BugReport
import com.rossomak.flashcards.core.domain.model.BugReportFailureReason.InvalidDescription
import com.rossomak.flashcards.core.domain.model.BugReportSeverity
import com.rossomak.flashcards.core.domain.model.BugReportSubmissionResult
import com.rossomak.flashcards.core.domain.model.InstallationInfo
import com.rossomak.flashcards.core.domain.repository.BugReportRepository
import com.rossomak.flashcards.core.domain.repository.InstallationInfoRepository
import com.rossomak.flashcards.core.domain.usecase.base.UseCase
import javax.inject.Inject

/** Fails with [InvalidDescription] before anything is read or sent. */
class SubmitBugReportUseCase @Inject constructor(
    private val bugReportRepository: BugReportRepository,
    private val installationInfoRepository: InstallationInfoRepository,
) : UseCase<SubmitBugReportUseCase.Params, BugReportSubmissionResult> {

    data class Params(
        val description: String,
        val severity: BugReportSeverity,
    )

    override suspend fun invoke(params: Params): BugReportSubmissionResult = with(params) {
        val trimmedDescription = description.trim()
        if (BugReport.descriptionLength(trimmedDescription) !in BugReport.MIN_DESCRIPTION_LENGTH..BugReport.MAX_DESCRIPTION_LENGTH) {
            return BugReportSubmissionResult.Failed(InvalidDescription)
        }
        val installationInfo = InstallationInfo(
            appVersion = installationInfoRepository.getAppVersion(),
            deviceInfo = installationInfoRepository.getDeviceInfo(),
        )
        bugReportRepository.submitBugReport(BugReport(trimmedDescription, severity, installationInfo))
    }
}
