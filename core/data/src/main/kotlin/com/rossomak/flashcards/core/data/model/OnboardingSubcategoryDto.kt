package com.rossomak.flashcards.core.data.model

/** One entry in `onboarding/subcategories`'s `subcategories` map — see [OnboardingSubcategoriesDocument]. */
data class OnboardingSubcategoryDto(
    val order: Int = 0,
    val categoryId: String = "",
    val categoryName: String = "",
    val subcategoryId: String = "",
    val subcategoryName: String = "",
    val iconSvg: String? = null,
)

/**
 * Deserialization target for the single `onboarding/subcategories` document: every curated entry
 * lives in one map field, keyed by subcategory id, so the whole curated list costs one Firestore
 * read (see [com.rossomak.flashcards.core.data.source.OnboardingSubcategoriesRemoteDataSource]).
 */
data class OnboardingSubcategoriesDocument(
    val subcategories: Map<String, OnboardingSubcategoryDto> = emptyMap(),
)
