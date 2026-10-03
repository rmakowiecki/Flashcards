package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.CategorySubcategoriesResolution
import com.rossomak.flashcards.core.domain.repository.FlashcardRepository
import com.rossomak.flashcards.core.domain.usecase.base.UseCase
import javax.inject.Inject

class ResolveCategorySubcategoriesUseCase @Inject constructor(
    private val repository: FlashcardRepository
) : UseCase<String, CategorySubcategoriesResolution> {

    override suspend operator fun invoke(params: String): CategorySubcategoriesResolution =
        repository.resolveCategorySubcategories(categoryId = params)
}
