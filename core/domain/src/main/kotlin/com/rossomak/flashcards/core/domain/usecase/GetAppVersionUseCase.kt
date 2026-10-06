package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.AppVersion
import com.rossomak.flashcards.core.domain.repository.InstallationInfoRepository
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject

class GetAppVersionUseCase @Inject constructor(
    private val installationInfoRepository: InstallationInfoRepository,
) : NoParamUseCase<AppVersion> {

    override suspend operator fun invoke(): AppVersion = installationInfoRepository.getAppVersion()
}
