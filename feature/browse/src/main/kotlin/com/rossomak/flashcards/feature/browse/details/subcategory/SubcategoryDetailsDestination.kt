package com.rossomak.flashcards.feature.browse.details.subcategory

import com.rossomak.flashcards.core.domain.model.FlashcardSortOrder
import com.rossomak.flashcards.core.ui.navigation.NavigationEvent

sealed interface SubcategoryDetailsDestination : NavigationEvent {

    /**
     * @param sortOrder always non-null from here: this screen resolved an order the moment it
     * seeded from the user's preference, and the list the user just looked at is ordered by it. The
     * route it lands on accepts null from entry points that never had a list to order (ADR-0038).
     */
    data class PreviewStudySession(
        val categoryId: String,
        val categoryName: String,
        val subcategoryId: String,
        val subcategoryName: String,
        val filterTagIds: List<String>,
        val difficultyRange: IntRange,
        val sortOrder: FlashcardSortOrder,
    ) : SubcategoryDetailsDestination
}
