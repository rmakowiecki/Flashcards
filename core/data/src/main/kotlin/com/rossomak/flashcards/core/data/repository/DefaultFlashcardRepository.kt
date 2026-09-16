package com.rossomak.flashcards.core.data.repository

import android.util.Log
import com.rossomak.flashcards.core.data.mapper.toDomain
import com.rossomak.flashcards.core.data.model.FlashcardDto
import com.rossomak.flashcards.core.data.source.FlashcardReadSource
import com.rossomak.flashcards.core.data.source.FlashcardRemoteDataSource
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.repository.FlashcardRepository
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns flashcard caching so no use case above it has to (ADR-0038).
 *
 * Firestore's default read is server-*first*, not cache-first — the SDK suppresses the cached
 * snapshot whenever it believes it is online — so without an explicit policy every read of the same
 * Subcategory costs a round trip, and browse, session preview and each filter confirm re-read the
 * same pool. The policy here: the **first** read of a Subcategory in a cache generation goes to the
 * server, every read after that is served from the on-device cache, and a new generation
 * ([invalidateFlashcardCache]) re-arms the server read.
 *
 * A cache read that comes back empty is treated as a miss and retried against the server. That is
 * sound only because **a Subcategory always contains at least one Flashcard** (`CONTEXT.md`):
 * Firestore reports an eviction or a never-cached query as an empty list rather than an error, so
 * without the invariant a miss would be indistinguishable from a genuinely empty Subcategory.
 *
 * `@Singleton`-bound, so the generation bookkeeping lives as long as the process.
 */
class DefaultFlashcardRepository @Inject constructor(
    private val remoteDataSource: FlashcardRemoteDataSource
) : FlashcardRepository {

    private val cacheMutex = Mutex()

    /** Subcategory id -> the generation in which it was last read from the server. */
    private val serverReadGenerations = mutableMapOf<String, Long>()

    /**
     * `AtomicLong`, not a plain `var`: [invalidateFlashcardCache] is a non-suspend call reachable
     * from any thread, while [readFromServer] reads it from `Dispatchers.IO`. A plain field has no
     * cross-thread visibility guarantee; `AtomicLong` does, and its atomic
     * [java.util.concurrent.atomic.AtomicLong.incrementAndGet] means invalidation itself never
     * needs [cacheMutex].
     */
    private val cacheGeneration = AtomicLong(0L)

    override suspend fun fetchCategories(): Result<List<Category>> = withContext(Dispatchers.IO) {
        try {
            Result.success(remoteDataSource.getCategories().map { it.toDomain() })
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun fetchSubcategories(categoryId: String): Result<List<Subcategory>> = withContext(Dispatchers.IO) {
        try {
            Result.success(
                remoteDataSource.getSubcategoriesByCategoryId(categoryId).map { it.toDomain() }
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun fetchCategoriesByIds(ids: Set<String>): Result<List<Category>> = withContext(Dispatchers.IO) {
        try {
            Result.success(remoteDataSource.getCategoriesByIds(ids).map { it.toDomain() })
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun fetchSubcategoriesByIds(ids: Set<String>): Result<List<Subcategory>> = withContext(Dispatchers.IO) {
        try {
            Result.success(remoteDataSource.getSubcategoriesByIds(ids).map { it.toDomain() })
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun searchSubcategories(namePrefix: String): Result<List<Subcategory>> = withContext(Dispatchers.IO) {
        try {
            Result.success(
                remoteDataSource.searchSubcategoriesByNamePrefix(namePrefix).map { it.toDomain() }
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun fetchFlashcards(subcategoryId: String): Result<List<Flashcard>> = withContext(Dispatchers.IO) {
        try {
            Result.success(readFlashcards(subcategoryId).mapNotNull { it.toDomain(subcategoryId) })
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override fun invalidateFlashcardCache() {
        val newGeneration = cacheGeneration.incrementAndGet()
        Log.d(TAG, "Flashcard cache invalidated, generation is now $newGeneration")
    }

    override suspend fun fetchCacheSeed(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Result.success(remoteDataSource.getCacheSeed())
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    /**
     * Server on the first read of this Subcategory in the current generation, cache after that —
     * with an empty cache result retried against the server, since Firestore reports an eviction as
     * emptiness rather than an error and a Subcategory is never genuinely empty.
     */
    private suspend fun readFlashcards(subcategoryId: String): List<FlashcardDto> {
        if (needsServerRead(subcategoryId)) {
            return readFromServer(subcategoryId)
        }
        val cached = remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Cache)
        return cached.ifEmpty { readFromServer(subcategoryId) }
    }

    /**
     * Checking and stamping are separate lock acquisitions, so two *concurrent* first reads of the
     * same Subcategory can both go to the server. Deliberate: holding the lock across the read would
     * serialize the multi-Subcategory fan-out, which reads distinct ids and never collides. The
     * duplicate costs one extra read, not a wrong answer.
     */
    private suspend fun needsServerRead(subcategoryId: String): Boolean = cacheMutex.withLock {
        serverReadGenerations[subcategoryId] != cacheGeneration.get()
    }

    /**
     * The generation is recorded only after the read succeeds — a throwing read leaves the
     * Subcategory un-stamped, so the next attempt goes to the server again rather than falling
     * through to a cache that was never populated.
     *
     * [startGeneration] is captured *before* the network call and re-checked *after* it, both
     * against the live [cacheGeneration] rather than a value trusted from before the call: an
     * [invalidateFlashcardCache] that lands while this request is in flight must not have its new
     * generation stamped with a response that was actually fetched under the old one. When that
     * happens, this response is still returned to its own caller — it was genuinely fetched from
     * the server, just not new enough to trust as "current" for the *next* read — but the
     * Subcategory is left un-stamped, so that next read goes to the server again instead of
     * treating this stale response as belonging to the new generation.
     */
    private suspend fun readFromServer(subcategoryId: String): List<FlashcardDto> {
        val startGeneration = cacheGeneration.get()
        val fromServer = remoteDataSource.getFlashcardsBySubcategoryId(subcategoryId, FlashcardReadSource.Server)
        cacheMutex.withLock {
            if (cacheGeneration.get() == startGeneration) {
                serverReadGenerations[subcategoryId] = startGeneration
            }
        }
        return fromServer
    }

    private companion object {
        const val TAG = "FlashcardRepository"
    }
}
