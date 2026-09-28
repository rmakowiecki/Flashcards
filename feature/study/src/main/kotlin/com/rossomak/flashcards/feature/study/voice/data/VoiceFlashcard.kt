package com.rossomak.flashcards.feature.study.voice.data

data class VoiceFlashcard(
    val spokenQuestion: String,
    val spokenAnswer: String,
    // Grading context for voice answering: the original (non-speech-transformed) card content
    // the LLM grades the spoken answer against.
    val cardId: String = "",
    val questionText: String = "",
    val answerText: String = "",
)
