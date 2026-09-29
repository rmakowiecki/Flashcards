package com.rossomak.flashcards.core.domain.model

/**
 * Where the current Voice Answering round is. [Idle] while voice answering is off or paused;
 * [WaitingForQuestion] while the question is being read; [Listening] and [SpeechDetected] while
 * the microphone is open; [Grading] from the end of speech until the grade or failure arrives;
 * [SpeakingNotice] from then until the notice and its tail have finished.
 */
enum class VoiceAnswerPhase {
    Idle,
    WaitingForQuestion,
    Listening,
    SpeechDetected,
    Grading,
    SpeakingNotice,
}
