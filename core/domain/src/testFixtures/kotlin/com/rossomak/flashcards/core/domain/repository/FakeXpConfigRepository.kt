package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.XpConfig

class FakeXpConfigRepository : XpConfigRepository {
    /** Overrides every [getXpConfig] call, success or failure alike. */
    var resultToReturn: Result<XpConfig> = Result.success(XpConfig())

    var getXpConfigCallCount: Int = 0
        private set

    var refreshCallCount: Int = 0
        private set

    override suspend fun getXpConfig(): Result<XpConfig> {
        getXpConfigCallCount++
        return resultToReturn
    }

    override suspend fun refreshXpConfig() {
        refreshCallCount++
    }
}
