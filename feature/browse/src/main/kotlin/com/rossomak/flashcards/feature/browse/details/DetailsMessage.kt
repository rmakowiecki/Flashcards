package com.rossomak.flashcards.feature.browse.details

/**
 * One-shot snackbar messages from Category Details and Subcategory Details, per the
 * SharedFlow-for-transient-events rule. Never screen state — a message is shown once, not held.
 * Both screens raise the same favorite and shortcut outcomes, so they share one type and one
 * [DetailsMessagesEffect].
 */
sealed interface DetailsMessage {

    data object AddedToFavorites : DetailsMessage

    data object RemovedFromFavorites : DetailsMessage

    /** [com.rossomak.flashcards.core.domain.model.PinShortcutResult.UnsupportedLauncher] — no OS placement dialog appeared. */
    data object ShortcutPinUnsupported : DetailsMessage

    /** [com.rossomak.flashcards.core.domain.model.PinShortcutResult.EntityResolutionError] — the Category or Subcategory no longer resolves. */
    data object ShortcutPinFailed : DetailsMessage
}
