package com.rossomak.flashcards.core.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * The signed-in User's Premium, read from their entitlement record. The app only reads it: the backend
 * or a project admin changes it. A missing record means not Premium, and a Guest or a signed-out app is
 * never Premium, whatever the record says.
 */
interface PremiumRepository {

    /**
     * Premium for whoever is signed in, then every change to it, including a change of User. Null while
     * it is not known yet: before the server has answered once, or after the listener failed.
     */
    fun observePremium(): Flow<Boolean?>

    /** Premium as the server holds it right now, never a cached value. Fails when the server can't be reached. */
    suspend fun fetchPremium(): Result<Boolean>
}
