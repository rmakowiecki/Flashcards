package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.common.logd
import com.rossomak.flashcards.core.common.logw
import com.rossomak.flashcards.core.data.mapper.toDomain
import com.rossomak.flashcards.core.data.source.XpConfigLocalDataSource
import com.rossomak.flashcards.core.data.source.XpConfigRemoteDataSource
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.repository.XpConfigRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Serves the last server configuration fetched by [refreshXpConfig], kept on the device, and falls
 * back to [XpConfig]'s bundled defaults only until a first fetch succeeds (ADR-0047). Reading never
 * touches the network, so a session starts offline with the rates last seen online.
 */
class DefaultXpConfigRepository @Inject constructor(
    private val remoteDataSource: XpConfigRemoteDataSource,
    private val localDataSource: XpConfigLocalDataSource,
) : XpConfigRepository {

    override suspend fun getXpConfig(): Result<XpConfig> = Result.success(localDataSource.getXpConfig() ?: XpConfig())

    // Broad on purpose: a refresh is best-effort, and any failure (network, a missing document, an
    // invalid field) must leave the kept copy in place rather than reach the caller.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun refreshXpConfig() = withContext(Dispatchers.IO) {
        try {
            val dto = remoteDataSource.getXpConfig()
            if (dto == null) {
                logw { "XP configuration document is missing; keeping the current configuration" }
            } else {
                localDataSource.save(dto.toDomain())
                logd { "XP configuration refreshed from the server" }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logw(exception) { "XP configuration refresh failed; keeping the current configuration" }
        }
    }
}
