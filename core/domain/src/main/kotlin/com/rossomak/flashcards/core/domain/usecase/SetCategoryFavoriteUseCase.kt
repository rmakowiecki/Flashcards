package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.repository.UserFavoritesRepository
import com.rossomak.flashcards.core.domain.usecase.base.UseCase
import javax.inject.Inject

class SetCategoryFavoriteUseCase @Inject constructor(
    private val userFavoritesRepository: UserFavoritesRepository,
) : UseCase<SetCategoryFavoriteUseCase.Params, Result<Unit>> {

    data class Params(val categoryId: String, val isFavorite: Boolean)

    override suspend operator fun invoke(params: Params): Result<Unit> =
        userFavoritesRepository.setCategoryFavorite(params.categoryId, params.isFavorite)
}
