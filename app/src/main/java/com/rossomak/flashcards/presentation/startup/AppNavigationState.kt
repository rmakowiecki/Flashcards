package com.rossomak.flashcards.presentation.startup

/**
 * What the nav host reports about its back stack, so [AppStartViewModel] can decide whether a warm
 * shortcut tap may open its target without holding a `NavController`.
 */
data class AppNavigationState(
    val top: TopDestination = TopDestination.Launch,
    val isStudySessionInBackStack: Boolean = false,
)

/** The top screen, as far as a shortcut cares: a launch screen, one of its targets, or anything else. */
sealed interface TopDestination {
    /** Splash, Onboarding or Login; also the moment before the nav host shows anything. */
    data object Launch : TopDestination

    data class CategoryDetails(val categoryId: String) : TopDestination
    data class SubcategoryDetails(val subcategoryId: String) : TopDestination
    data object Other : TopDestination
}
