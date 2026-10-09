package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.common.logd
import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.common.logi
import com.rossomak.flashcards.core.data.network.NetworkAvailability
import com.rossomak.flashcards.core.data.source.AccountDeletionMarkerLocalDataSource
import com.rossomak.flashcards.core.data.source.AccountDeletionRemoteDataSource
import com.rossomak.flashcards.core.data.source.AuthRemoteDataSource
import com.rossomak.flashcards.core.domain.model.AccountDeletionFailureReason.NoConnection
import com.rossomak.flashcards.core.domain.model.AccountDeletionFailureReason.ServiceError
import com.rossomak.flashcards.core.domain.model.AccountDeletionResult
import com.rossomak.flashcards.core.domain.model.AccountDeletionResult.Deleted
import com.rossomak.flashcards.core.domain.model.AccountDeletionResult.Failed
import com.rossomak.flashcards.core.domain.repository.AccountRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Deletes the account through the `deleteAccount` callable. Nothing local is touched before the server
 * confirms the account is gone. Then the deleted User's Pending Sessions and dead letters are removed
 * and the device signs out, as an ordinary sign-out would. See ADR-0060 for the cases this leaves out.
 *
 * The uid is marked in [AccountDeletionMarkerLocalDataSource] while the call runs, so a process death
 * mid-deletion is finished at the next start by
 * [com.rossomak.flashcards.core.data.InterruptedAccountDeletionCompleter]. A cancelled call keeps the
 * marker, since the server may still delete the account.
 */
class DefaultAccountRepository @Inject constructor(
    private val accountDeletionRemoteDataSource: AccountDeletionRemoteDataSource,
    private val authRemoteDataSource: AuthRemoteDataSource,
    private val accountDeletionMarkerLocalDataSource: AccountDeletionMarkerLocalDataSource,
    private val deletedAccountQueuePurger: DeletedAccountQueuePurger,
    private val networkAvailability: NetworkAvailability,
) : AccountRepository {

    override suspend fun deleteAccount(): AccountDeletionResult {
        val uid = authRemoteDataSource.getCurrentUser()?.uid ?: run {
            loge { "Account deletion not started: nobody is signed in" }
            return Failed(ServiceError)
        }
        if (!networkAvailability.isInternetAvailable()) {
            logd { "Account deletion not started: no internet" }
            return Failed(NoConnection)
        }
        withContext(Dispatchers.IO) { accountDeletionMarkerLocalDataSource.write(uid) }
        return accountDeletionRemoteDataSource.deleteAccount().fold(
            onSuccess = {
                logi { "Account deleted on the server, signing out" }
                completeOnDevice(uid)
                Deleted
            },
            onFailure = { exception ->
                loge(exception) { "Account deletion failed" }
                withContext(Dispatchers.IO) { accountDeletionMarkerLocalDataSource.clear() }
                Failed(exception.toFailureReason(NoConnection, ServiceError))
            },
        )
    }

    // The account is already gone, so the device steps must finish even if the caller is cancelled.
    private suspend fun completeOnDevice(uid: String) = withContext(NonCancellable) {
        deletedAccountQueuePurger.purge(uid)
        authRemoteDataSource.signOut()
        withContext(Dispatchers.IO) { accountDeletionMarkerLocalDataSource.clear() }
    }
}
