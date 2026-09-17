package com.rossomak.flashcards.feature.browse.details.subcategory

/**
 * One-shot snackbar messages from Subcategory Details, per the SharedFlow-for-transient-events rule.
 * Never screen state — a message is shown once, not held.
 */
sealed interface SubcategoryDetailsMessage {

    /** Both favourite messages are cosmetic: nothing is persisted. See [SubcategoryDetailsViewModel.onFavoriteToggle]. */
    data object AddedToFavorites : SubcategoryDetailsMessage

    data object RemovedFromFavorites : SubcategoryDetailsMessage

    /** [com.rossomak.flashcards.core.domain.usecase.PinSubcategoryShortcutUseCase] returned `false` — no OS placement dialog appeared. */
    data object ShortcutPinUnsupported : SubcategoryDetailsMessage
}
