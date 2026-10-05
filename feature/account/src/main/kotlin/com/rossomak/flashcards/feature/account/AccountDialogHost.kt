package com.rossomak.flashcards.feature.account

import androidx.compose.runtime.Composable

@Suppress("UnusedParameter")
@Composable
internal fun AccountDialogHost(
    activeDialog: AccountDialog?,
    onDialogEvent: (AccountDialogEvent) -> Unit,
) {
    // No dialogs yet: each variant added to AccountDialog adds its branch here.
    if (activeDialog == null) return
}
