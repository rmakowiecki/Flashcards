package com.rossomak.flashcards.feature.account

import com.rossomak.flashcards.core.ui.navigation.NavigationEvent

sealed interface AccountDestination : NavigationEvent {
    data object Login : AccountDestination
}
