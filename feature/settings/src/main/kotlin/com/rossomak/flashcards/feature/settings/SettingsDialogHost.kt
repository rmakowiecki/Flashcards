package com.rossomak.flashcards.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rossomak.flashcards.core.domain.model.StudySessionConfig
import com.rossomak.flashcards.core.domain.model.StudySessionConfig.Companion.LENGTH_STEP
import com.rossomak.flashcards.core.domain.model.StudySessionConfig.Companion.RATED_ATTEMPTS_STEP
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardSortOrderDialog
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardsDecisionDialog
import com.rossomak.flashcards.core.ui.composables.dialogs.PartialRatingCardRequeueingDialog
import com.rossomak.flashcards.core.ui.composables.dialogs.RatedAttemptsDialog
import com.rossomak.flashcards.core.ui.composables.dialogs.ReadAloudDialog
import com.rossomak.flashcards.core.ui.composables.dialogs.SessionLengthDialog
import com.rossomak.flashcards.core.ui.composables.dialogs.StudyModeDialog
import com.rossomak.flashcards.core.ui.composables.dialogs.SubcategoryCountRangeDialog
import com.rossomak.flashcards.core.ui.composables.dialogs.VoiceAnsweringDialog
import com.rossomak.flashcards.core.ui.composables.dialogs.VoiceSettingsDialog
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.DraftChange
import com.rossomak.flashcards.feature.settings.SettingsDialog.DailyStudyGoal
import com.rossomak.flashcards.feature.settings.SettingsDialog.FastSessionReadAloud
import com.rossomak.flashcards.feature.settings.SettingsDialog.QuickSessionSubcategoryCountRange
import com.rossomak.flashcards.feature.settings.SettingsDialog.RatedSessionMaxCardAttempts
import com.rossomak.flashcards.feature.settings.SettingsDialog.RatedSessionPartialRatingCardRequeueing
import com.rossomak.flashcards.feature.settings.SettingsDialog.RatedSessionVoiceAnswering
import com.rossomak.flashcards.feature.settings.SettingsDialog.SessionCardCount
import com.rossomak.flashcards.feature.settings.SettingsDialog.SessionCardsSortingOrder
import com.rossomak.flashcards.feature.settings.SettingsDialog.SessionMode
import com.rossomak.flashcards.feature.settings.SettingsDialog.SessionVoiceSettings
import com.rossomak.flashcards.feature.settings.SettingsDialog.SignOut

private val SESSION_CARD_COUNT_RANGE = StudySessionConfig.MIN_LENGTH..StudySessionConfig.MAX_LENGTH
private val RATED_ATTEMPTS_RANGE = StudySessionConfig.MIN_RATED_ATTEMPTS..StudySessionConfig.MAX_RATED_ATTEMPTS
private val SUBCATEGORY_COUNT_RANGE =
    StudySessionConfig.MIN_SUBCATEGORY_COUNT..StudySessionConfig.MAX_SUBCATEGORY_COUNT

@Suppress("LongMethod")
@Composable
internal fun SettingsDialogHost(
    activeDialog: SettingsDialog?,
    onDialogEvent: (SettingsDialogEvent) -> Unit,
) {
    val onConfirm = { onDialogEvent(Confirm) }
    val onDismiss = { onDialogEvent(Dismiss) }

    when (activeDialog) {
        null -> Unit
        is SessionCardCount -> SessionLengthDialog(
            draft = activeDialog.draftState,
            range = SESSION_CARD_COUNT_RANGE,
            step = LENGTH_STEP,
            onDraftChange = { onDialogEvent(DraftChange(activeDialog.copy(draftState = it))) },
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
        is DailyStudyGoal -> DailyGoalDialog(
            draft = activeDialog.draftState,
            onDraftChange = { onDialogEvent(DraftChange(activeDialog.copy(draftState = it))) },
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
        is RatedSessionMaxCardAttempts -> RatedAttemptsDialog(
            draft = activeDialog.draftState,
            range = RATED_ATTEMPTS_RANGE,
            step = RATED_ATTEMPTS_STEP,
            onDraftChange = { onDialogEvent(DraftChange(activeDialog.copy(draftState = it))) },
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
        is RatedSessionPartialRatingCardRequeueing -> PartialRatingCardRequeueingDialog(
            draft = activeDialog.draftState,
            onDraftChange = { onDialogEvent(DraftChange(activeDialog.copy(draftState = it))) },
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
        is SessionMode -> StudyModeDialog(
            draft = activeDialog.draftState,
            onDraftChange = { onDialogEvent(DraftChange(activeDialog.copy(draftState = it))) },
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
        is SessionCardsSortingOrder -> FlashcardSortOrderDialog(
            draft = activeDialog.draftState,
            onDraftChange = { onDialogEvent(DraftChange(activeDialog.copy(draftState = it))) },
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
        is QuickSessionSubcategoryCountRange -> SubcategoryCountRangeDialog(
            draft = activeDialog.draftState,
            bounds = SUBCATEGORY_COUNT_RANGE,
            onDraftChange = { onDialogEvent(DraftChange(activeDialog.copy(draftState = it))) },
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
        is RatedSessionVoiceAnswering -> VoiceAnsweringDialog(
            draft = activeDialog.draftState,
            onDraftChange = { onDialogEvent(DraftChange(activeDialog.copy(draftState = it))) },
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
        is FastSessionReadAloud -> ReadAloudDialog(
            draft = activeDialog.draftState,
            onDraftChange = { onDialogEvent(DraftChange(activeDialog.copy(draftState = it))) },
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
        is SessionVoiceSettings -> VoiceSettingsDialog(
            availableVoices = activeDialog.draftState.availableVoices,
            draftVoiceId = activeDialog.draftState.draftVoiceId,
            seededVoiceLabel = activeDialog.draftState.seededVoiceLabel,
            onDraftVoiceChange = {
                onDialogEvent(DraftChange(activeDialog.copy(draftState = activeDialog.draftState.copy(draftVoiceId = it))))
            },
            draftSpeechRate = activeDialog.draftState.draftSpeed,
            onDraftSpeechRateChange = {
                onDialogEvent(DraftChange(activeDialog.copy(draftState = activeDialog.draftState.copy(draftSpeed = it))))
            },
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
        // Cancel and back both discard, so the decision dialog's single onCancel is Dismiss.
        SignOut -> FlashcardsDecisionDialog(
            title = stringResource(R.string.settings_sign_out_dialog_title),
            confirmLabel = stringResource(R.string.settings_sign_out_button),
            onConfirm = onConfirm,
            onCancel = onDismiss,
            icon = Icons.AutoMirrored.Filled.Logout,
            supportingText = stringResource(R.string.settings_sign_out_dialog_message),
        )
    }
}
