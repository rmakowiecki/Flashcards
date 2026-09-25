package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.common.logd
import com.rossomak.flashcards.core.data.source.VoiceOptionsDataSource
import com.rossomak.flashcards.core.domain.model.VoiceOption
import com.rossomak.flashcards.core.domain.repository.VoiceOptionsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Loads the voice list at most once per app run. A failed load is not cached; the next call retries. */
@Singleton
class DefaultVoiceOptionsRepository @Inject constructor(
    private val dataSource: VoiceOptionsDataSource,
) : VoiceOptionsRepository {

    private val loadLock = Mutex()

    @Volatile
    private var cachedVoices: List<VoiceOption>? = null

    override suspend fun getAvailableVoices(): List<VoiceOption> = cachedVoices ?: loadLock.withLock {
        cachedVoices ?: dataSource.getAvailableVoices().also { voices ->
            cachedVoices = voices
            logd { "Voice list loaded: ${voices.size} voices" }
        }
    }
}
