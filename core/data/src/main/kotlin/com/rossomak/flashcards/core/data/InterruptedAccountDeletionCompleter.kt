package com.rossomak.flashcards.core.data

import com.rossomak.flashcards.core.common.logi
import com.rossomak.flashcards.core.data.di.ApplicationScope
import com.rossomak.flashcards.core.data.repository.DeletedAccountQueuePurger
import com.rossomak.flashcards.core.data.source.AccountDeletionMarkerLocalDataSource
import com.rossomak.flashcards.core.data.source.AuthRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Finishes on the device an Account Deletion that a process death interrupted, as recorded by
 * [AccountDeletionMarkerLocalDataSource]. The server keeps deleting after the device dies, so the marked
 * User is treated as deleted without asking it: if the deletion actually failed, they sign in again.
 *
 * [complete] runs once per process, from [com.rossomak.flashcards.FlashcardsApplication], before
 * [SignedInWorkRunner.start]: the signed-out state is in place before a drain is scheduled for the
 * deleted User and before the start screen is chosen. It signs out only when the marked uid is the one
 * signed in. The queue purge and the marker clear run in the background; a process death before they
 * finish repeats them at the next start.
 */
@Singleton
class InterruptedAccountDeletionCompleter @Inject constructor(
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val accountDeletionMarkerLocalDataSource: AccountDeletionMarkerLocalDataSource,
    private val authRemoteDataSource: AuthRemoteDataSource,
    private val deletedAccountQueuePurger: DeletedAccountQueuePurger,
) {

    fun complete() {
        val uid = accountDeletionMarkerLocalDataSource.read() ?: return
        if (authRemoteDataSource.getCurrentUser()?.uid == uid) {
            logi { "Signing out an account whose deletion was interrupted" }
            authRemoteDataSource.signOut()
        }
        applicationScope.launch {
            deletedAccountQueuePurger.purge(uid)
            accountDeletionMarkerLocalDataSource.clear()
        }
    }
}
