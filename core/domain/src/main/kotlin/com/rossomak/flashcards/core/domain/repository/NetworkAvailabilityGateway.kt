package com.rossomak.flashcards.core.domain.repository

/** A one-off answer to "is there a network that can reach the internet right now?". */
fun interface NetworkAvailabilityGateway {
    fun isInternetAvailable(): Boolean
}
