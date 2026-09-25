package com.rossomak.flashcards.feature.debug.voiceindicator

import com.rossomak.flashcards.core.ui.composables.voice.FlashcardsVoiceCaptureIndicatorDefaults

/** Control state only; bar levels are a separate flow so a snapshot recomposes just the indicator. */
data class VoiceIndicatorDebugScreenState(
    val isSpeechSimulated: Boolean = true,
    val levelIntervalMillis: Int = FlashcardsVoiceCaptureIndicatorDefaults.LEVEL_INTERVAL_MILLIS,
)
