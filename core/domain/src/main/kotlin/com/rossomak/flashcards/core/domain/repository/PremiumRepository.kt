package com.rossomak.flashcards.core.domain.repository

import kotlinx.coroutines.flow.Flow

/** The signed-in User's Premium, read-only. A missing record, a Guest or no User all mean not Premium. */
interface PremiumRepository {

    /** Follows the signed-in User's Premium. Null while unknown: before the server first answers, or after the listener fails. */
    fun observePremium(): Flow<Boolean?>

    /** Reads Premium from the server, never the cache. Fails only when the server is unreachable; a refused read means not Premium. */
    suspend fun fetchPremium(): Result<Boolean>
}
