package com.rossomak.flashcards.core.domain.model

/**
 * Why a voice demo attempt failed. Non-string per the domain/UI string split (AGENTS.md, "String
 * Resources"): the repository never picks UI copy, so the presentation layer resolves each variant to
 * a string resource when (and only when) it actually renders one.
 */
sealed interface VoiceDemoFailureReason {
    /** The capture audio route never became ready in time. */
    data object RouteUnavailable : VoiceDemoFailureReason

    /** The capture engine reported a failure. */
    data class CaptureError(val reason: VoiceCaptureFailureReason) : VoiceDemoFailureReason
}
