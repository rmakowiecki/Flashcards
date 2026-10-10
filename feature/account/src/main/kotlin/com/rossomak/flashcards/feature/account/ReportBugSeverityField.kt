package com.rossomak.flashcards.feature.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rossomak.flashcards.core.domain.model.BugReportSeverity
import com.rossomak.flashcards.core.domain.model.BugReportSeverity.Blocker
import com.rossomak.flashcards.core.ui.composables.FlashcardsIconTile
import com.rossomak.flashcards.core.ui.composables.FlashcardsOverlineLabel
import com.rossomak.flashcards.core.ui.composables.common.disabledAlpha
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing

/** The whole card is the tap target; the edit icon is decoration. */
@Composable
internal fun ReportBugSeverityField(
    severity: BugReportSeverity?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val option = severity?.option()
    val shape = RoundedCornerShape(MaterialTheme.cornerRadius.card)
    // One TalkBack node: the field's name, then its value.
    val fieldName = stringResource(R.string.report_bug_severity_label)
    val fieldValue = stringResource(option?.title ?: R.string.report_bug_severity_hint)

    FlashcardsOverlineLabel(text = fieldName)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.normal)
            .disabledAlpha(enabled)
            // Before `clickable`, so the ripple follows the corners.
            .clip(shape)
            .clickable(
                enabled = enabled,
                onClickLabel = stringResource(R.string.report_bug_severity_hint),
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = fieldName
                stateDescription = fieldValue
            },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(width = MaterialTheme.sizes.hairline, color = MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlashcardsIconTile(
                icon = option?.icon ?: Icons.Outlined.Flag,
                contentDescription = null,
                contentColor = if (option != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (option != null) {
                Text(
                    text = stringResource(option.title),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = MaterialTheme.spacing.small),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(
                    text = stringResource(R.string.report_bug_severity_hint),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = MaterialTheme.spacing.small),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (severity == null) {
        Text(
            text = stringResource(R.string.report_bug_severity_required_message),
            modifier = Modifier.padding(
                start = MaterialTheme.spacing.normal,
                top = MaterialTheme.spacing.xsmall,
                end = MaterialTheme.spacing.normal,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@PreviewLightDark
@Composable
private fun ReportBugSeverityFieldEmptyPreview() {
    FlashcardsTheme {
        Surface {
            Column { ReportBugSeverityField(severity = null, enabled = true, onClick = {}) }
        }
    }
}

@PreviewLightDark
@Composable
private fun ReportBugSeverityFieldChosenPreview() {
    FlashcardsTheme {
        Surface {
            Column { ReportBugSeverityField(severity = Blocker, enabled = true, onClick = {}) }
        }
    }
}

@PreviewLightDark
@Composable
private fun ReportBugSeverityFieldDisabledPreview() {
    FlashcardsTheme {
        Surface {
            Column { ReportBugSeverityField(severity = Blocker, enabled = false, onClick = {}) }
        }
    }
}
