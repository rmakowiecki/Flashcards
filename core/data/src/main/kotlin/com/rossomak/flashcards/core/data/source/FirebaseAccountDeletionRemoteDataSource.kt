package com.rossomak.flashcards.core.data.source

import com.google.firebase.functions.FirebaseFunctions
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** The `deleteAccount` callable takes no payload and returns nothing; see ADR-0059 for what it deletes. */
class FirebaseAccountDeletionRemoteDataSource @Inject constructor(
    private val functions: FirebaseFunctions,
) : AccountDeletionRemoteDataSource {

    // A callable Task can fail with more than FirebaseFunctionsException.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun deleteAccount(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            functions.getHttpsCallable(DELETE_ACCOUNT_FUNCTION_NAME).call().await()
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private companion object {
        const val DELETE_ACCOUNT_FUNCTION_NAME = "deleteAccount"
    }
}
