package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.VoiceDemoState
import kotlinx.coroutines.flow.StateFlow

/**
 * One-shot local voice pipeline demo for onboarding: record a short VAD-bounded utterance, hear it
 * played back obfuscated. Scoped to one ViewModel and released when that ViewModel is cleared.
 *
 * The caller must hold the microphone permission before [start], confirmed through
 * [com.rossomak.flashcards.core.domain.usecase.RequestPermissionUseCase].
 */
interface VoiceDemoGateway {
    val state: StateFlow<VoiceDemoState>

    /** Starts a fresh listening attempt. Re-randomizes the obfuscation shift for this attempt. */
    fun start()

    /** Plays back the captured utterance while [VoiceDemoState.Ready]. No-op otherwise. */
    fun play()

    /** Hard stop: cancels listening/playback immediately. Called on backgrounding or leaving the step. */
    fun stop()
}
