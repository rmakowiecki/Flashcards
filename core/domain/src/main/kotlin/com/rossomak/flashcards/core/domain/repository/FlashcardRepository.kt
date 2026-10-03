package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.Subcategory

interface FlashcardRepository {

    suspend fun fetchCategories(): Result<List<Category>>

    /** Categories matching [ids], in no particular order. Ids with no matching document are omitted. */
    suspend fun fetchCategoriesByIds(ids: Set<String>): Result<List<Category>>

    suspend fun fetchSubcategories(categoryId: String): Result<List<Subcategory>>

    /** Subcategories matching [ids], in no particular order. Ids with no matching document are omitted. */
    suspend fun fetchSubcategoriesByIds(ids: Set<String>): Result<List<Subcategory>>

    /**
     * Subcategories across every category whose name starts with [namePrefix], ordered by name
     * ascending and capped to a small page — a live query, never a bulk load of the collection.
     * Matching is prefix-only and case-insensitive; [namePrefix] is normalized by the caller.
     */
    suspend fun searchSubcategories(namePrefix: String): Result<List<Subcategory>>

    /**
     * A Subcategory's whole flashcard pool.
     *
     * Cached: repeat calls within a cache generation are served without contacting the backend, so
     * a caller may re-read freely rather than holding a pool of its own. Invalidated only by
     * [invalidateFlashcardCache].
     */
    suspend fun fetchFlashcards(subcategoryId: String): Result<List<Flashcard>>

    /**
     * Starts a new cache generation: the next [fetchFlashcards] for each Subcategory goes to the
     * backend, and reads after that are served from cache again.
     *
     * Called by `SyncFlashcardCacheGenerationUseCase` (core:domain) on app start, when [fetchCacheSeed]
     * disagrees with the locally stored copy (ADR-0039).
     */
    fun invalidateFlashcardCache()

    /**
     * The server-side knowledge-base seed (`meta/seed.value`) — a monotonic int bumped by the
     * import script every time it writes new content. Compared against a locally stored copy to
     * decide whether to call [invalidateFlashcardCache] (ADR-0039). Always a live read, never
     * served from cache — that would defeat the purpose of checking at all.
     */
    suspend fun fetchCacheSeed(): Result<Int>
}
