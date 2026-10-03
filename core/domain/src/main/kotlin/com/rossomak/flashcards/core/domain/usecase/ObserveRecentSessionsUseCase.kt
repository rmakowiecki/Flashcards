package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.RecentItem
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Custom
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Quick
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.repository.FlashcardRepository
import com.rossomak.flashcards.core.domain.repository.RecentSessionsRepository
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val FETCH_RETRY_ATTEMPTS = 2
private const val FETCH_RETRY_BASE_DELAY_MILLIS = 300L

/**
 * Resolves [RecentSessionsRepository.observeRecentSessions] against the current taxonomy, keeping the
 * repository's newest-first order.
 *
 * Every Recent needs its Category, so a Recent whose Category no longer resolves is dropped, as is a
 * single-subcategory Recent whose Subcategory no longer resolves: the same rule Favorites applies. A
 * Custom Recent is never dropped for its Subcategories and keeps only those that resolve, possibly none.
 * A Quick Recent resolves no Subcategories, since its replay samples the Category again.
 *
 * Offline, a fetch returns only what is cached, so an uncached id simply does not resolve. A fetch that
 * still fails after its retries degrades to resolving nothing rather than failing the emission.
 */
class ObserveRecentSessionsUseCase @Inject constructor(
    private val recentSessionsRepository: RecentSessionsRepository,
    private val flashcardRepository: FlashcardRepository,
) : NoParamUseCase<Flow<List<RecentItem>>> {
    override suspend operator fun invoke(): Flow<List<RecentItem>> =
        recentSessionsRepository.observeRecentSessions().map { sessions ->
            val subcategoryIdsToFetch = sessions.flatMapTo(mutableSetOf()) { session ->
                when (session.sourceType) {
                    SingleSubcategory, Custom -> session.subcategoryIds
                    Quick -> emptyList()
                }
            }
            val categoryIdsToFetch = sessions.mapTo(mutableSetOf()) { session -> session.categoryId }
            val subcategoriesById = retryFetch(subcategoryIdsToFetch, flashcardRepository::fetchSubcategoriesByIds)
                .associateBy(Subcategory::id)
            val categoriesById = retryFetch(categoryIdsToFetch, flashcardRepository::fetchCategoriesByIds)
                .associateBy(Category::id)

            sessions.mapNotNull { session ->
                with(session) {
                    val category = categoriesById[categoryId] ?: return@mapNotNull null
                    when (sourceType) {
                        SingleSubcategory -> subcategoryIds.firstOrNull()?.let(subcategoriesById::get)?.let { subcategory ->
                            RecentItem(session, category, listOf(subcategory))
                        }
                        Custom -> RecentItem(session, category, subcategoryIds.mapNotNull(subcategoriesById::get))
                        Quick -> RecentItem(session, category, emptyList())
                    }
                }
            }
        }

    // Transient one-shot fetch failures (e.g. reconnect race) get a few bounded retries before
    // degrading to empty, since unlike observeRecentSessions() these calls have no listener to retry them.
    private suspend fun <T> retryFetch(ids: Set<String>, fetch: suspend (Set<String>) -> Result<List<T>>): List<T> {
        if (ids.isEmpty()) return emptyList()
        repeat(FETCH_RETRY_ATTEMPTS) { attempt ->
            fetch(ids).getOrNull()?.let { return it }
            delay((FETCH_RETRY_BASE_DELAY_MILLIS * (attempt + 1)).milliseconds)
        }
        return fetch(ids).getOrDefault(emptyList())
    }
}
