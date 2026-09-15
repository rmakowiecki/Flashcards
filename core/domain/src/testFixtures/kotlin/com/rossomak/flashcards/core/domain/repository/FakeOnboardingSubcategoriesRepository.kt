package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.OnboardingSubcategory

class FakeOnboardingSubcategoriesRepository : OnboardingSubcategoriesRepository {
    /** Overrides every [fetchOnboardingSubcategories] call, success or failure alike. */
    var resultToReturn: Result<List<OnboardingSubcategory>> = Result.success(emptyList())

    override suspend fun fetchOnboardingSubcategories(): Result<List<OnboardingSubcategory>> = resultToReturn
}
