package com.rossomak.flashcards.feature.settings

sealed interface SettingsMessage {
    data object SaveFailed : SettingsMessage
}
