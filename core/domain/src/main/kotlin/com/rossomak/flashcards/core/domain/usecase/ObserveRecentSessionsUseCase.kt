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
 * Resolves [RecentSessionsRepository.observeRecentSessions] against the current taxonomy, newest first.
 *
 * A Recent whose Category no longer resolves is dropped, as is a single-subcategory Recent whose
 * Subcategory no longer resolves. A Custom Recent keeps only the Subcategories that resolve, possibly
 * none. A Quick Recent resolves none: its replay samples the Category again. A fetch that keeps failing
 * resolves nothing instead of failing the emission.
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
