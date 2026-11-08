package com.rossomak.flashcards.core.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * A flow over the one [source] returns, with the call to [source] deferred to collection. A throw
 * from [source] itself, such as a suspend use case failing before it builds its flow, then reaches
 * downstream operators like `catch`, the same as a throw from inside the flow.
 */
fun <T> flowFrom(source: suspend () -> Flow<T>): Flow<T> = flow { emitAll(source()) }

/** Reports an upstream failure with [loge] and completes, instead of rethrowing it to the collector. */
fun <T> Flow<T>.catchAndLog(message: () -> String): Flow<T> = catch { error -> loge(error, message) }
