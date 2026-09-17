package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.data.mapper.toDomain
import com.rossomak.flashcards.core.data.source.UserFavoritesRemoteDataSource
import com.rossomak.flashcards.core.domain.model.UserFavorites
import com.rossomak.flashcards.core.domain.repository.UserFavoritesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultUserFavoritesRepository @Inject constructor(
    private val remoteDataSource: UserFavoritesRemoteDataSource,
) : UserFavoritesRepository {

    override fun observeFavorites(): Flow<UserFavorites> =
        remoteDataSource.observeFavorites()
            .map { it.toDomain() }
            .retryOnFirestorePermissionDenied()

    override suspend fun setCategoryFavorite(categoryId: String, isFavorite: Boolean): Result<Unit> =
        runCatchingFirestoreWrite { remoteDataSource.setCategoryFavorite(categoryId, isFavorite) }

    override suspend fun setSubcategoryFavorite(subcategoryId: String, isFavorite: Boolean): Result<Unit> =
        runCatchingFirestoreWrite { remoteDataSource.setSubcategoryFavorite(subcategoryId, isFavorite) }

    override suspend fun setSubcategoriesFavorite(subcategoryIds: Set<String>, isFavorite: Boolean): Result<Unit> =
        runCatchingFirestoreWrite { remoteDataSource.setSubcategoriesFavorite(subcategoryIds, isFavorite) }
}
