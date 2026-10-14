package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.repository.FlashcardRepository
import com.rossomak.flashcards.core.domain.usecase.base.UseCase
import javax.inject.Inject

/** Fetches one Category by id; a missing Category fails with [NoSuchElementException]. */
class GetCategoryUseCase @Inject constructor(
    private val repository: FlashcardRepository
) : UseCase<String, Result<Category>> {

    override suspend operator fun invoke(params: String): Result<Category> =
        repository.fetchCategoriesByIds(setOf(params)).mapCatching { it.first() }
}
