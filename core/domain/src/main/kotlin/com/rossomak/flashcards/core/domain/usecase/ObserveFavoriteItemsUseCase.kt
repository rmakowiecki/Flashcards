package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.FavoriteItem
import com.rossomak.flashcards.core.domain.repository.FlashcardRepository
import com.rossomak.flashcards.core.domain.repository.UserFavoritesRepository
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val FETCH_RETRY_ATTEMPTS = 2
private const val FETCH_RETRY_BASE_DELAY_MILLIS = 300L

/**
 * Resolves [UserFavoritesRepository.observeFavorites] ids into full [FavoriteItem]s, sorted
 * most-recently-favorited first across both categories and subcategories. Shared by every
 * favorites display consumer — the home screen's carousel today, the launcher shortcuts list
 * later (see docs/design/launcher-shortcuts.md) — since both need the same join-and-interleave
 * behavior, not just the raw ids [UserFavoritesRepository] hands back.
 *
 * A resolution failure for one id type (e.g. offline) degrades to an empty list for that type
 * rather than failing the whole emission.
 */
class ObserveFavoriteItemsUseCase @Inject constructor(
    private val userFavoritesRepository: UserFavoritesRepository,
    private val flashcardRepository: FlashcardRepository,
) : NoParamUseCase<Flow<List<FavoriteItem>>> {
    override suspend operator fun invoke(): Flow<List<FavoriteItem>> =
        userFavoritesRepository.observeFavorites().map { favorites ->
            val subcategories = retryFetch {
                flashcardRepository.fetchSubcategoriesByIds(favorites.subcategoryIds.keys)
            }

            // One fetch for both directly-favorited categories and favorited subcategories'
            // parent categories, rather than two separate `whereIn` calls.
            val categoryIdsToFetch = favorites.categoryIds.keys + subcategories.map { it.categoryId }
            val categoriesById = retryFetch { flashcardRepository.fetchCategoriesByIds(categoryIdsToFetch) }
                .associateBy(Category::id)

            val favoriteCategories = favorites.categoryIds.keys
                .mapNotNull { categoryId -> categoriesById[categoryId] }
                .map { category ->
                    FavoriteItem.FavoriteCategory(
                        category = category,
                        favoritedAt = favorites.categoryIds.getValue(category.id),
                    )
                }
            val favoriteSubcategories = subcategories.mapNotNull { subcategory ->
                val parentCategory = categoriesById[subcategory.categoryId] ?: return@mapNotNull null
                FavoriteItem.FavoriteSubcategory(
                    subcategory = subcategory,
                    parentCategory = parentCategory,
                    favoritedAt = favorites.subcategoryIds.getValue(subcategory.id),
                )
            }

            (favoriteCategories + favoriteSubcategories).sortedByDescending { it.favoritedAt }
        }

    // Transient one-shot fetch failures (e.g. reconnect race) get a few bounded retries before
    // degrading to empty, since unlike observeFavorites() these calls have no listener to retry them.
    private suspend fun <T> retryFetch(block: suspend () -> Result<List<T>>): List<T> {
        repeat(FETCH_RETRY_ATTEMPTS) { attempt ->
            block().getOrNull()?.let { return it }
            delay(FETCH_RETRY_BASE_DELAY_MILLIS * (attempt + 1))
        }
        return block().getOrDefault(emptyList())
    }
}
