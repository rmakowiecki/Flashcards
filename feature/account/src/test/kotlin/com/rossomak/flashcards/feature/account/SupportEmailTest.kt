package com.rossomak.flashcards.feature.account

import android.content.Context
import com.rossomak.flashcards.core.domain.model.AppVersion
import com.rossomak.flashcards.core.domain.model.DeviceInfo
import com.rossomak.flashcards.core.domain.model.InstallationInfo
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class SupportEmailTest {

    private val context: Context = mockk {
        every { getString(R.string.account_support_email_details_label) } returns DETAILS_LINE
        every {
            getString(R.string.account_support_email_app_version_label, APP_VERSION_NAME, APP_VERSION_CODE)
        } returns APP_VERSION_LINE
        every { getString(R.string.account_support_email_device_label, DEVICE_MODEL) } returns DEVICE_LINE
        every {
            getString(R.string.account_support_email_android_version_label, SYSTEM_VERSION)
        } returns ANDROID_LINE
        every { getString(R.string.account_support_email_account_id_label, UID) } returns ACCOUNT_LINE
    }

    @Test
    fun `the details come first and two empty lines follow`() {
        val body = supportEmailBody(context, INSTALLATION_INFO, UID)

        body shouldBe listOf(DETAILS_LINE, APP_VERSION_LINE, DEVICE_LINE, ANDROID_LINE, ACCOUNT_LINE, "", "")
            .joinToString("\n")
    }

    @Test
    fun `a signed out user has no account line`() {
        val body = supportEmailBody(context, INSTALLATION_INFO, uid = null)

        body shouldBe listOf(DETAILS_LINE, APP_VERSION_LINE, DEVICE_LINE, ANDROID_LINE, "", "").joinToString("\n")
    }

    private companion object {
        const val APP_VERSION_NAME = "1.4.0"
        const val APP_VERSION_CODE = 142L
        const val DEVICE_MODEL = "Google Pixel 8"
        const val SYSTEM_VERSION = 35
        const val UID = "uid-1"
        const val DETAILS_LINE = "--- Support details ---"
        const val APP_VERSION_LINE = "App version: 1.4.0 (142)"
        const val DEVICE_LINE = "Device: Google Pixel 8"
        const val ANDROID_LINE = "Android: API 35"
        const val ACCOUNT_LINE = "Account ID: uid-1"
        val INSTALLATION_INFO = InstallationInfo(
            appVersion = AppVersion(name = APP_VERSION_NAME, code = APP_VERSION_CODE),
            deviceInfo = DeviceInfo(model = DEVICE_MODEL, systemVersion = SYSTEM_VERSION),
        )
    }
}
