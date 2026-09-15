package com.rossomak.flashcards.core.data.model

/**
 * One entry in `onboarding/subcategories`'s `subcategories` map — see [OnboardingSubcategoriesDocument].
 * Required fields are nullable because Firestore is an external data source: a missing field
 * deserializes to `null` rather than a silently-wrong `""`, so [toDomain][com.rossomak.flashcards.core.data.mapper.toDomain]
 * can reject the entry instead of letting an empty id/name flow into the domain model.
 */
data class OnboardingSubcategoryDto(
    val order: Int = 0,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val subcategoryId: String? = null,
    val subcategoryName: String? = null,
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
