package com.rossomak.flashcards.core.domain.model

/** What identifies this installation to support: the app build and the device it runs on. */
data class InstallationInfo(
    val appVersion: AppVersion,
    val deviceInfo: DeviceInfo,
)
