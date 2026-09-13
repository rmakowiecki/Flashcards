package com.rossomak.flashcards.feature.browse.details.category

/**
 * One-shot snackbar messages from Category Details, per the SharedFlow-for-transient-events rule.
 * Never screen state — a message is shown once, not held.
 */
sealed interface CategoryDetailsMessage {

    /** Both favourite messages are cosmetic: nothing is persisted. See [CategoryDetailsViewModel.onFavoriteToggle]. */
    data object AddedToFavorites : CategoryDetailsMessage

    data object RemovedFromFavorites : CategoryDetailsMessage
}
