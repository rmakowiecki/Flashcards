package com.rossomak.flashcards.core.domain.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePremiumRepository : PremiumRepository {
    val premium = MutableStateFlow<Boolean?>(true)
    var getPremiumResult: Result<Boolean> = Result.success(true)
    var getPremiumCallCount: Int = 0
    var getPremiumGate: CompletableDeferred<Unit>? = null

    override fun observePremium(): Flow<Boolean?> = premium

    override suspend fun getPremium(): Result<Boolean> {
        getPremiumCallCount++
        getPremiumGate?.await()
        return getPremiumResult
    }
}
