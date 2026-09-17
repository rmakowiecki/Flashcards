package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.ShortcutTarget

class FakeAppShortcutsRepository : AppShortcutsRepository {
    var pinShortcutResult: Boolean = true

    /** Every target [pinShortcut] was called with, in call order. */
    val pinnedTargets: MutableList<ShortcutTarget> = mutableListOf()

    /** Every [syncDynamicShortcuts] call's favorites list, in call order. */
    val syncedFavorites: MutableList<List<ShortcutTarget>> = mutableListOf()

    override suspend fun pinShortcut(target: ShortcutTarget): Boolean {
        pinnedTargets += target
        return pinShortcutResult
    }

    override suspend fun syncDynamicShortcuts(favorites: List<ShortcutTarget>) {
        syncedFavorites += favorites
    }
}
