package com.rossomak.flashcards.core.data.source

import com.rossomak.flashcards.core.data.model.UserFavoritesDto
import kotlinx.coroutines.flow.Flow

interface UserFavoritesRemoteDataSource {

    fun observeFavorites(): Flow<UserFavoritesDto>

    suspend fun setCategoryFavorite(categoryId: String, isFavorite: Boolean)

    suspend fun setSubcategoryFavorite(subcategoryId: String, isFavorite: Boolean)

    suspend fun setSubcategoriesFavorite(subcategoryIds: Set<String>, isFavorite: Boolean)
}
