package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.SubcategoryAvailability
import com.rossomak.flashcards.core.domain.repository.FlashcardRepository
import com.rossomak.flashcards.core.domain.usecase.base.UseCase
import javax.inject.Inject

class ResolveSubcategoryAvailabilityUseCase @Inject constructor(
    private val repository: FlashcardRepository
) : UseCase<Set<String>, Map<String, SubcategoryAvailability>> {

    override suspend operator fun invoke(params: Set<String>): Map<String, SubcategoryAvailability> =
        repository.resolveSubcategoryAvailability(ids = params)
}
