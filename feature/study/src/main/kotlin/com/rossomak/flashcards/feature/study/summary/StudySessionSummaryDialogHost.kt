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
import androidx.compose.ui.text.font.FontWeight
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardsSingleActionDialog
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.format.currentLocale
import com.rossomak.flashcards.core.ui.format.formatSignedXp
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.study.R
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryDialog.XpBreakdown
import java.util.Locale

/**
 * Renders [activeDialog], if any. The XP breakdown only reads state, so Done is a [Confirm] and an
 * outside tap or back press a [Dismiss], and both just close it.
 */
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

/** One row per line, a divider, then the total. The dialog scaffold scrolls the rows when they do not fit. */
@Composable
private fun XpBreakdownDialog(
    xpLines: List<XpBreakdownLine>,
    xpTotal: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = currentLocale()
    FlashcardsSingleActionDialog(
        title = stringResource(R.string.study_session_summary_xp_breakdown_title),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    ) {
        xpLines.forEach { line -> XpBreakdownDialogRow(line = line, locale = locale) }
        HorizontalDivider()
        XpBreakdownTotalRow(xpTotal = xpTotal, locale = locale)
    }
}

/** Icon, the rule's name, and its arithmetic. A loss is drawn in the error colour. */
@Composable
private fun XpBreakdownDialogRow(line: XpBreakdownLine, locale: Locale, modifier: Modifier = Modifier) {
    val labelColor = if (line.isLoss) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = if (line.isLoss) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
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
                text = xpLineValue(line, locale),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
            )
        }
    }
}

@Composable
private fun XpBreakdownTotalRow(xpTotal: Int, locale: Locale, modifier: Modifier = Modifier) {
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
            text = stringResource(R.string.study_session_summary_xp_row_amount_label, formatSignedXp(xpTotal, locale)),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
