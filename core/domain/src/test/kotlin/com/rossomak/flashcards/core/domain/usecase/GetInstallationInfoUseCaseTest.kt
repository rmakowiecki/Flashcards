package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.AppVersion
import com.rossomak.flashcards.core.domain.model.DeviceInfo
import com.rossomak.flashcards.core.domain.model.InstallationInfo
import com.rossomak.flashcards.core.domain.repository.InstallationInfoRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetInstallationInfoUseCaseTest {

    private val installationInfoRepository: InstallationInfoRepository = mockk()
    private val useCase = GetInstallationInfoUseCase(installationInfoRepository)

    @Test
    fun `combines the repository's app version and device info`() = runTest {
        val appVersion = AppVersion(name = "1.4.142", code = 142L)
        val deviceInfo = DeviceInfo(model = "Google Pixel 8", systemVersion = 35)
        coEvery { installationInfoRepository.getAppVersion() } returns appVersion
        coEvery { installationInfoRepository.getDeviceInfo() } returns deviceInfo

        useCase() shouldBe InstallationInfo(appVersion = appVersion, deviceInfo = deviceInfo)

        coVerify(exactly = 1) { installationInfoRepository.getAppVersion() }
        coVerify(exactly = 1) { installationInfoRepository.getDeviceInfo() }
    }
}
