package com.rossomak.flashcards.feature.study.preview

/** One-shot snackbar messages from the Preview Study Session Screen — never screen state. */
sealed interface PreviewStudySessionMessage {

    /** Start asked for the microphone again and the system still refused without prompting. */
    data object MicPermissionStillDenied : PreviewStudySessionMessage

    /**
     * Voice answering was asked for by a User who is not Premium and stays off. The screen also
     * closes the settings sheet, so the snackbar isn't hidden behind it.
     */
    data object PremiumFeatureComingSoon : PreviewStudySessionMessage
}
