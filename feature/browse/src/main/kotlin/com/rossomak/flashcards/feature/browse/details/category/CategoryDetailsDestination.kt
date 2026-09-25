package com.rossomak.flashcards.feature.browse.details.category

import com.rossomak.flashcards.core.ui.navigation.NavigationEvent

sealed interface CategoryDetailsDestination : NavigationEvent {

    /** A Subcategory row's tap — [CategoryDetailsViewModel.onSubcategorySelect]. */
    data class SubcategoryDetails(
        val categoryId: String,
        val categoryName: String,
        val subcategoryId: String,
        val subcategoryName: String,
    ) : CategoryDetailsDestination

    /** A Subcategory row's play button — [CategoryDetailsViewModel.onSubcategorySessionStart]. */
    data class SubcategoryPreviewStudySession(
        val categoryId: String,
        val categoryName: String,
        val subcategoryId: String,
        val subcategoryName: String,
    ) : CategoryDetailsDestination

    /**
     * Both of Category Details' CTAs land here — [CategoryDetailsViewModel.onQuickSessionStart]
     * and [CategoryDetailsViewModel.onCustomSessionStart] — distinguished only by [isQuickSession]
     * and by which [subcategoryIds] they carry: every subcategory for Quick, exactly the selection for
     * Custom.
     */
    data class PreviewStudySession(
        val categoryId: String,
        val categoryName: String,
        val subcategoryIds: List<String>,
        val subcategoryNames: List<String>,
        val isQuickSession: Boolean,
    ) : CategoryDetailsDestination
}
