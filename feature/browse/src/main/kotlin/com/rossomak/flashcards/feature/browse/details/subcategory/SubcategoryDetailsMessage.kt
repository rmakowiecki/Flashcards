package com.rossomak.flashcards.feature.browse.details.subcategory

/**
 * One-shot snackbar messages from Subcategory Details, per the SharedFlow-for-transient-events rule.
 * Never screen state — a message is shown once, not held.
 */
sealed interface SubcategoryDetailsMessage {

    data object AddedToFavorites : SubcategoryDetailsMessage

    data object RemovedFromFavorites : SubcategoryDetailsMessage

    /** [com.rossomak.flashcards.core.domain.model.PinShortcutResult.UnsupportedLauncher] — no OS placement dialog appeared. */
    data object ShortcutPinUnsupported : SubcategoryDetailsMessage

    /** [com.rossomak.flashcards.core.domain.model.PinShortcutResult.EntityNotFound] — the Subcategory/parent Category no longer resolves. */
    data object ShortcutPinFailed : SubcategoryDetailsMessage
}
