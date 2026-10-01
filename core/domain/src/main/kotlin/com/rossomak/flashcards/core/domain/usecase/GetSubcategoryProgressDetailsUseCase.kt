package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.SubcategoryProgressDetails
import com.rossomak.flashcards.core.domain.repository.CardProgressRepository
import com.rossomak.flashcards.core.domain.usecase.base.UseCase
import javax.inject.Inject

/**
 * Thin wrapper around [CardProgressRepository.getProgress], mirroring [GetFlashcardsUseCase]'s shape:
 * one Subcategory id in, its packed progress document out. Both Study Session ViewModels fan this out
 * once per Subcategory in scope at session start to learn
 * which cards are new and which were previously Mastered — never chunked or batched, since packing
 * already removed the old `whereIn` thirty-id cap this read used to be subject to.
 */
class GetSubcategoryProgressDetailsUseCase @Inject constructor(
    private val repository: CardProgressRepository,
) : UseCase<String, Result<SubcategoryProgressDetails?>> {

    override suspend operator fun invoke(params: String): Result<SubcategoryProgressDetails?> =
        repository.getProgress(subcategoryId = params)
}
