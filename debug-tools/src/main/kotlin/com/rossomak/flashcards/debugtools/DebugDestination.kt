package com.rossomak.flashcards.debugtools

import com.rossomak.flashcards.core.ui.navigation.NavigationEvent

sealed interface DebugDestination : NavigationEvent {

    /** Replays the onboarding flow. Owned here rather than by Settings, which ships in release. */
    data object Onboarding : DebugDestination
}
