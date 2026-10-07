package com.rossomak.flashcards.feature.account

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector
import com.rossomak.flashcards.core.domain.model.BugReportSeverity
import com.rossomak.flashcards.core.domain.model.BugReportSeverity.Blocker
import com.rossomak.flashcards.core.domain.model.BugReportSeverity.Cosmetic
import com.rossomak.flashcards.core.domain.model.BugReportSeverity.Minor

/** How a severity reads: on the form's field and as a card in the severity dialog. */
internal class ReportBugSeverityOption(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val icon: ImageVector,
)

internal fun BugReportSeverity.option(): ReportBugSeverityOption = when (this) {
    Blocker -> ReportBugSeverityOption(
        title = R.string.report_bug_severity_blocker_label,
        description = R.string.report_bug_severity_blocker_message,
        icon = Icons.Default.ErrorOutline,
    )
    Minor -> ReportBugSeverityOption(
        title = R.string.report_bug_severity_minor_label,
        description = R.string.report_bug_severity_minor_message,
        icon = Icons.Default.WarningAmber,
    )
    Cosmetic -> ReportBugSeverityOption(
        title = R.string.report_bug_severity_cosmetic_label,
        description = R.string.report_bug_severity_cosmetic_message,
        icon = Icons.Default.Brush,
    )
}
