package com.rossomak.flashcards.feature.browse.details.subcategory

import com.rossomak.flashcards.core.domain.model.FlashcardSortOrder
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardFilters
import com.rossomak.flashcards.core.ui.dialog.DialogEvent

/** The event type this screen's dialogs report through. See [DialogEvent]. */
typealias SubcategoryDetailsDialogEvent = DialogEvent<SubcategoryDetailsDialog>

/**
 * Which dialog Subcategory Details currently has open, and everything that dialog needs (ADR-0036).
 *
 * One sealed nullable field rather than a flag per dialog: two dialogs open at once becomes
 * unrepresentable, the call site is a single exhaustive `when`, and discard on dismiss is free —
 * the draft dies with the field.
 */
sealed interface SubcategoryDetailsDialog {

    /**
     * @param keepAsDefault promotes this session-scoped choice to the stored
     * [StudySessionPreference.SortOrder][com.rossomak.flashcards.core.domain.model.StudySessionPreference.SortOrder].
     * Offered here because browsing and a Study Session share one notion of order (ADR-0038), so
     * this screen is a peer of the Preview screen rather than a separate setting.
     */
    data class Sort(
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
