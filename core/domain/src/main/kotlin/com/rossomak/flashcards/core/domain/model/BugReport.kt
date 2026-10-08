package com.rossomak.flashcards.core.domain.model

/** [description] is already trimmed. */
data class BugReport(
    val description: String,
    val severity: BugReportSeverity,
    val installationInfo: InstallationInfo,
) {

    companion object {
        // Keep in sync with functions/src/lib/submitBugReport.ts.
        const val MIN_DESCRIPTION_LENGTH = 20
        const val MAX_DESCRIPTION_LENGTH = 1000

        /** Trimmed length of [rawText]. */
        fun descriptionLength(rawText: String): Int = rawText.trim().length
    }
}
