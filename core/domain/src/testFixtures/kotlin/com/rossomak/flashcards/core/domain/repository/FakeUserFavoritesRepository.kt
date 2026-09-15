package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.UserFavorites
import kotlinx.coroutines.flow.MutableStateFlow

class FakeUserFavoritesRepository : UserFavoritesRepository {
    val favorites = MutableStateFlow(UserFavorites.EMPTY)
    var setSubcategoriesFavoriteResult: Result<Unit> = Result.success(Unit)
    var lastSetSubcategoriesFavoriteCall: Pair<Set<String>, Boolean>? = null

    override fun observeFavorites() = favorites

    override suspend fun setCategoryFavorite(categoryId: String, isFavorite: Boolean): Result<Unit> =
        Result.success(Unit)

    override suspend fun setSubcategoryFavorite(subcategoryId: String, isFavorite: Boolean): Result<Unit> =
        Result.success(Unit)

    override suspend fun setSubcategoriesFavorite(subcategoryIds: Set<String>, isFavorite: Boolean): Result<Unit> {
        lastSetSubcategoriesFavoriteCall = subcategoryIds to isFavorite
        return setSubcategoriesFavoriteResult
    }
}
