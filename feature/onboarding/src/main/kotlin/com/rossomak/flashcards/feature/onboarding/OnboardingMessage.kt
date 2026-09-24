package com.rossomak.flashcards.feature.onboarding

import com.rossomak.flashcards.core.domain.model.VoiceDemoFailureReason

/** One-shot snackbar messages from Onboarding — never screen state. */
sealed interface OnboardingMessage {

    /** The voice demo stopped on a route or capture failure. */
    data class VoiceDemoFailed(val reason: VoiceDemoFailureReason) : OnboardingMessage

    /** "Test your voice" asked for the microphone again and the system still refused without prompting. */
    data object MicPermissionStillDenied : OnboardingMessage
}
