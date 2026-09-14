package com.rossomak.flashcards.feature.study.chrome

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardsDecisionDialog
import com.rossomak.flashcards.core.ui.composables.dialogs.VoiceSettingsDialog
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.DraftChange
import com.rossomak.flashcards.feature.study.R
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.ExitSession
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.CurrentCardExtendedContext
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.ReportCurrentCardProblem
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.VoiceAnswerConsent
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.SessionVoiceSettings
import com.rossomak.flashcards.feature.study.dialogs.ExtendedContextDialog
import com.rossomak.flashcards.feature.study.dialogs.ReportProblemDialog

@Composable
internal fun StudySessionDialogHost(
    activeDialog: StudySessionDialog?,
    onDialogEvent: (StudySessionDialogEvent) -> Unit,
) {
    val onConfirm = { onDialogEvent(Confirm) }
    val onDismiss = { onDialogEvent(Dismiss) }

    when (activeDialog) {
        null -> Unit
        is ReportCurrentCardProblem -> ReportProblemDialog(
            selectedActions = activeDialog.selectedActions,
            onActionCheckedChange = { action, isChecked ->
                onDialogEvent(DraftChange(activeDialog.withAction(action, isChecked)))
            },
            onSubmit = onConfirm,
            onCancel = onDismiss,
        )
        is CurrentCardExtendedContext -> ExtendedContextDialog(extendedContext = activeDialog.text, onDismiss = onDismiss)
        VoiceAnswerConsent -> FlashcardsDecisionDialog(
            title = stringResource(R.string.study_session_voice_answer_consent_title),
            confirmLabel = stringResource(R.string.study_session_voice_answer_consent_accept_button),
            onConfirm = onConfirm,
            onCancel = onDismiss,
            supportingText = stringResource(R.string.study_session_voice_answer_consent_message),
            cancelLabel = stringResource(R.string.study_session_voice_answer_consent_decline_button),
        )
        is SessionVoiceSettings -> VoiceSettingsDialog(
            availableVoices = activeDialog.draftState.availableVoices,
            draftVoiceId = activeDialog.draftState.draftVoiceId,
            onDraftVoiceChange = {
                onDialogEvent(DraftChange(activeDialog.copy(draftState = activeDialog.draftState.copy(draftVoiceId = it))))
            },
            draftSpeechRate = activeDialog.draftState.draftSpeed,
            onDraftSpeechRateChange = {
                onDialogEvent(DraftChange(activeDialog.copy(draftState = activeDialog.draftState.copy(draftSpeed = it))))
            },
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            keepAsDefault = activeDialog.keepAsDefault,
            onKeepAsDefaultChange = { onDialogEvent(DraftChange(activeDialog.copy(keepAsDefault = it))) },
        )
        ExitSession -> FlashcardsDecisionDialog(
            title = stringResource(R.string.exit_session_dialog_title),
            confirmLabel = stringResource(R.string.exit_session_confirm_button),
            onConfirm = onConfirm,
            onCancel = onDismiss,
            supportingText = stringResource(R.string.exit_session_dialog_message),
            cancelLabel = stringResource(CoreUiR.string.common_cancel_button),
        )
    }
}
