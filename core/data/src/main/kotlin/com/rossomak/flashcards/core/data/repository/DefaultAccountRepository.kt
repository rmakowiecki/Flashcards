package com.rossomak.flashcards.core.data.repository

import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.FirebaseFunctionsException.Code.DEADLINE_EXCEEDED
import com.google.firebase.functions.FirebaseFunctionsException.Code.UNAVAILABLE
import com.rossomak.flashcards.core.common.isConnectionFailure
import com.rossomak.flashcards.core.common.logd
import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.common.logi
import com.rossomak.flashcards.core.data.source.AccountDeletionMarkerLocalDataSource
import com.rossomak.flashcards.core.data.source.AccountDeletionRemoteDataSource
import com.rossomak.flashcards.core.data.source.AuthRemoteDataSource
import com.rossomak.flashcards.core.domain.model.AccountDeletionFailureReason.NoConnection
import com.rossomak.flashcards.core.domain.model.AccountDeletionFailureReason.ServiceError
import com.rossomak.flashcards.core.domain.model.AccountDeletionResult
import com.rossomak.flashcards.core.domain.model.AccountDeletionResult.Deleted
import com.rossomak.flashcards.core.domain.model.AccountDeletionResult.Failed
import com.rossomak.flashcards.core.domain.repository.AccountRepository
import com.rossomak.flashcards.core.domain.repository.NetworkAvailabilityGateway
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
 * [com.rossomak.flashcards.core.data.InterruptedAccountDeletionCompleter]. The call never starts without
 * a saved marker. A cancelled call keeps the marker, even if the server's answer was a success, and so
 * does a dropped connection or a timeout, whose answer is unknown: the next start finishes the deletion.
 */
class DefaultAccountRepository @Inject constructor(
    private val accountDeletionRemoteDataSource: AccountDeletionRemoteDataSource,
    private val authRemoteDataSource: AuthRemoteDataSource,
    private val accountDeletionMarkerLocalDataSource: AccountDeletionMarkerLocalDataSource,
    private val deletedAccountQueuePurger: DeletedAccountQueuePurger,
    private val networkAvailabilityGateway: NetworkAvailabilityGateway,
) : AccountRepository {

    override suspend fun deleteAccount(): AccountDeletionResult {
        val uid = authRemoteDataSource.getCurrentUser()?.uid ?: run {
            loge { "Account deletion not started: nobody is signed in" }
            return Failed(ServiceError)
        }
        if (!networkAvailabilityGateway.isInternetAvailable()) {
            logd { "Account deletion not started: no internet" }
            return Failed(NoConnection)
        }
        val isMarked = withContext(Dispatchers.IO) { accountDeletionMarkerLocalDataSource.write(uid) }
        if (!isMarked) {
            loge { "Account deletion not started: the deletion marker could not be saved" }
            return Failed(ServiceError)
        }
        return accountDeletionRemoteDataSource.deleteAccount().fold(
            onSuccess = {
                logi { "Account deleted on the server, signing out" }
                completeOnDevice(uid)
                Deleted
            },
            onFailure = { exception ->
                if (exception.leavesDeletionUnknown()) {
                    loge(exception) { "Account deletion result unknown, keeping the marker for the next start" }
                } else {
                    loge(exception) { "Account deletion failed" }
                    withContext(Dispatchers.IO) { accountDeletionMarkerLocalDataSource.clear() }
                }
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

    // A dropped connection or a timeout may hide a deletion the server finished; any other failure did not delete.
    private fun Throwable.leavesDeletionUnknown(): Boolean =
        isConnectionFailure() || (this is FirebaseFunctionsException && code in UNKNOWN_RESULT_CODES)

    private companion object {
        val UNKNOWN_RESULT_CODES = setOf(DEADLINE_EXCEEDED, UNAVAILABLE)
    }
}
