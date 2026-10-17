package com.rossomak.flashcards.core.data.source

import kotlinx.coroutines.flow.Flow

/** The User's entitlement record, `users/{uid}/entitlement/premium`. The app only reads it. */
interface PremiumRemoteDataSource {

    /** Premium as the server confirms it, then every change. A missing record reads as not Premium. */
    fun observePremium(uid: String): Flow<Boolean>

    /** Premium read from the server, skipping the cache. Throws when the server can't be reached. */
    suspend fun fetchPremium(uid: String): Boolean
}
