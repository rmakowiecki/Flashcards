package com.rossomak.flashcards.feature.browse.details.subcategory

import com.rossomak.flashcards.core.domain.model.FlashcardSortOrder
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardFilters
import com.rossomak.flashcards.core.ui.dialog.DialogEvent

typealias SubcategoryDetailsDialogEvent = DialogEvent<SubcategoryDetailsDialog>

sealed interface SubcategoryDetailsDialog {

    /**
     * @param keepAsDefault promotes this session-scoped choice to the stored
     * [StudySessionPreference.SortOrder][com.rossomak.flashcards.core.domain.model.StudySessionPreference.SortOrder].
     * Offered here because browsing and a Study Session share one notion of order (ADR-0038), so
     * this screen is a peer of the Preview screen rather than a separate setting.
     */
    data class CardsSortingOrder(
        val draftState: FlashcardSortOrder,
        val keepAsDefault: Boolean = false,
    ) : SubcategoryDetailsDialog

    /**
     * No `keepAsDefault`: tags belong to one Subcategory and cannot carry to another, so filters are
     * session-scoped by definition (ADR-0030).
     *
     * @param availableTags the pool's tag vocabulary, carried on the dialog so the host needs
     * nothing but the open dialog and the callback.
     * @param difficultyBounds the selectable range, carried here for the same reason.
     */
    data class Filters(
        val draftState: FlashcardFilters,
        val availableTags: List<String>,
        val difficultyBounds: IntRange = SubcategoryDetailsScreenState.DIFFICULTY_BOUNDS,
    ) : SubcategoryDetailsDialog
}
