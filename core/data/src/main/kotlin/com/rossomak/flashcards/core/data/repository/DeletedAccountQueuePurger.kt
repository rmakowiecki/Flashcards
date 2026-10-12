package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.data.source.DeadLetteredSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.PendingSessionSubmissionLocalDataSource
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Removes a deleted User's Pending Sessions and dead letters, after a deletion that completed or one a
 * process death interrupted. Never throws: entries it cannot remove belong to a uid no one can sign in
 * as again, so a failure is only logged. Each store is purged on its own, so one failing store never
 * keeps the other's entries.
 */
class DeletedAccountQueuePurger @Inject constructor(
    private val pendingSessionSubmissionLocalDataSource: PendingSessionSubmissionLocalDataSource,
    private val deadLetteredSessionSubmissionLocalDataSource: DeadLetteredSessionSubmissionLocalDataSource,
) {

    suspend fun purge(uid: String) {
        purgeStore("Pending Sessions") { pendingSessionSubmissionLocalDataSource.removeAllForUser(uid) }
        purgeStore("dead letters") { deadLetteredSessionSubmissionLocalDataSource.removeAllForUser(uid) }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun purgeStore(storeName: String, removeAllForUser: suspend () -> Unit) {
        try {
            removeAllForUser()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            loge(exception) { "Could not remove the deleted account's $storeName" }
        }
    }
}
