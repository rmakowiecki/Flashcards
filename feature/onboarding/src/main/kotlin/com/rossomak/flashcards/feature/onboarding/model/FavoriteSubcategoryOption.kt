package com.rossomak.flashcards.feature.onboarding.model

import androidx.compose.runtime.Immutable

/**
 * One selectable topic on the Favorites step — a UI-only surfacing of a Subcategory; "Topic" never
 * appears outside onboarding's own presentation layer (CONTEXT.md).
 *
 * [id] is a real Subcategory id, sourced from the curated `onboarding/subcategories` document
 * (`GetOnboardingSubcategoriesUseCase`) and written back via `SetFavoriteSubcategoriesUseCase` on
 * commit. [iconSvg] is denormalized from the subcategory's parent Category on that same document.
 */
@Immutable
data class FavoriteSubcategoryOption(
    val id: String,
    val name: String,
    val categoryName: String,
    val iconSvg: String?,
)
