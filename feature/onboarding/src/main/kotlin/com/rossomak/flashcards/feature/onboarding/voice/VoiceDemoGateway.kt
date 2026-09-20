package com.rossomak.flashcards.feature.onboarding.voice

import com.rossomak.flashcards.core.voice.CapturedUtterance
import kotlinx.coroutines.flow.StateFlow

sealed interface VoiceDemoState {
    data object Idle : VoiceDemoState
    data object Listening : VoiceDemoState
    data object SpeechDetected : VoiceDemoState
    data class Ready(val utterance: CapturedUtterance) : VoiceDemoState
    data object Playing : VoiceDemoState
    data class Failed(val reason: String) : VoiceDemoState
}

/**
 * One-shot local voice pipeline demo for onboarding: record a short VAD-bounded utterance, hear it
 * played back obfuscated. Mirrors [com.rossomak.flashcards.feature.study.voice.VoiceGateway] — the
 * ViewModel talks only to this interface, never to core:voice concretes directly.
 *
 * Callers must already hold RECORD_AUDIO before calling [start]; permission is owned by the screen,
 * not this gateway (docs/temp/to-grill/mic-permission-check-platform-layer.md tracks moving that
 * off the UI later).
 */
interface VoiceDemoGateway {
    val state: StateFlow<VoiceDemoState>

    /** Starts a fresh listening attempt. Re-randomizes the obfuscation shift for this attempt. */
    fun start()

    /** Plays back the obfuscated utterance held by [VoiceDemoState.Ready]. No-op otherwise. */
    fun play()

    /** Hard stop: cancels listening/playback immediately. Called on backgrounding or leaving the step. */
    fun stop()
}
