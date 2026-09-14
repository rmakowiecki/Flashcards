package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.data.mapper.toDomain
import com.rossomak.flashcards.core.data.source.UserFavoritesRemoteDataSource
import com.rossomak.flashcards.core.domain.model.UserFavorites
import com.rossomak.flashcards.core.domain.repository.UserFavoritesRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DefaultUserFavoritesRepository @Inject constructor(
    private val remoteDataSource: UserFavoritesRemoteDataSource,
) : UserFavoritesRepository {

    override fun observeFavorites(): Flow<UserFavorites> =
        remoteDataSource.observeFavorites()
            .map { it.toDomain() }
            .flowOn(Dispatchers.IO)

    override suspend fun setCategoryFavorite(categoryId: String, isFavorite: Boolean): Result<Unit> =
        runCatchingWrite { remoteDataSource.setCategoryFavorite(categoryId, isFavorite) }

    override suspend fun setSubcategoryFavorite(subcategoryId: String, isFavorite: Boolean): Result<Unit> =
        runCatchingWrite { remoteDataSource.setSubcategoryFavorite(subcategoryId, isFavorite) }

    override suspend fun setSubcategoriesFavorite(subcategoryIds: Set<String>, isFavorite: Boolean): Result<Unit> =
        runCatchingWrite { remoteDataSource.setSubcategoriesFavorite(subcategoryIds, isFavorite) }

    // Generic catch is deliberate: this is the repository boundary converting any Firestore/task failure into Result.failure
    @Suppress("TooGenericExceptionCaught")
    private suspend fun runCatchingWrite(block: suspend () -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            block()
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }
}
