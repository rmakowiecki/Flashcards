package com.rossomak.flashcards.feature.account

import com.rossomak.flashcards.core.ui.dialog.DialogEvent

typealias AccountDialogEvent = DialogEvent<AccountDialog>

/** Every dialog the Account screen can show. Each Account feature adds its own variant. */
sealed interface AccountDialog {

    data object SignOut : AccountDialog
}
