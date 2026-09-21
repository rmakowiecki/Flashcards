package com.rossomak.flashcards.feature.study.voice

import com.rossomak.flashcards.core.voice.VoiceCaptureFailureReason

/**
 * Why [VoiceAnswerController] failed. Non-string per the domain/UI string split (AGENTS.md, "String
 * Resources") — resolved to a string resource only at the presentation boundary that surfaces it.
 * [GradingFailed.detail] is a raw caught-exception message for logging only, never shown to a user.
 */
sealed interface VoiceAnswerFailureReason {
    data object PermissionMissing : VoiceAnswerFailureReason
    data class CaptureFailed(val reason: VoiceCaptureFailureReason) : VoiceAnswerFailureReason
    data class GradingFailed(val detail: String?) : VoiceAnswerFailureReason
}
