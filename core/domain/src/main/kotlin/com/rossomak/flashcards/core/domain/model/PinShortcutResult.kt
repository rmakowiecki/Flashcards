package com.rossomak.flashcards.core.domain.model

/** Result of [PinCategoryShortcutUseCase]/[PinSubcategoryShortcutUseCase], distinguishing why a pin attempt didn't place a shortcut. */
sealed interface PinShortcutResult {

    data object Pinned : PinShortcutResult

    /** The Category/Subcategory (or its parent Category) no longer resolves — deleted server-side between page load and tap. */
    data object EntityNotFound : PinShortcutResult

    /** The launcher doesn't support `ShortcutManagerCompat.requestPinShortcut` — no OS placement dialog was shown. */
    data object UnsupportedLauncher : PinShortcutResult
}
