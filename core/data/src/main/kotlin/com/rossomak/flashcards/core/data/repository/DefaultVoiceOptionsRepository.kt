package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.data.source.VoiceOptionsDataSource
import com.rossomak.flashcards.core.domain.model.VoiceOption
import com.rossomak.flashcards.core.domain.repository.VoiceOptionsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide cache of the curated voice list: enumerating it starts a text-to-speech engine, so
 * it happens at most once per app run, shared by every screen. Concurrent callers queue behind the
 * one load in flight and read its result. A failed load throws to its caller and is not cached, so
 * a later call retries.
 */
@Singleton
class DefaultVoiceOptionsRepository @Inject constructor(
    private val dataSource: VoiceOptionsDataSource,
) : VoiceOptionsRepository {

    private val loadLock = Mutex()

    @Volatile
    private var cachedVoices: List<VoiceOption>? = null

    override suspend fun getAvailableVoices(): List<VoiceOption> = cachedVoices ?: loadLock.withLock {
        cachedVoices ?: dataSource.getAvailableVoices().also { cachedVoices = it }
    }
}
