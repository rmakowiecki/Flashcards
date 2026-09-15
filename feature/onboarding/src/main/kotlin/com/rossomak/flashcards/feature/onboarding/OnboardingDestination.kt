package com.rossomak.flashcards.feature.onboarding

import com.rossomak.flashcards.core.ui.navigation.NavigationEvent

sealed interface OnboardingDestination : NavigationEvent {
    data object Main : OnboardingDestination
    data object Login : OnboardingDestination
}
