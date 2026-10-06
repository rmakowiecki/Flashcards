package com.rossomak.flashcards.core.data.source

import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import com.rossomak.flashcards.core.domain.model.AppVersion
import com.rossomak.flashcards.core.domain.model.BugReport
import com.rossomak.flashcards.core.domain.model.BugReportSeverity
import com.rossomak.flashcards.core.domain.model.DeviceInfo
import com.rossomak.flashcards.core.domain.model.InstallationInfo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FirebaseBugReportRemoteDataSourceTest {

    private val functions: FirebaseFunctions = mockk()
    private val callableReference: HttpsCallableReference = mockk()

    private fun createDataSource(): FirebaseBugReportRemoteDataSource = FirebaseBugReportRemoteDataSource(functions)

    private fun stubCallable(): CapturingSlot<Any> {
        val payloadSlot = slot<Any>()
        every { functions.getHttpsCallable(SUBMIT_BUG_REPORT_FUNCTION_NAME) } returns callableReference
        every { callableReference.call(capture(payloadSlot)) } returns Tasks.forResult(mockk<HttpsCallableResult>())
        return payloadSlot
    }

    private fun report(
        severity: BugReportSeverity = BugReportSeverity.Minor,
        appVersionName: String = APP_VERSION_NAME,
        deviceModel: String = DEVICE_MODEL,
    ) = BugReport(
        description = DESCRIPTION,
        severity = severity,
        installationInfo = InstallationInfo(
            appVersion = AppVersion(name = appVersionName, code = APP_VERSION_CODE),
            deviceInfo = DeviceInfo(model = deviceModel, systemVersion = ANDROID_VERSION),
        ),
    )

    @Suppress("UNCHECKED_CAST")
    private fun CapturingSlot<Any>.payload(): Map<String, Any> = captured as Map<String, Any>

    @Test
    fun `submitBugReport calls the submitBugReport callable and succeeds`() = runTest {
        stubCallable()

        val result = createDataSource().submitBugReport(report())

        result shouldBe Result.success(Unit)
        verify(exactly = 1) { functions.getHttpsCallable(SUBMIT_BUG_REPORT_FUNCTION_NAME) }
        verify(exactly = 1) { callableReference.call(any()) }
    }

    @Test
    fun `submitBugReport sends exactly the six contract keys with the right types`() = runTest {
        val payloadSlot = stubCallable()

        createDataSource().submitBugReport(report(severity = BugReportSeverity.Blocker))

        val payload = payloadSlot.payload()
        payload.keys shouldBe setOf("description", "severity", "appVersionName", "appVersionCode", "deviceModel", "androidVersion")
        payload["description"] shouldBe DESCRIPTION
        payload["severity"] shouldBe "blocker"
        payload["appVersionName"] shouldBe APP_VERSION_NAME
        payload["appVersionCode"] shouldBe APP_VERSION_CODE
        payload["deviceModel"] shouldBe DEVICE_MODEL
        payload["androidVersion"] shouldBe ANDROID_VERSION

        verify(exactly = 1) { callableReference.call(any()) }
    }

    @Test
    fun `submitBugReport maps each severity to its wire value`() = runTest {
        val payloadSlot = stubCallable()
        val wireValues = mapOf(
            BugReportSeverity.Blocker to "blocker",
            BugReportSeverity.Minor to "minor",
            BugReportSeverity.Cosmetic to "cosmetic",
        )

        BugReportSeverity.entries.forEach { severity ->
            createDataSource().submitBugReport(report(severity = severity))

            payloadSlot.payload()["severity"] shouldBe wireValues.getValue(severity)
        }
        wireValues.keys shouldBe BugReportSeverity.entries.toSet()

        verify(exactly = BugReportSeverity.entries.size) { callableReference.call(any()) }
    }

    @Test
    fun `submitBugReport clips an app version name over 50 characters and keeps one at the limit`() = runTest {
        val payloadSlot = stubCallable()

        createDataSource().submitBugReport(report(appVersionName = "v".repeat(MAX_APP_VERSION_NAME_LENGTH + 1)))
        payloadSlot.payload()["appVersionName"] shouldBe "v".repeat(MAX_APP_VERSION_NAME_LENGTH)

        createDataSource().submitBugReport(report(appVersionName = "v".repeat(MAX_APP_VERSION_NAME_LENGTH)))
        payloadSlot.payload()["appVersionName"] shouldBe "v".repeat(MAX_APP_VERSION_NAME_LENGTH)

        verify(exactly = 2) { callableReference.call(any()) }
    }

    @Test
    fun `submitBugReport clips a device model over 100 characters and keeps one at the limit`() = runTest {
        val payloadSlot = stubCallable()

        createDataSource().submitBugReport(report(deviceModel = "m".repeat(MAX_DEVICE_MODEL_LENGTH + 1)))
        payloadSlot.payload()["deviceModel"] shouldBe "m".repeat(MAX_DEVICE_MODEL_LENGTH)

        createDataSource().submitBugReport(report(deviceModel = "m".repeat(MAX_DEVICE_MODEL_LENGTH)))
        payloadSlot.payload()["deviceModel"] shouldBe "m".repeat(MAX_DEVICE_MODEL_LENGTH)

        verify(exactly = 2) { callableReference.call(any()) }
    }

    @Test
    fun `submitBugReport wraps a callable failure in a failure result`() = runTest {
        val error: FirebaseFunctionsException = mockk()
        every { functions.getHttpsCallable(SUBMIT_BUG_REPORT_FUNCTION_NAME) } returns callableReference
        every { callableReference.call(any()) } returns Tasks.forException(error)

        val result = createDataSource().submitBugReport(report())

        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe error
        verify(exactly = 1) { callableReference.call(any()) }
    }

    @Test
    fun `submitBugReport rethrows cancellation`() = runTest {
        every { functions.getHttpsCallable(SUBMIT_BUG_REPORT_FUNCTION_NAME) } returns callableReference
        every { callableReference.call(any()) } throws CancellationException("cancelled")

        shouldThrow<CancellationException> { createDataSource().submitBugReport(report()) }

        verify(exactly = 1) { callableReference.call(any()) }
    }

    private companion object {
        const val SUBMIT_BUG_REPORT_FUNCTION_NAME = "submitBugReport"
        const val DESCRIPTION = "The timer freezes after a rotation."
        const val APP_VERSION_NAME = "1.4.142-debug"
        const val APP_VERSION_CODE = 142L
        const val DEVICE_MODEL = "Google Pixel 8"
        const val ANDROID_VERSION = 35
        const val MAX_APP_VERSION_NAME_LENGTH = 50
        const val MAX_DEVICE_MODEL_LENGTH = 100
    }
}
