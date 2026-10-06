package com.rossomak.flashcards.core.domain.model

/** [description] is already trimmed: nothing downstream trims it again. */
data class BugReport(
    val description: String,
    val severity: BugReportSeverity,
    val installationInfo: InstallationInfo,
) {

    companion object {
        // The server enforces the same limits in functions/src/lib/submitBugReport.ts: change both together.
        // It trims with JavaScript's rules, which differ from Kotlin's for a few exotic characters
        // (U+FEFF, U+001C to U+001F). That can only matter exactly on a limit, and the outcome is the generic failure.
        const val MIN_DESCRIPTION_LENGTH = 20
        const val MAX_DESCRIPTION_LENGTH = 1000

        /** The one definition of a description's length: the trimmed length of [rawText]. */
        fun descriptionLength(rawText: String): Int = rawText.trim().length
    }
}
