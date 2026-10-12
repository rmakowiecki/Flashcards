package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.AppVersion
import com.rossomak.flashcards.core.domain.repository.InstallationInfoRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetAppVersionUseCaseTest {

    private val installationInfoRepository: InstallationInfoRepository = mockk()
    private val useCase = GetAppVersionUseCase(installationInfoRepository)

    @Test
    fun `returns the repository's app version`() = runTest {
        val appVersion = AppVersion(name = "1.4.142", code = 142L)
        coEvery { installationInfoRepository.getAppVersion() } returns appVersion

        useCase() shouldBe appVersion

        coVerify(exactly = 1) { installationInfoRepository.getAppVersion() }
    }
}
