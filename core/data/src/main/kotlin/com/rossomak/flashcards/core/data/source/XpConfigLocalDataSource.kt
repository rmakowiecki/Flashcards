package com.rossomak.flashcards.core.data.source

import com.rossomak.flashcards.core.domain.model.XpConfig

/** The last XP configuration fetched from the server, kept on the device. */
interface XpConfigLocalDataSource {

    /** `null` when no configuration has been saved yet. */
    suspend fun getXpConfig(): XpConfig?

    suspend fun save(config: XpConfig)
}
