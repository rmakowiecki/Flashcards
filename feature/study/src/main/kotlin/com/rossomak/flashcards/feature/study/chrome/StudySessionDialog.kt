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
     *
     * @param isSubmitting the report is being sent; the dialog stays open until it is, with Submit
     * disabled.
     */
    data class ReportCurrentCardProblem(
        val cardId: String,
        val subcategoryId: String,
        val selectedActions: Set<CurationAction> = emptySet(),
        val isSubmitting: Boolean = false,
    ) : StudySessionDialog {
        val canSubmit: Boolean get() = selectedActions.isNotEmpty() && !isSubmitting

        fun withAction(action: CurationAction, isChecked: Boolean): ReportCurrentCardProblem = copy(
            selectedActions = if (isChecked) {
                selectedActions + action - setOfNotNull(action.difficultyOpposite())
            } else {
                selectedActions - action
            },
        )
    }

    data class CurrentCardExtendedContext(val text: String) : StudySessionDialog

    data class SessionVoiceSettings(val draftState: VoiceSettingsDraftState = VoiceSettingsDraftState(), val keepAsDefault: Boolean = false) : StudySessionDialog

    data object ExitSession : StudySessionDialog
}
