package com.rossomak.flashcards.feature.browse.details.category

import com.rossomak.flashcards.core.ui.navigation.NavigationEvent

sealed interface CategoryDetailsDestination : NavigationEvent {

    /**
     * Both of Category Details' CTAs land here — [CategoryDetailsViewModel.onQuickSessionStart]
     * and [CategoryDetailsViewModel.onCustomSessionStart] — distinguished only by [isQuickSession]
     * and by which [subcategoryIds] they carry: every topic for Quick, exactly the selection for
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
