package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.UserFavorites
import kotlinx.coroutines.flow.Flow

interface UserFavoritesRepository {

    /**
     * One Firestore listener backs both category and subcategory favorites (single-document
     * design) — every screen that surfaces favorite state (Home, Category Details, Subcategory
     * Details, onboarding) observes this same document rather than issuing its own per-item read.
     */
    fun observeFavorites(): Flow<UserFavorites>

    suspend fun setCategoryFavorite(categoryId: String, isFavorite: Boolean): Result<Unit>

    suspend fun setSubcategoryFavorite(subcategoryId: String, isFavorite: Boolean): Result<Unit>

    /**
     * Favorites (or unfavorites) every id in [subcategoryIds] in one write, regardless of N — used
     * by onboarding's multi-select step, which would otherwise need a WriteBatch across N documents
     * under the old per-item schema.
     */
    suspend fun setSubcategoriesFavorite(subcategoryIds: Set<String>, isFavorite: Boolean): Result<Unit>
}
