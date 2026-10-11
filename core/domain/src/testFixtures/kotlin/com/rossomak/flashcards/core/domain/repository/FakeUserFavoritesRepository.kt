package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.UserFavorites
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.yield

class FakeUserFavoritesRepository : UserFavoritesRepository {
    private val favorites = MutableStateFlow(UserFavorites.EMPTY)
    var setSubcategoriesFavoriteResult: Result<Unit> = Result.success(Unit)
    var lastSetSubcategoriesFavoriteCall: Pair<Set<String>, Boolean>? = null

    /**
     * When set, [observeFavorites] suspends on this before its first emission — lets a test park
     * the favorites read indefinitely to assert an in-between state (e.g. rows already rendered,
     * favorites still pending), mirroring [FakeCardProgressRepository.summaryReadGate]. `null` (the
     * default) keeps the old single-[yield] behavior.
     */
    var favoritesReadGate: CompletableDeferred<Unit>? = null

    /** When set, [observeFavorites] throws this before its first emission, like a failing snapshot listener. */
    var favoritesReadFailure: Throwable? = null

    /**
     * When set, [observeFavorites] completes after this many emissions, like a listener torn down on permission
     * denied; `0` completes before the first. `null` (the default) never completes.
     */
    var favoritesEmissionLimit: Int? = null

    override fun observeFavorites(): Flow<UserFavorites> = flow {
        favoritesReadGate?.await() ?: yield()
        favoritesReadFailure?.let { throw it }
        when (val limit = favoritesEmissionLimit) {
            null -> emitAll(favorites)
            0 -> Unit
            else -> emitAll(favorites.take(limit))
        }
    }

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
