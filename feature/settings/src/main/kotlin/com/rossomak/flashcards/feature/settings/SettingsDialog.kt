package com.rossomak.flashcards.feature.settings

import com.rossomak.flashcards.core.domain.model.FlashcardSortOrder
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.ui.dialog.DialogEvent
import com.rossomak.flashcards.core.ui.voice.VoiceSettingsDraftState

typealias SettingsDialogEvent = DialogEvent<SettingsDialog>

sealed interface SettingsDialog {

    data class Length(val draftState: Int) : SettingsDialog

    data class Goal(val draftState: Int) : SettingsDialog

    data class Attempts(val draftState: Int) : SettingsDialog

    /**`draftState = true` (the default) re-queues a Partial-rated Rated mode card; `false` finishes it on the spot, recording Terminal Partial rather than Mastered (ADR-0044) */
    data class PartialRatingCardRequeueing(val draftState: Boolean) : SettingsDialog

    data class Mode(val draftState: StudyMode) : SettingsDialog

    data class Sort(val draftState: FlashcardSortOrder) : SettingsDialog

    data class SubcategoryCountRange(val draftState: IntRange) : SettingsDialog

    data class VoiceAnswering(val draftState: Boolean) : SettingsDialog

    data class ReadAloud(val draftState: Boolean) : SettingsDialog

    /**
     * The draft lives here like every other dialog's, but is the one this screen cannot seed at the call site: it comes from
     * [VoiceSettingsController][com.rossomak.flashcards.core.ui.voice.VoiceSettingsController]'s
     * saved settings and voice cache, which the row does not have. The ViewModel always replaces
     * what it is handed, so the default here is a placeholder, never a value in use.
     */
    data class VoiceSettings(val draftState: VoiceSettingsDraftState = VoiceSettingsDraftState()) : SettingsDialog

    data object SignOut : SettingsDialog
}
