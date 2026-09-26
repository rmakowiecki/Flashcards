package com.rossomak.flashcards.feature.browse.details

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
import com.rossomak.flashcards.feature.browse.R
import com.rossomak.flashcards.feature.browse.details.DetailsMessage.AddedToFavorites
import com.rossomak.flashcards.feature.browse.details.DetailsMessage.RemovedFromFavorites
import com.rossomak.flashcards.feature.browse.details.DetailsMessage.ShortcutPinFailed
import com.rossomak.flashcards.feature.browse.details.DetailsMessage.ShortcutPinUnsupported
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Shows each [DetailsMessage] as a snackbar on [snackbarHostState]. A favorite message carries an
 * Undo action that reports back through [onFavoriteUndo] with the value the toggle moved away from.
 */
@Composable
internal fun DetailsMessagesEffect(
    messages: Flow<DetailsMessage>,
    snackbarHostState: SnackbarHostState,
    onFavoriteUndo: (restoreTo: Boolean) -> Unit,
) {
    val addedToFavoritesText = stringResource(R.string.favorites_added_message)
    val removedFromFavoritesText = stringResource(R.string.favorites_removed_message)
    val undoLabelText = stringResource(R.string.favorites_undo_button)
    val shortcutPinUnsupportedText = stringResource(R.string.shortcut_pin_unsupported_message)
    val shortcutPinFailedText = stringResource(R.string.shortcut_pin_failed_message)

    // showSnackbar suspends until the snackbar is dismissed, and observeAsEvents hands over a plain
    // lambda, so the wait is launched rather than blocking the collector.
    val snackbarScope = rememberCoroutineScope()
    observeAsEvents(messages) { message ->
        snackbarScope.launch {
            when (message) {
                AddedToFavorites, RemovedFromFavorites -> {
                    val result = snackbarHostState.showSnackbar(
                        message = if (message == AddedToFavorites) addedToFavoritesText else removedFromFavoritesText,
                        actionLabel = undoLabelText,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        onFavoriteUndo(message != AddedToFavorites)
                    }
                }
                ShortcutPinUnsupported -> snackbarHostState.showSnackbar(message = shortcutPinUnsupportedText, duration = SnackbarDuration.Short)
                ShortcutPinFailed -> snackbarHostState.showSnackbar(message = shortcutPinFailedText, duration = SnackbarDuration.Short)
            }
        }
    }
}
