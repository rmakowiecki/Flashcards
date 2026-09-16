package com.rossomak.flashcards.core.data.repository

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

private const val INITIAL_RETRY_BACKOFF_MILLIS = 1_000L
private const val MAX_RETRY_BACKOFF_MILLIS = 30_000L
private const val MAX_RETRY_BACKOFF_SHIFT = 5L

private fun Throwable.isPermissionDenied(): Boolean =
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED

private fun retryBackoffMillis(attempt: Long): Long =
    min(INITIAL_RETRY_BACKOFF_MILLIS shl attempt.coerceAtMost(MAX_RETRY_BACKOFF_SHIFT).toInt(), MAX_RETRY_BACKOFF_MILLIS)

fun <T> Flow<T>.retryOnFirestorePermissionDenied(): Flow<T> =
    retryWhen { cause, attempt ->
        // Sign-out clears auth mid-collection and Firestore rejects the listener with
        // PERMISSION_DENIED; that's an intentional teardown, not a transient failure, so
        // let it fall through to .catch below instead of reopening a now-unauthenticated listener.
        if (cause.isPermissionDenied()) {
            false
        } else {
            delay(retryBackoffMillis(attempt).milliseconds)
            true
        }
    }
        .catch { exception ->
            if (exception.isPermissionDenied()) {
                return@catch
            }
            throw exception
        }
        .flowOn(Dispatchers.IO)

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
