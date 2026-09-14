package com.rossomak.flashcards.feature.study.chrome

import com.rossomak.flashcards.core.domain.model.CurationAction
import com.rossomak.flashcards.core.ui.dialog.DialogEvent
import com.rossomak.flashcards.core.ui.voice.VoiceSettingsDraftState

typealias StudySessionDialogEvent = DialogEvent<StudySessionDialog>

sealed interface StudySessionDialog {

    /**
     * The report draft. Always starts empty: this files a fresh report rather than editing the
     * card's previous one, so an unchecked row is never ambiguous between "not a problem" and
     * "already reported" (ADR-0017).
     */
    data class ReportProblem(
        val cardId: String,
        val subcategoryId: String,
        val selectedActions: Set<CurationAction> = emptySet(),
    ) : StudySessionDialog {
        val canSubmit: Boolean get() = selectedActions.isNotEmpty()

        fun withAction(action: CurationAction, isChecked: Boolean): ReportProblem = copy(
            selectedActions = if (isChecked) {
                selectedActions + action - setOfNotNull(action.difficultyOpposite())
            } else {
                selectedActions - action
            },
        )
    }

    data class ExtendedContext(val text: String) : StudySessionDialog

    data object VoiceAnswerConsent : StudySessionDialog

    data class VoiceSettings(
        val draftState: VoiceSettingsDraftState = VoiceSettingsDraftState(),
        val keepAsDefault: Boolean = false,
    ) : StudySessionDialog

    data object ExitSession : StudySessionDialog
}
