package com.rossomak.flashcards.core.data.source

import kotlinx.coroutines.flow.Flow

/** Read-only access to `users/{uid}/entitlement/premium`. */
interface PremiumRemoteDataSource {

    /** A missing record reads as not Premium. */
    fun observePremium(uid: String): Flow<Boolean>

    /** Skips the cache. Throws when the server is unreachable. */
    suspend fun getPremium(uid: String): Boolean
}
