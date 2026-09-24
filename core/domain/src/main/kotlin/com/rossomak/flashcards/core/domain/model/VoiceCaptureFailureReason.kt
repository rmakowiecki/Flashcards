package com.rossomak.flashcards.core.domain.model

/**
 * Why voice capture failed. Non-string per the domain/UI string split (AGENTS.md,
 * "String Resources") — resolved to a string resource only at the presentation boundary that
 * actually surfaces it. [PermissionMissing.detail] and [CaptureLoopError.detail] carry the raw
 * caught-exception message for logging only, never shown to a user.
 */
sealed interface VoiceCaptureFailureReason {
    data object BluetoothMicUnavailable : VoiceCaptureFailureReason
    data object AudioRecordInitFailed : VoiceCaptureFailureReason
    data object CaptureNotRoutedToBluetooth : VoiceCaptureFailureReason
    data class PermissionMissing(val detail: String?) : VoiceCaptureFailureReason
    data class CaptureLoopError(val detail: String?) : VoiceCaptureFailureReason
}
