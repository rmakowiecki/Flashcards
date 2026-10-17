package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.repository.NetworkAvailabilityGateway
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveInternetAvailabilityUseCase @Inject constructor(
    private val networkAvailabilityGateway: NetworkAvailabilityGateway,
) : NoParamUseCase<Flow<Boolean>> {

    override suspend fun invoke(): Flow<Boolean> = networkAvailabilityGateway.observeInternetAvailability()
}
