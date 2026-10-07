package com.rossomak.flashcards.feature.account

/** Where a report is on its way out. Sending and Delivered both lock the form; they are never both true. */
enum class ReportBugSubmissionStatus {
    Idle,
    Sending,
    Delivered,
}
