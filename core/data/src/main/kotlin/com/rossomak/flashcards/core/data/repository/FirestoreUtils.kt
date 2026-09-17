package com.rossomak.flashcards.core.data.repository

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext

private const val INITIAL_RETRY_BACKOFF_MILLIS = 1_000L
private const val MAX_RETRY_BACKOFF_MILLIS = 30_000L
private const val MAX_RETRY_BACKOFF_SHIFT = 5L

private val TRANSIENT_FIRESTORE_CODES = setOf(
    FirebaseFirestoreException.Code.UNAVAILABLE,
    FirebaseFirestoreException.Code.ABORTED,
    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
    FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
)

private fun Throwable.isPermissionDenied(): Boolean =
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED

private fun Throwable.isTransientFirestoreFailure(): Boolean =
    this is FirebaseFirestoreException && code in TRANSIENT_FIRESTORE_CODES

private fun retryBackoffMillis(attempt: Long): Long =
    min(INITIAL_RETRY_BACKOFF_MILLIS shl attempt.coerceAtMost(MAX_RETRY_BACKOFF_SHIFT).toInt(), MAX_RETRY_BACKOFF_MILLIS)

fun <T> Flow<T>.retryOnFirestorePermissionDenied(): Flow<T> =
    retryWhen { cause, attempt ->
        // Only known-transient Firestore failures are worth retrying. Everything else — permanent
        // Firestore errors (including PERMISSION_DENIED, an intentional sign-out teardown, not a
        // transient failure) and non-Firestore failures (e.g. a mapper crash) — falls through to
        // .catch below instead of retrying forever.
        if (cause.isTransientFirestoreFailure()) {
            delay(retryBackoffMillis(attempt).milliseconds)
            true
        } else {
            false
        }
    }.catch { exception ->
        if (exception.isPermissionDenied()) {
            return@catch
        }
        throw exception
    }.flowOn(Dispatchers.IO)

// Generic catch is deliberate: this is the repository boundary converting any Firestore/task failure into Result.failure
@Suppress("TooGenericExceptionCaught")
suspend fun <T> runCatchingFirestoreWrite(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
    try {
        Result.success(block())
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}
