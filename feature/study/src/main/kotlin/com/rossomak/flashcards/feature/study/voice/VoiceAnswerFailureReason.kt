package com.rossomak.flashcards.feature.study.voice

import com.rossomak.flashcards.core.domain.model.VoiceCaptureFailureReason
import java.io.IOException

/**
 * Why [VoiceAnswerController] failed. Non-string per the domain/UI string split (AGENTS.md, "String
 * Resources") — resolved to a string resource only at the presentation boundary that surfaces it.
 */
sealed interface VoiceAnswerFailureReason {
    data object PermissionMissing : VoiceAnswerFailureReason
    data class CaptureFailed(val reason: VoiceCaptureFailureReason) : VoiceAnswerFailureReason

    /** The captured answer could not be transcribed or graded. Classified by [toGradingFailureReason]. */
    sealed interface GradingFailed : VoiceAnswerFailureReason {

        /** The grading service could not be reached: the device is offline or the request timed out. */
        data object NoConnection : GradingFailed

        /** The grading service was reached but did not return a grade. */
        data object ServiceError : GradingFailed
    }
}

/**
 * The streamed grading callable never surfaces a bare [IOException]: the Firebase Functions SDK
 * wraps a failed or timed-out request in its own exception with the [IOException] as the cause, so
 * the whole cause chain is searched. Everything else, including an HTTP 503 or an entitlement
 * rejection, means the service was reached.
 */
fun Throwable.toGradingFailureReason(): VoiceAnswerFailureReason.GradingFailed {
    // Bounded, since a malformed cause chain can loop back on itself.
    val causeChain = generateSequence(this) { throwable -> throwable.cause }.take(MAX_CAUSE_CHAIN_DEPTH)
    return if (causeChain.any { throwable -> throwable is IOException }) {
        VoiceAnswerFailureReason.GradingFailed.NoConnection
    } else {
        VoiceAnswerFailureReason.GradingFailed.ServiceError
    }
}

private const val MAX_CAUSE_CHAIN_DEPTH = 16
