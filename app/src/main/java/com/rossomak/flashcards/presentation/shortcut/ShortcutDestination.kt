package com.rossomak.flashcards.presentation.shortcut

import com.rossomak.flashcards.core.ui.navigation.NavigationEvent
import com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsRoute
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsRoute

/** The Details screen a launcher shortcut's route resolves to. */
sealed interface ShortcutDestination : NavigationEvent {
    val route: Any

    data class ToCategoryDetails(override val route: CategoryDetailsRoute) : ShortcutDestination
    data class ToSubcategoryDetails(override val route: SubcategoryDetailsRoute) : ShortcutDestination
}
