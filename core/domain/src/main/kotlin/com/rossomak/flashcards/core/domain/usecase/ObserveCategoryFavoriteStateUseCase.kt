package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.repository.UserFavoritesRepository
import com.rossomak.flashcards.core.domain.usecase.base.UseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Favorite state for a single Category, exclusive to Category Details — nothing else needs just
 * one item's state at a time. For a display list use [ObserveFavoriteItemsUseCase] instead.
 */
class ObserveCategoryFavoriteStateUseCase @Inject constructor(
    private val userFavoritesRepository: UserFavoritesRepository,
) : UseCase<String, Flow<Boolean>> {
    override suspend operator fun invoke(categoryId: String): Flow<Boolean> =
        userFavoritesRepository.observeFavorites().map { it.categoryIds.containsKey(categoryId) }
}
