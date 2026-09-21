package com.rossomak.flashcards.feature.onboarding.voice

import com.rossomak.flashcards.core.voice.CapturedUtterance
import com.rossomak.flashcards.core.voice.VoiceCaptureFailureReason
import kotlinx.coroutines.flow.StateFlow

sealed interface VoiceDemoState {
    data object Idle : VoiceDemoState
    data object Listening : VoiceDemoState
    data object SpeechDetected : VoiceDemoState
    data class Ready(val utterance: CapturedUtterance) : VoiceDemoState
    data object Playing : VoiceDemoState
    data class Failed(val reason: VoiceDemoFailureReason) : VoiceDemoState
}

/**
 * Why a voice demo attempt failed. Kept non-string per the domain/UI string split (AGENTS.md,
 * "String Resources"): the gateway never picks UI copy, so [VoicePrivacyStep] resolves each
 * variant to a string resource when (and only when) it actually renders one.
 */
sealed interface VoiceDemoFailureReason {
    /** [com.rossomak.flashcards.core.voice.AudioRouteManager.awaitRouteReady] never resolved in time. */
    data object RouteUnavailable : VoiceDemoFailureReason

    /** [com.rossomak.flashcards.core.voice.VoiceCaptureEvent.CaptureFailed] from the capture engine. */
    data class CaptureError(val reason: VoiceCaptureFailureReason) : VoiceDemoFailureReason
}

/**
 * One-shot local voice pipeline demo for onboarding: record a short VAD-bounded utterance, hear it
 * played back obfuscated. Mirrors [com.rossomak.flashcards.feature.study.voice.VoiceGateway] — the
 * ViewModel talks only to this interface, never to core:voice concretes directly.
 *
 * Callers must already hold RECORD_AUDIO before calling [start]; permission is owned by the screen,
 * not this gateway.
 */
interface VoiceDemoGateway {
    val state: StateFlow<VoiceDemoState>

    /** Starts a fresh listening attempt. Re-randomizes the obfuscation shift for this attempt. */
    fun start()

    /** Plays back the obfuscated utterance held by [VoiceDemoState.Ready]. No-op otherwise. */
    fun play()

    /** Hard stop: cancels listening/playback immediately. Called on backgrounding or leaving the step. */
    fun stop()

    /** Releases resources held for the lifetime of this gateway. Called once, from `onCleared()`. */
    fun release()
}
