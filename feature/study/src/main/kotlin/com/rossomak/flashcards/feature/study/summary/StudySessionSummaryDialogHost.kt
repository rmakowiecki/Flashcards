package com.rossomak.flashcards.feature.study.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.rossomak.flashcards.core.designsystem.theme.sizes
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardsSingleActionDialog
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.format.AppLocale
import com.rossomak.flashcards.core.ui.format.formatSignedXp
import com.rossomak.flashcards.feature.study.R
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryDialog.XpBreakdown

@Composable
internal fun StudySessionSummaryDialogHost(
    activeDialog: StudySessionSummaryDialog?,
    xpLines: List<XpBreakdownLine>,
    xpTotal: Int,
    onDialogEvent: (StudySessionSummaryDialogEvent) -> Unit,
) {
    when (activeDialog) {
        null -> Unit
        XpBreakdown -> XpBreakdownDialog(
            xpLines = xpLines,
            xpTotal = xpTotal,
            onConfirm = { onDialogEvent(Confirm) },
            onDismiss = { onDialogEvent(Dismiss) },
        )
    }
}

@Composable
private fun XpBreakdownDialog(
    xpLines: List<XpBreakdownLine>,
    xpTotal: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FlashcardsSingleActionDialog(
        title = stringResource(R.string.study_session_summary_xp_breakdown_title),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    ) {
        xpLines.forEach { line -> XpBreakdownDialogRow(line = line) }
        HorizontalDivider()
        XpBreakdownTotalRow(xpTotal = xpTotal)
    }
}

@Composable
private fun XpBreakdownDialogRow(line: XpBreakdownLine, modifier: Modifier = Modifier) {
    val labelColor = if (line.isLoss) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = if (line.isLoss) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = xpSourceIcon(line.source),
            // Decorative: the label names the rule this icon stands for.
            contentDescription = null,
            modifier = Modifier.size(MaterialTheme.sizes.xpBreakdownRowIcon),
            tint = valueColor,
        )
        Column {
            Text(text = xpSourceLabel(line.source), style = MaterialTheme.typography.labelMedium, color = labelColor)
            Text(
                text = xpLineValue(line),
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = valueColor,
            )
        }
    }
}

@Composable
private fun XpBreakdownTotalRow(xpTotal: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.study_session_summary_xp_breakdown_total_label),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.study_session_summary_xp_row_amount_label, formatSignedXp(xpTotal, AppLocale)),
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
