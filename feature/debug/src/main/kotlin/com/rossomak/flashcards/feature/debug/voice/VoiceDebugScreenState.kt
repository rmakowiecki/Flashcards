package com.rossomak.flashcards.feature.debug.voice

data class VoiceDebugScreenState(
    val micRouteLabel: String = "",
    val playbackRouteLabel: String = "Idle",
    val isVadListening: Boolean = false,
    val isSpeechDetected: Boolean = false,
    val vadSpeechProbability: Float = 0f,
    val hasCapturedUtterance: Boolean = false,
    val isPlayingLastAnswer: Boolean = false,
    val capturedUtteranceDurationMs: Long = 0L,
    val vadEventLog: List<String> = emptyList(),
    val isRecordingClip: Boolean = false,
    val rawClipDurationMs: Long = 0L,
    val hasRawClip: Boolean = false,
    val isTranscribing: Boolean = false,
    val transcriptionResult: String? = null,
    val isCheckingEntitlement: Boolean = false,
    val entitlementResult: String? = null,
)
