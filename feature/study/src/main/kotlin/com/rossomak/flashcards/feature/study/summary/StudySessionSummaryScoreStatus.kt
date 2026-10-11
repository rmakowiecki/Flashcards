package com.rossomak.flashcards.feature.study.summary

/** Where the Session Summary's XP score stands: still being resolved, resolved, or not computable. */
enum class StudySessionSummaryScoreStatus {
    Loading,
    Scored,
    Unavailable,
}
