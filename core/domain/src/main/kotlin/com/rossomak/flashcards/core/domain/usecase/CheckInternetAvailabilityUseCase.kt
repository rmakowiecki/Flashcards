package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.repository.NetworkAvailabilityGateway
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject

class CheckInternetAvailabilityUseCase @Inject constructor(
    private val networkAvailabilityGateway: NetworkAvailabilityGateway,
) : NoParamUseCase<Boolean> {

    override suspend fun invoke(): Boolean = networkAvailabilityGateway.isInternetAvailable()
}
