package com.rossomak.flashcards.feature.account

import com.rossomak.flashcards.core.domain.model.InstallationInfo
import com.rossomak.flashcards.core.ui.navigation.NavigationEvent

sealed interface AccountDestination : NavigationEvent {
    /** [uid] is the signed-in user's account id, `null` when signed out. */
    data class ContactSupport(val installationInfo: InstallationInfo, val uid: String?) : AccountDestination
}
