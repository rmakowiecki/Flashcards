package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.FavoriteItem
import com.rossomak.flashcards.core.domain.model.FavoriteItemsResult
import com.rossomak.flashcards.core.domain.model.FavoriteItemsResult.Resolved
import com.rossomak.flashcards.core.domain.model.FavoriteItemsResult.Unresolved
import com.rossomak.flashcards.core.domain.model.UserFavorites
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
 * favorites display consumer — the home screen's carousel and the launcher shortcuts sync — since
 * both need the same join-and-interleave behavior, not just the raw ids [UserFavoritesRepository]
 * hands back.
 *
 * A fetch that runs out of retries (e.g. offline) makes the emission [Unresolved] rather than a
 * shorter list, so a consumer never mistakes a failed fetch for Favorites the User does not have.
 * A newer favorites emission cancels the resolution of an older one, so a slow retry never delays
 * the latest list.
 */
class ObserveFavoriteItemsUseCase @Inject constructor(
    private val userFavoritesRepository: UserFavoritesRepository,
    private val flashcardRepository: FlashcardRepository,
) : NoParamUseCase<Flow<FavoriteItemsResult>> {
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend operator fun invoke(): Flow<FavoriteItemsResult> =
        userFavoritesRepository.observeFavorites().mapLatest { favorites -> resolve(favorites) }

    private suspend fun resolve(favorites: UserFavorites): FavoriteItemsResult {
        val subcategories = fetchUnlessEmpty(favorites.subcategoryIds.keys) { ids ->
            flashcardRepository.fetchSubcategoriesByIds(ids)
        } ?: return Unresolved

        // One fetch for both directly-favorited categories and favorited subcategories'
        // parent categories, rather than two separate `whereIn` calls.
        val categoryIdsToFetch = favorites.categoryIds.keys + subcategories.map { it.categoryId }
        val categoriesById = fetchUnlessEmpty(categoryIdsToFetch) { ids -> flashcardRepository.fetchCategoriesByIds(ids) }
            ?.associateBy(Category::id)
            ?: return Unresolved

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

        return Resolved((favoriteCategories + favoriteSubcategories).sortedByDescending { it.favoritedAt })
    }

    /** `null` when the fetch ran out of retries; no ids need no fetch. */
    private suspend fun <T> fetchUnlessEmpty(ids: Set<String>, fetch: suspend (Set<String>) -> Result<List<T>>): List<T>? =
        if (ids.isEmpty()) emptyList() else retryFetchOrNull { fetch(ids) }
}
