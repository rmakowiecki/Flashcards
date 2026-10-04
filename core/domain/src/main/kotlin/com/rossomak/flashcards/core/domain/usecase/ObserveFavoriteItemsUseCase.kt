package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.FavoriteItem
import com.rossomak.flashcards.core.domain.repository.FlashcardRepository
import com.rossomak.flashcards.core.domain.repository.UserFavoritesRepository
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

/**
 * Resolves [UserFavoritesRepository.observeFavorites] ids into full [FavoriteItem]s, sorted
 * most-recently-favorited first across both categories and subcategories. Shared by every
 * favorites display consumer — the home screen's carousel today, the launcher shortcuts list
 * later (see docs/design/launcher-shortcuts.md) — since both need the same join-and-interleave
 * behavior, not just the raw ids [UserFavoritesRepository] hands back.
 *
 * A resolution failure for one id type (e.g. offline) degrades to an empty list for that type
 * rather than failing the whole emission. A newer favorites emission cancels the resolution of an
 * older one, so a slow retry never delays the latest list.
 */
class ObserveFavoriteItemsUseCase @Inject constructor(
    private val userFavoritesRepository: UserFavoritesRepository,
    private val flashcardRepository: FlashcardRepository,
) : NoParamUseCase<Flow<List<FavoriteItem>>> {
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend operator fun invoke(): Flow<List<FavoriteItem>> =
        userFavoritesRepository.observeFavorites().mapLatest { favorites ->
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
}
