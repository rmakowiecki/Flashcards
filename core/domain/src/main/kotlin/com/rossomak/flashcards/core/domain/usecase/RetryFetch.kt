package com.rossomak.flashcards.core.domain.usecase

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

private const val FETCH_RETRY_ATTEMPTS = 2
private const val FETCH_RETRY_BASE_DELAY_MILLIS = 300L

/**
 * Gives a one-shot fetch made inside an observed flow a few bounded retries before degrading to an
 * empty list (see [retryFetchOrNull] to tell the two apart). Transient failures (e.g. a reconnect
 * race) have no listener to retry them, unlike the flow the fetch enriches.
 */
internal suspend fun <T> retryFetch(block: suspend () -> Result<List<T>>): List<T> = retryFetchOrNull(block).orEmpty()

/** [retryFetch] for a caller that must tell an exhausted fetch apart from an empty result: `null` once retries run out. */
internal suspend fun <T> retryFetchOrNull(block: suspend () -> Result<List<T>>): List<T>? {
    repeat(FETCH_RETRY_ATTEMPTS) { attempt ->
        block().getOrNull()?.let { return it }
        delay((FETCH_RETRY_BASE_DELAY_MILLIS * (attempt + 1)).milliseconds)
    }
    return block().getOrNull()
}
