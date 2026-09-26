package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.VoiceDemoRecordingResult
import com.rossomak.flashcards.core.domain.model.VoiceDemoState
import kotlinx.coroutines.flow.Flow
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

    /**
     * Audio level as bar levels in `0..1`, index 0 newest, one list per shaping interval: the
     * microphone input while listening, the played-back audio while [VoiceDemoState.Playing], and an
     * all-zero list otherwise. Cold: computed only while collected, on the device only.
     */
    val levels: Flow<List<Float>>

    /** Starts a fresh listening attempt. Re-randomizes the obfuscation shift for this attempt. */
    fun start()

    /**
     * Stops listening early but keeps what was already said: moves to [VoiceDemoState.Processing],
     * finishes the in-flight utterance, then settles on [VoiceDemoState.Ready] or, when nothing
     * long enough was spoken, [VoiceDemoState.Idle]. Safe to call when the attempt already ended.
     * Only the call that did the finishing returns [VoiceDemoRecordingResult.NothingCaptured]; a
     * call that arrives while another is finishing, or after the attempt ended, returns
     * [VoiceDemoRecordingResult.Captured] or [VoiceDemoRecordingResult.Cancelled].
     */
    suspend fun finishRecording(): VoiceDemoRecordingResult

    /** Plays back the captured utterance while [VoiceDemoState.Ready]. No-op otherwise. */
    fun play()

    /** Hard stop: cancels listening/playback immediately. Called on backgrounding or leaving the step. */
    fun stop()
}
