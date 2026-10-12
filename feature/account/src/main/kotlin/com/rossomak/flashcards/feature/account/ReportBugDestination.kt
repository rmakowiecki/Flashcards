package com.rossomak.flashcards.feature.account

import com.rossomak.flashcards.core.ui.navigation.NavigationEvent

sealed interface ReportBugDestination : NavigationEvent {
    data object Back : ReportBugDestination
}
