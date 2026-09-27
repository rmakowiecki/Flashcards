package com.rossomak.flashcards.core.data.source

import com.rossomak.flashcards.core.domain.model.XpConfig

/** In-memory [XpConfigLocalDataSource] for tests. */
class FakeXpConfigLocalDataSource : XpConfigLocalDataSource {

    var savedConfig: XpConfig? = null

    override suspend fun getXpConfig(): XpConfig? = savedConfig

    override suspend fun save(config: XpConfig) {
        savedConfig = config
    }
}
