package com.rossomak.flashcards.feature.study.preview

/** One-shot snackbar messages from the Preview Study Session Screen — never screen state. */
sealed interface PreviewStudySessionMessage {

    /** Start asked for the microphone again and the system still refused without prompting. */
    data object MicPermissionStillDenied : PreviewStudySessionMessage
}
