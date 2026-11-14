package com.rossomak.flashcards.core.domain.model

/**
 * One entry in onboarding's curated Favorites list — a denormalized slice of a real Subcategory
 * plus its parent Category's [iconSvg] and [color], sourced from a single Firestore document
 * (`onboarding/subcategories`) so the whole list costs one read. Deliberately not [Subcategory]:
 * that model is the general-purpose taxonomy shape (Browse, Category/Subcategory Details) and
 * carries fields (`cardCount`) onboarding doesn't need, while this carries fields ([iconSvg], [color])
 * [Subcategory] doesn't have. [color] is the parent Category's `#RRGGBB` hex string.
 */
data class OnboardingSubcategory(
    val id: String,
    val name: String,
    val categoryId: String,
    val categoryName: String,
    val order: Int,
    val iconSvg: String?,
    val color: String?,
)
