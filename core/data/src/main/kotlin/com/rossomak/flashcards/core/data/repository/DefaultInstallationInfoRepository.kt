package com.rossomak.flashcards.core.data.repository

import android.os.Build
import com.rossomak.flashcards.core.domain.model.AppVersion
import com.rossomak.flashcards.core.domain.model.DeviceInfo
import com.rossomak.flashcards.core.domain.repository.InstallationInfoRepository
import javax.inject.Inject

/**
 * The app version comes in as a binding from the host app, the only module that can read its own
 * build configuration; the device facts are read from the platform.
 */
class DefaultInstallationInfoRepository @Inject constructor(
    private val appVersion: AppVersion,
) : InstallationInfoRepository {

    override suspend fun getAppVersion(): AppVersion = appVersion

    override suspend fun getDeviceInfo(): DeviceInfo = DeviceInfo(
        model = deviceModelLabel(manufacturer = Build.MANUFACTURER, model = Build.MODEL),
        systemVersion = Build.VERSION.SDK_INT,
    )
}
