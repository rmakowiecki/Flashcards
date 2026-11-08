package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.repository.FlashcardRepository
import com.rossomak.flashcards.core.domain.usecase.base.UseCase
import javax.inject.Inject

/** Fetches one Subcategory by id; a missing Subcategory fails with [NoSuchElementException]. */
class GetSubcategoryUseCase @Inject constructor(
    private val repository: FlashcardRepository
) : UseCase<String, Result<Subcategory>> {

    override suspend operator fun invoke(params: String): Result<Subcategory> =
        repository.fetchSubcategoriesByIds(setOf(params)).mapCatching { it.first() }
}
