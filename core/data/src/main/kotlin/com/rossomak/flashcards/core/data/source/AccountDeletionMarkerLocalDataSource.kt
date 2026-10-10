package com.rossomak.flashcards.core.data.source

/**
 * Remembers the uid of an Account Deletion that started on this device and has not settled yet, so the
 * next app start can sign out a User the server may already have deleted. See
 * [com.rossomak.flashcards.core.data.InterruptedAccountDeletionCompleter].
 *
 * Synchronous, since the app-start check reads it before anything else runs, and best effort: a failed
 * read, write or clear is logged and never throws.
 */
interface AccountDeletionMarkerLocalDataSource {

    /** The marked uid, or null when no deletion is marked or the marker cannot be read. */
    fun read(): String?

    /** Marks [uid]; false when the marker could not be saved. */
    fun write(uid: String): Boolean

    fun clear()
}
