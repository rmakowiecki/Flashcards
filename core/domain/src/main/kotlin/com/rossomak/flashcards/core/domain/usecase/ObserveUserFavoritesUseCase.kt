package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.UserFavorites
import com.rossomak.flashcards.core.domain.repository.UserFavoritesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Raw [UserFavorites] ids, for consumers that only need to check membership (e.g. badging list
 * rows) and don't need the full joined objects [ObserveFavoriteItemsUseCase] resolves.
 */
class ObserveUserFavoritesUseCase @Inject constructor(
    private val userFavoritesRepository: UserFavoritesRepository,
) {
    operator fun invoke(): Flow<UserFavorites> = userFavoritesRepository.observeFavorites()
}
