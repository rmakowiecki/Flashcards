package com.rossomak.flashcards.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.spacing

/**
 * A small card with a spinner over the dimmed screen while the account is deleted. It is a dialog
 * window that neither back nor a tap outside dismisses, so the screen behind it, the top bar's back
 * arrow and Sign out included, takes no input and no screen reader focus.
 */
@Composable
internal fun AccountDeletionOverlay() {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(
            shape = RoundedCornerShape(MaterialTheme.cornerRadius.large),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.normal),
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.account_deleting_account_message),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun AccountDeletionOverlayPreview() {
    FlashcardsTheme {
        AccountDeletionOverlay()
    }
}
