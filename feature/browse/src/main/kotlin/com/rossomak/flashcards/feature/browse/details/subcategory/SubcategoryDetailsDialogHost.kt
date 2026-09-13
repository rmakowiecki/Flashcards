package com.rossomak.flashcards.feature.browse.details.subcategory

import androidx.compose.runtime.Composable
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardFiltersDialog
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardSortOrderDialog
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.DraftChange

/**
 * Renders whichever dialog this screen has open (ADR-0036).
 *
 * The `when` has already narrowed to a concrete case, so each branch emits a total `copy()` — no
 * per-field event type, and no cast. The host holds no state of its own: the draft lives in
 * [SubcategoryDetailsDialog] so dismissing discards it for free.
 */
@Composable
fun SubcategoryDetailsDialogHost(
    activeDialog: SubcategoryDetailsDialog?,
    onDialogEvent: (SubcategoryDetailsDialogEvent) -> Unit,
) {
    val onConfirm = { onDialogEvent(Confirm) }
    val onDismiss = { onDialogEvent(Dismiss) }

    when (activeDialog) {
        null -> Unit
        is SubcategoryDetailsDialog.Sort -> FlashcardSortOrderDialog(
            draft = activeDialog.draftState,
            onDraftChange = { onDialogEvent(DraftChange(activeDialog.copy(draftState = it))) },
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            keepAsDefault = activeDialog.keepAsDefault,
            onKeepAsDefaultChange = { onDialogEvent(DraftChange(activeDialog.copy(keepAsDefault = it))) },
        )
        is SubcategoryDetailsDialog.Filters -> FlashcardFiltersDialog(
            availableTags = activeDialog.availableTags,
            filters = activeDialog.draftState,
            difficultyBounds = activeDialog.difficultyBounds,
            onFiltersChange = { onDialogEvent(DraftChange(activeDialog.copy(draftState = it))) },
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}
