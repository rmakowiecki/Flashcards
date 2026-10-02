package com.rossomak.flashcards.feature.home

import com.rossomak.flashcards.core.ui.navigation.NavigationEvent

sealed interface HomeDestination : NavigationEvent {

    /** A Favorite Category card's body — [HomeViewModel.onFavoriteCategorySelect]. */
    data class CategoryDetails(
        val categoryId: String,
        val categoryName: String,
    ) : HomeDestination

    /** A Favorite Subcategory card's body — [HomeViewModel.onFavoriteSubcategorySelect]. */
    data class SubcategoryDetails(
        val categoryId: String,
        val categoryName: String,
        val subcategoryId: String,
        val subcategoryName: String,
    ) : HomeDestination

    /** A Favorite Subcategory card's play button — [HomeViewModel.onFavoriteSubcategorySessionStart]. */
    data class SubcategoryPreviewStudySession(
        val categoryId: String,
        val categoryName: String,
        val subcategoryId: String,
        val subcategoryName: String,
    ) : HomeDestination

    /**
     * A Favorite Category card's Quick session button — [HomeViewModel.onFavoriteCategoryQuickSessionStart].
     * Carries no Subcategories: the Preview screen resolves the candidate pool itself (ADR-0056).
     */
    data class QuickSessionPreviewStudySession(
        val categoryId: String,
        val categoryName: String,
    ) : HomeDestination
}
