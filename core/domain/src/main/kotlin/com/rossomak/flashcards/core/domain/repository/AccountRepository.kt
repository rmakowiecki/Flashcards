package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.AccountDeletionResult

interface AccountRepository {

    /**
     * Deletes the signed-in User's account on the server, then resets the device's local state for that
     * User and restarts the app. Never throws except on cancellation. Needs a connection: a deletion is
     * never queued.
     */
    suspend fun deleteAccount(): AccountDeletionResult
}
