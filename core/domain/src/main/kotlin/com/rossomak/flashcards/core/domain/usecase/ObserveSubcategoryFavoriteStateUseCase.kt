package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.repository.UserFavoritesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Favorite state for a single Subcategory, exclusive to Subcategory Details — nothing else needs
 * just one item's state at a time. For a display list use [ObserveFavoriteItemsUseCase] instead.
 */
class ObserveSubcategoryFavoriteStateUseCase @Inject constructor(
    private val userFavoritesRepository: UserFavoritesRepository,
) {
    operator fun invoke(subcategoryId: String): Flow<Boolean> =
        userFavoritesRepository.observeFavorites().map { it.subcategoryIds.containsKey(subcategoryId) }
}
