package com.rossomak.flashcards.core.data.repository

import com.google.firebase.firestore.FirebaseFirestoreException
import com.rossomak.flashcards.core.data.mapper.toDomain
import com.rossomak.flashcards.core.data.source.UserFavoritesRemoteDataSource
import com.rossomak.flashcards.core.domain.model.UserFavorites
import com.rossomak.flashcards.core.domain.repository.UserFavoritesRepository
import javax.inject.Inject
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext

class DefaultUserFavoritesRepository @Inject constructor(
    private val remoteDataSource: UserFavoritesRemoteDataSource,
) : UserFavoritesRepository {

    override fun observeFavorites(): Flow<UserFavorites> =
        remoteDataSource.observeFavorites()
            .map { it.toDomain() }
            .retryWhen { cause, attempt ->
                // Sign-out clears auth mid-collection and Firestore rejects the listener with
                // PERMISSION_DENIED; that's an intentional teardown, not a transient failure, so
                // let it fall through to .catch below instead of reopening a now-unauthenticated listener.
                if (cause.isPermissionDenied()) {
                    false
                } else {
                    delay(retryBackoffMillis(attempt))
                    true
                }
            }
            .catch { exception ->
                if (exception.isPermissionDenied()) {
                    return@catch
                }
                throw exception
            }
            .flowOn(Dispatchers.IO)

    private fun Throwable.isPermissionDenied(): Boolean =
        this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED

    private fun retryBackoffMillis(attempt: Long): Long =
        min(INITIAL_RETRY_BACKOFF_MILLIS shl attempt.coerceAtMost(MAX_RETRY_BACKOFF_SHIFT).toInt(), MAX_RETRY_BACKOFF_MILLIS)

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

    private companion object {
        const val INITIAL_RETRY_BACKOFF_MILLIS = 1_000L
        const val MAX_RETRY_BACKOFF_MILLIS = 30_000L
        const val MAX_RETRY_BACKOFF_SHIFT = 5L
    }
}
