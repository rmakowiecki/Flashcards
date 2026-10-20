package com.rossomak.flashcards.core.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeNetworkAvailabilityGateway(isInternetAvailable: Boolean = true) : NetworkAvailabilityGateway {
    val internetAvailable = MutableStateFlow(isInternetAvailable)

    override fun isInternetAvailable(): Boolean = internetAvailable.value

    override fun observeInternetAvailability(): Flow<Boolean> = internetAvailable
}
