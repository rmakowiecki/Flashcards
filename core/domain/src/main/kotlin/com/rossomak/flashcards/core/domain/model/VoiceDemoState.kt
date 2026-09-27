package com.rossomak.flashcards.core.domain.model

/**
 * Where the onboarding voice demo is: listening for an utterance, finishing one the user stopped
 * early, holding one ready to play back, playing it, or failed.
 */
sealed interface VoiceDemoState {
    data object Idle : VoiceDemoState
    data object Listening : VoiceDemoState
    data object SpeechDetected : VoiceDemoState

    /** The user stopped recording early; the utterance recorded so far is being finished. */
    data object Processing : VoiceDemoState

    /** An utterance was captured and can be played back. */
    data object Ready : VoiceDemoState
    data object Playing : VoiceDemoState
    data class Failed(val reason: VoiceDemoFailureReason) : VoiceDemoState
}
