package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.ShortcutTarget

/**
 * Wraps `ShortcutManagerCompat` for pinning and syncing this app's launcher shortcuts. See
 * [ShortcutTarget] for the shortcut id scheme, and [ACTION_OPEN_ROUTE]/[EXTRA_ROUTE] for the
 * Intent contract a shortcut is built with and that app startup routing consumes.
 */
interface AppShortcutsRepository {
    /**
     * Requests the launcher pin [target] as a shortcut. Returns whether the launcher supports
     * pinning (`ShortcutManagerCompat.isRequestPinShortcutSupported`) — `false` means no placement
     * dialog was shown, and the caller must surface that to the user some other way.
     */
    suspend fun pinShortcut(target: ShortcutTarget): Boolean

    /** Replaces the app's dynamic shortcuts with one shortcut per entry in [favorites]. */
    suspend fun syncDynamicShortcuts(favorites: List<ShortcutTarget>)

    companion object {
        /** Intent action a shortcut is built with, and that app-startup route handling listens for. */
        const val ACTION_OPEN_ROUTE = "com.rossomak.flashcards.action.OPEN_ROUTE"

        /** String extra key carrying the [ShortcutTarget.route] a shortcut's Intent should open. */
        const val EXTRA_ROUTE = "com.rossomak.flashcards.extra.ROUTE"
    }
}
