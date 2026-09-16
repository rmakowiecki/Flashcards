package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.UserFavorites
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeUserFavoritesRepository : UserFavoritesRepository {
    val favorites = MutableStateFlow(UserFavorites.EMPTY)
    var setSubcategoriesFavoriteResult: Result<Unit> = Result.success(Unit)
    var lastSetSubcategoriesFavoriteCall: Pair<Set<String>, Boolean>? = null

    override fun observeFavorites() = favorites

    override suspend fun setCategoryFavorite(categoryId: String, isFavorite: Boolean): Result<Unit> {
        favorites.update { current ->
            current.copy(
                categoryIds = if (isFavorite) {
                    current.categoryIds + (categoryId to Instant.now())
                } else {
                    current.categoryIds - categoryId
                },
            )
        }
        return Result.success(Unit)
    }

    override suspend fun setSubcategoryFavorite(subcategoryId: String, isFavorite: Boolean): Result<Unit> {
        favorites.update { current ->
            current.copy(
                subcategoryIds = if (isFavorite) {
                    current.subcategoryIds + (subcategoryId to Instant.now())
                } else {
                    current.subcategoryIds - subcategoryId
                },
            )
        }
        return Result.success(Unit)
    }

    override suspend fun setSubcategoriesFavorite(subcategoryIds: Set<String>, isFavorite: Boolean): Result<Unit> {
        lastSetSubcategoriesFavoriteCall = subcategoryIds to isFavorite
        return setSubcategoriesFavoriteResult
    }
}
