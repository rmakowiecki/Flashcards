package com.rossomak.flashcards.core.domain.model

/** How an early "stop recording" of the onboarding voice demo ended. */
enum class VoiceDemoRecordingResult {
    /** An utterance was kept and is ready to play back. */
    Captured,

    /** Nothing long enough to keep was spoken before the stop; the demo went back to idle. */
    NothingCaptured,

    /** The attempt ended some other way first: a hard stop, or a capture failure. */
    Cancelled,
}
