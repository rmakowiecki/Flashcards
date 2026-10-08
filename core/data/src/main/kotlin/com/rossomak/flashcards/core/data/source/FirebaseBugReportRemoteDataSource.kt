package com.rossomak.flashcards.core.data.source

import com.google.firebase.functions.FirebaseFunctions
import com.rossomak.flashcards.core.domain.model.BugReport
import com.rossomak.flashcards.core.domain.model.BugReportSeverity
import com.rossomak.flashcards.core.domain.model.BugReportSeverity.Blocker
import com.rossomak.flashcards.core.domain.model.BugReportSeverity.Cosmetic
import com.rossomak.flashcards.core.domain.model.BugReportSeverity.Minor
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Clips the app version name and device model to the server's limits instead of failing the send. */
class FirebaseBugReportRemoteDataSource @Inject constructor(
    private val functions: FirebaseFunctions,
) : BugReportRemoteDataSource {

    // A callable Task can fail with more than FirebaseFunctionsException.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun submitBugReport(report: BugReport): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            functions.getHttpsCallable(SUBMIT_BUG_REPORT_FUNCTION_NAME).call(report.toPayload()).await()
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun BugReport.toPayload(): Map<String, Any> = mapOf(
        FIELD_DESCRIPTION to description,
        FIELD_SEVERITY to severity.toWireValue(),
        FIELD_APP_VERSION_NAME to installationInfo.appVersion.name.take(MAX_APP_VERSION_NAME_LENGTH),
        FIELD_APP_VERSION_CODE to installationInfo.appVersion.code,
        FIELD_DEVICE_MODEL to installationInfo.deviceInfo.model.take(MAX_DEVICE_MODEL_LENGTH),
        FIELD_ANDROID_VERSION to installationInfo.deviceInfo.systemVersion,
    )

    // Explicit so renaming an entry cannot change the contract.
    private fun BugReportSeverity.toWireValue(): String = when (this) {
        Blocker -> SEVERITY_BLOCKER
        Minor -> SEVERITY_MINOR
        Cosmetic -> SEVERITY_COSMETIC
    }

    private companion object {
        const val SUBMIT_BUG_REPORT_FUNCTION_NAME = "submitBugReport"

        // Mirrors functions/src/lib/submitBugReport.ts.
        const val FIELD_DESCRIPTION = "description"
        const val FIELD_SEVERITY = "severity"
        const val FIELD_APP_VERSION_NAME = "appVersionName"
        const val FIELD_APP_VERSION_CODE = "appVersionCode"
        const val FIELD_DEVICE_MODEL = "deviceModel"
        const val FIELD_ANDROID_VERSION = "androidVersion"
        const val SEVERITY_BLOCKER = "blocker"
        const val SEVERITY_MINOR = "minor"
        const val SEVERITY_COSMETIC = "cosmetic"
        const val MAX_APP_VERSION_NAME_LENGTH = 50
        const val MAX_DEVICE_MODEL_LENGTH = 100
    }
}
