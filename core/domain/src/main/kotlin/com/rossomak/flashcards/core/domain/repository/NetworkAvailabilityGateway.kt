package com.rossomak.flashcards.core.domain.repository

import kotlinx.coroutines.flow.Flow

/** Whether there is a network that can reach the internet. */
interface NetworkAvailabilityGateway {

    /** A one-off answer for right now. */
    fun isInternetAvailable(): Boolean

    /** The current answer, then every change to it. */
    fun observeInternetAvailability(): Flow<Boolean>
}
