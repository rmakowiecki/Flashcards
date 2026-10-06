package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.InstallationInfo
import com.rossomak.flashcards.core.domain.repository.InstallationInfoRepository
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject

class GetInstallationInfoUseCase @Inject constructor(
    private val installationInfoRepository: InstallationInfoRepository,
) : NoParamUseCase<InstallationInfo> {

    override suspend operator fun invoke(): InstallationInfo = InstallationInfo(
        appVersion = installationInfoRepository.getAppVersion(),
        deviceInfo = installationInfoRepository.getDeviceInfo(),
    )
}
