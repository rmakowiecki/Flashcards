package com.rossomak.flashcards.core.data.repository

import com.google.firebase.firestore.FirebaseFirestoreException
import com.rossomak.flashcards.core.data.mapper.toDomain
import com.rossomak.flashcards.core.data.source.UserFavoritesRemoteDataSource
import com.rossomak.flashcards.core.domain.model.UserFavorites
import com.rossomak.flashcards.core.domain.repository.UserFavoritesRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DefaultUserFavoritesRepository @Inject constructor(
    private val remoteDataSource: UserFavoritesRemoteDataSource,
) : UserFavoritesRepository {

    override fun observeFavorites(): Flow<UserFavorites> =
        remoteDataSource.observeFavorites()
            .map { it.toDomain() }
            .catch { exception ->
                // Firestore rejects the listener with PERMISSION_DENIED once sign-out clears auth
                // mid-collection; swallow just that case here so it never leaks past this boundary.
                if (exception is FirebaseFirestoreException &&
                    exception.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                ) {
                    return@catch
                }
                throw exception
            }
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
