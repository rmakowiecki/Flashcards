package com.rossomak.flashcards.core.domain.model

/** A [VoiceAnswerGrade] for tests; its spoken feedback matches the shown one unless a test sets it. */
fun voiceAnswerGrade(sanitizedTranscript: String = "answer", gradePercent: Int, feedback: String = "Right.", spokenFeedback: String = feedback): VoiceAnswerGrade =
    VoiceAnswerGrade(sanitizedTranscript = sanitizedTranscript, gradePercent = gradePercent, feedback = feedback, spokenFeedback = spokenFeedback)
