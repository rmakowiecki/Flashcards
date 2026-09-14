package com.rossomak.flashcards.feature.settings

import com.rossomak.flashcards.core.domain.model.FlashcardSortOrder
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.ui.dialog.DialogEvent
import com.rossomak.flashcards.core.ui.voice.VoiceSettingsDraftState

typealias SettingsDialogEvent = DialogEvent<SettingsDialog>

sealed interface SettingsDialog {

    data class SessionMode(val draftState: StudyMode) : SettingsDialog

    data class SessionCardCount(val draftState: Int) : SettingsDialog

    data class DailyStudyGoal(val draftState: Int) : SettingsDialog

    data class RatedSessionMaxCardAttempts(val draftState: Int) : SettingsDialog

    /**`draftState = true` (the default) re-queues a Partial-rated Rated mode card; `false` finishes it on the spot, recording Terminal Partial rather than Mastered (ADR-0044) */
    data class RatedSessionPartialRatingCardRequeueing(val draftState: Boolean) : SettingsDialog

    data class SessionCardsSortingOrder(val draftState: FlashcardSortOrder) : SettingsDialog

    /** Quick Session only (regardless of Fast/Rated mode) — the row that opens it is not offered for a single-Subcategory or Custom session (ADR-0040) */
    data class QuickSessionSubcategoryCountRange(val draftState: IntRange) : SettingsDialog

    data class RatedSessionVoiceAnswering(val draftState: Boolean) : SettingsDialog

    data class FastSessionReadAloud(val draftState: Boolean) : SettingsDialog

    /**
     * Offered for Fast mode, or Rated with voice answering on — the same gate the summary row
     * itself uses (ADR-0030). The draft comes from
     * [VoiceSettingsController][com.rossomak.flashcards.core.ui.voice.VoiceSettingsController]'s
     * voice cache plus this session's current settings, neither of which the row has, so the ViewModel always replaces what it is handed here.
     */
    data class SessionVoiceSettings(val draftState: VoiceSettingsDraftState = VoiceSettingsDraftState()) : SettingsDialog

    data object SignOut : SettingsDialog
}
