package com.rossomak.flashcards.core.domain.repository

import kotlinx.coroutines.flow.Flow

/** Whether there is a network that can reach the internet. */
interface NetworkAvailabilityGateway {

    fun isInternetAvailable(): Boolean

    /** Emits the current answer first, then every change. */
    fun observeInternetAvailability(): Flow<Boolean>
}
