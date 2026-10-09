package com.rossomak.flashcards.core.common

import java.io.IOException

/**
 * The service was never reached: an [IOException] anywhere in the cause chain. The whole chain is
 * searched because SDKs such as Firebase Functions never surface a bare [IOException]: they wrap a
 * failed or timed-out request in their own exception with the [IOException] as the cause.
 */
fun Throwable.isConnectionFailure(): Boolean {
    // Bounded, since a malformed cause chain can loop back on itself.
    val causeChain = generateSequence(this) { throwable -> throwable.cause }.take(MAX_CAUSE_CHAIN_DEPTH)
    return causeChain.any { throwable -> throwable is IOException }
}

private const val MAX_CAUSE_CHAIN_DEPTH = 16
