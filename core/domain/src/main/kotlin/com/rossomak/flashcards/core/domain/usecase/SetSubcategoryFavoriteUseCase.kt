package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.repository.UserFavoritesRepository
import com.rossomak.flashcards.core.domain.usecase.base.UseCase
import javax.inject.Inject

class SetSubcategoryFavoriteUseCase @Inject constructor(
    private val userFavoritesRepository: UserFavoritesRepository,
) : UseCase<SetSubcategoryFavoriteUseCase.Params, Result<Unit>> {

    data class Params(val subcategoryId: String, val isFavorite: Boolean)

    override suspend operator fun invoke(params: Params): Result<Unit> =
        userFavoritesRepository.setSubcategoryFavorite(params.subcategoryId, params.isFavorite)
}
