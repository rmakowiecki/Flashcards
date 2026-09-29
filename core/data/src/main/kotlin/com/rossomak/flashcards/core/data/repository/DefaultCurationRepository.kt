package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.data.mapper.toDomain
import com.rossomak.flashcards.core.data.source.CurationRemoteDataSource
import com.rossomak.flashcards.core.data.source.CurationWriteResult
import com.rossomak.flashcards.core.domain.model.CurationAction
import com.rossomak.flashcards.core.domain.model.CurationRequest
import com.rossomak.flashcards.core.domain.repository.CurationRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [knownActions] is a process-lifetime, best-effort cache of what each card was last known to have
 * flagged. Only server-confirmed writes update it: a write left in Firestore's offline queue
 * ([CurationWriteResult.Queued]) may still be rejected later, so a resubmit must write again. It is filled lazily: a card's first [upsertCurationActions] call fetches its current
 * state from Firestore before deciding whether to write, then every write after that keeps the
 * cache in sync directly. This lazy fetch is presently the only caller of [getCurationRequests] —
 * that method itself does not touch the cache.
 */
class DefaultCurationRepository @Inject constructor(
    private val remoteDataSource: CurationRemoteDataSource,
) : CurationRepository {

    private val knownActions = ConcurrentHashMap<String, Set<CurationAction>>()

    override suspend fun getCurationRequests(cardIds: List<String>): Result<Map<String, CurationRequest>> = withContext(Dispatchers.IO) {
        try {
            Result.success(
                remoteDataSource.getCurationRequests(cardIds)
                    .mapValues { (cardId, dto) -> dto.toDomain(cardId) }
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun upsertCurationActions(
        cardId: String,
        subcategoryId: String,
        actions: Set<CurationAction>,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val known = knownActions[cardId] ?: fetchKnownActions(cardId).also { knownActions[cardId] = it }
            if (!known.containsAll(actions)) {
                val writeResult = remoteDataSource.upsertCurationActions(cardId, subcategoryId, actions)
                if (writeResult == CurationWriteResult.Confirmed) {
                    knownActions[cardId] = known + actions -
                        actions.mapNotNull { it.difficultyOpposite() }.filterNot { it in actions }.toSet()
                }
            }
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private suspend fun fetchKnownActions(cardId: String): Set<CurationAction> =
        remoteDataSource.getCurationRequests(listOf(cardId))[cardId]?.toDomain(cardId)?.actions?.keys.orEmpty()
}
