package com.rossomak.flashcards.presentation.splash

import com.rossomak.flashcards.core.ui.navigation.NavigationEvent
import com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsRoute
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsRoute

sealed interface SplashDestination : NavigationEvent {
    data object Main : SplashDestination
    data object Onboarding : SplashDestination
    data object Login : SplashDestination

    /** A resolved launcher-shortcut target, opened on top of Main instead of landing on Main alone. */
    data class ToCategoryDetails(val route: CategoryDetailsRoute) : SplashDestination
    data class ToSubcategoryDetails(val route: SubcategoryDetailsRoute) : SplashDestination
}
