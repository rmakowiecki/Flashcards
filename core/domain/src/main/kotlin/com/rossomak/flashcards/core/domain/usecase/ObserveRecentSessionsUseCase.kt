package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.RecentItem
import com.rossomak.flashcards.core.domain.repository.FlashcardRepository
import com.rossomak.flashcards.core.domain.repository.RecentSessionsRepository
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

/**
 * [RecentSessionsRepository.observeRecentSessions] with each Category looked up for its color and icon,
 * as Favorites does. Never drops a Recent: one whose Category cannot be read keeps a `null` Category.
 * A newer list cancels the lookup for an older one, so a just-queued session never waits behind it.
 */
class ObserveRecentSessionsUseCase @Inject constructor(
    private val recentSessionsRepository: RecentSessionsRepository,
    private val flashcardRepository: FlashcardRepository,
) : NoParamUseCase<Flow<List<RecentItem>>> {
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend operator fun invoke(): Flow<List<RecentItem>> =
        recentSessionsRepository.observeRecentSessions().mapLatest { sessions ->
            val categoriesById = fetchCategories(sessions.mapTo(mutableSetOf()) { session -> session.categoryId })
                .associateBy(Category::id)
            sessions.map { session -> RecentItem(session, categoriesById[session.categoryId]) }
        }

    private suspend fun fetchCategories(ids: Set<String>): List<Category> =
        if (ids.isEmpty()) emptyList() else retryFetch { flashcardRepository.fetchCategoriesByIds(ids) }
}
