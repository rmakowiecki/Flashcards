package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.repository.UserFavoritesRepository
import com.rossomak.flashcards.core.domain.usecase.base.UseCase
import javax.inject.Inject

/**
 * Named for 1:1 fidelity with [UserFavoritesRepository.setSubcategoriesFavorite], not `Save*` —
 * that verb stays reserved for [SaveOnboardingPreferencesUseCase].
 */
class SetFavoriteSubcategoriesUseCase @Inject constructor(
    private val userFavoritesRepository: UserFavoritesRepository,
) : UseCase<SetFavoriteSubcategoriesUseCase.Params, Result<Unit>> {

    data class Params(val subcategoryIds: Set<String>, val isFavorite: Boolean = true)

    override suspend operator fun invoke(params: Params): Result<Unit> =
        userFavoritesRepository.setSubcategoriesFavorite(params.subcategoryIds, params.isFavorite)
}
