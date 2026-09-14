package com.rossomak.flashcards.feature.study.preview

import com.rossomak.flashcards.core.domain.model.FlashcardSortOrder
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardFilters
import com.rossomak.flashcards.core.ui.dialog.DialogEvent
import com.rossomak.flashcards.core.ui.voice.VoiceSettingsDraftState

typealias PreviewDialogEvent = DialogEvent<PreviewDialog>

sealed interface PreviewDialog {

    data class SessionMode(val draftState: StudyMode, val keepAsDefault: Boolean = false) : PreviewDialog

    data class SessionCardCount(val draftState: Int, val keepAsDefault: Boolean = false) : PreviewDialog

    data class RatedSessionMaxCardAttempts(val draftState: Int, val keepAsDefault: Boolean = false) : PreviewDialog

    /**`draftState = true` (the default) re-queues a Partial-rated Rated mode card; `false` finishes it on the spot, recording Terminal Partial rather than Mastered (ADR-0044) */
    data class RatedSessionPartialRatingCardRequeueing(val draftState: Boolean, val keepAsDefault: Boolean = false) : PreviewDialog

    data class RatedSessionVoiceAnswering(val draftState: Boolean, val keepAsDefault: Boolean = false) : PreviewDialog

    data class FastSessionReadAloud(val draftState: Boolean, val keepAsDefault: Boolean = false) : PreviewDialog

    data class SessionCardsSortingOrder(val draftState: FlashcardSortOrder, val keepAsDefault: Boolean = false) : PreviewDialog

    /** Quick Session only (regardless of Fast/Rated mode) — the row that opens it is not offered for a single-Subcategory or Custom session (ADR-0040) */
    data class QuickSessionSubcategoryCountRange(val draftState: IntRange, val keepAsDefault: Boolean = false) : PreviewDialog

    /**
     * Offered for Fast mode, or Rated with voice answering on — the same gate the summary row
     * itself uses (ADR-0030). The draft comes from
     * [VoiceSettingsController][com.rossomak.flashcards.core.ui.voice.VoiceSettingsController]'s
     * voice cache plus this session's current settings, neither of which the row has, so the ViewModel always replaces what it is handed here.
     */
    data class SessionVoiceSettings(val draftState: VoiceSettingsDraftState = VoiceSettingsDraftState(), val keepAsDefault: Boolean = false, ) : PreviewDialog

    /**
     * No `keepAsDefault` option: tags belong to one subcategory and cannot carry to another, so filters are session-scoped by definition (ADR-0030).
     * [availableTags] is the pool's tag vocabulary
     */
    data class Filters(val draftState: FlashcardFilters, val availableTags: List<String>) : PreviewDialog
}
