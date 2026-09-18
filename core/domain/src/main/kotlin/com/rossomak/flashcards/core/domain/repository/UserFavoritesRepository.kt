package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.UserFavorites
import kotlinx.coroutines.flow.Flow

interface UserFavoritesRepository {

    /**
     * One data source backs both category and subcategory favorites
     * Every screen that surfaces favorite state (Home, Category Details, Subcategory Details, onboarding) observes this collection rather than issuing its own per-item read.
     * Caller UseCases might need just the IDs model, or want to merge the IDs with full e.g. Subcategory and Category models and resolve them, it's up to their discretion.
     */
    fun observeFavorites(): Flow<UserFavorites>

    suspend fun setCategoryFavorite(categoryId: String, isFavorite: Boolean): Result<Unit>

    suspend fun setSubcategoryFavorite(subcategoryId: String, isFavorite: Boolean): Result<Unit>

    /**
     * Favorites (or unfavorites) every id in [subcategoryIds] in one write, regardless of N — used
     * by onboarding's multi-select step, which would otherwise need N separate write operations
     */
    suspend fun setSubcategoriesFavorite(subcategoryIds: Set<String>, isFavorite: Boolean): Result<Unit>
}
