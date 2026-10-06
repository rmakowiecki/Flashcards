package com.rossomak.flashcards.feature.account

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardsDecisionDialog
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.feature.account.AccountDialog.SignOut

@Composable
internal fun AccountDialogHost(
    activeDialog: AccountDialog?,
    onDialogEvent: (AccountDialogEvent) -> Unit,
) {
    when (activeDialog) {
        null -> Unit
        // Cancel and back both discard, so the decision dialog's single onCancel is Dismiss.
        SignOut -> FlashcardsDecisionDialog(
            title = stringResource(R.string.account_sign_out_dialog_title),
            confirmLabel = stringResource(R.string.account_sign_out_button),
            onConfirm = { onDialogEvent(Confirm) },
            onCancel = { onDialogEvent(Dismiss) },
            icon = Icons.AutoMirrored.Filled.Logout,
            supportingText = stringResource(R.string.account_sign_out_dialog_message),
        )
    }
}
