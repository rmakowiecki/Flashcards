package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.OnboardingSubcategory

/**
 * Serves the small, admin-curated, publicly-readable list of Subcategories that back onboarding's
 * Favorites step — one denormalized Firestore document (`onboarding/subcategories`), separate
 * from the full, auth-gated `subcategories` taxonomy [FlashcardRepository] owns (see
 * firestore-schema.md).
 *
 * Deliberately not folded into [UserFavoritesRepository]: that repository's own `observeFavorites`
 * doc comment already scopes it to Home / Category Details / Subcategory Details / onboarding
 * favorite-*state*, and a content-sourcing method here would leak an onboarding-only concern into
 * three unrelated consumers. Mirrors [XpConfigRepository]'s shape — one method, one model, one
 * caller.
 */
interface OnboardingSubcategoriesRepository {
    suspend fun fetchOnboardingSubcategories(): Result<List<OnboardingSubcategory>>
}
