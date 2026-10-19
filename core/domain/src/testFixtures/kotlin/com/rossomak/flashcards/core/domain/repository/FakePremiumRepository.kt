package com.rossomak.flashcards.core.domain.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePremiumRepository : PremiumRepository {
    val premium = MutableStateFlow<Boolean?>(true)
    var fetchResult: Result<Boolean> = Result.success(true)
    var fetchCallCount: Int = 0
    var fetchGate: CompletableDeferred<Unit>? = null

    override fun observePremium(): Flow<Boolean?> = premium

    override suspend fun fetchPremium(): Result<Boolean> {
        fetchCallCount++
        fetchGate?.await()
        return fetchResult
    }
}
