package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.OnboardingSubcategory
import com.rossomak.flashcards.core.domain.repository.OnboardingSubcategoriesRepository
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject

class GetOnboardingSubcategoriesUseCase @Inject constructor(
    private val onboardingSubcategoriesRepository: OnboardingSubcategoriesRepository,
) : NoParamUseCase<Result<List<OnboardingSubcategory>>> {

    override suspend operator fun invoke(): Result<List<OnboardingSubcategory>> =
        onboardingSubcategoriesRepository.fetchOnboardingSubcategories()
}
