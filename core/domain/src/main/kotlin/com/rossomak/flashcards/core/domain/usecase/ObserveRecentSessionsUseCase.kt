package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.RecentItem
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
 * [RecentSessionsRepository.observeRecentSessions] with each Category looked up for its color and icon,
 * as Favorites does. Never drops a Recent: one whose Category cannot be read keeps a `null` Category.
 */
class ObserveRecentSessionsUseCase @Inject constructor(
    private val recentSessionsRepository: RecentSessionsRepository,
    private val flashcardRepository: FlashcardRepository,
) : NoParamUseCase<Flow<List<RecentItem>>> {
    override suspend operator fun invoke(): Flow<List<RecentItem>> =
        recentSessionsRepository.observeRecentSessions().map { sessions ->
            val categoriesById = fetchCategories(sessions.mapTo(mutableSetOf()) { session -> session.categoryId })
                .associateBy(Category::id)
            sessions.map { session -> RecentItem(session, categoriesById[session.categoryId]) }
        }

    // Transient one-shot fetch failures (e.g. reconnect race) get a few bounded retries before
    // degrading to empty, since unlike observeRecentSessions() this call has no listener to retry it.
    private suspend fun fetchCategories(ids: Set<String>): List<Category> {
        if (ids.isEmpty()) return emptyList()
        repeat(FETCH_RETRY_ATTEMPTS) { attempt ->
            flashcardRepository.fetchCategoriesByIds(ids).getOrNull()?.let { return it }
            delay((FETCH_RETRY_BASE_DELAY_MILLIS * (attempt + 1)).milliseconds)
        }
        return flashcardRepository.fetchCategoriesByIds(ids).getOrDefault(emptyList())
    }
}
