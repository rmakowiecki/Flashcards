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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.rossomak.flashcards.core.domain.model.BugReportSeverity
import com.rossomak.flashcards.core.domain.model.BugReportSeverity.Blocker
import com.rossomak.flashcards.core.ui.composables.FlashcardsIconTile
import com.rossomak.flashcards.core.ui.composables.FlashcardsOverlineLabel
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing

/** Opacity of the whole field when it is disabled, as on the option cards in the severity dialog. */
private const val DISABLED_ALPHA = 0.6f

/**
 * The form's severity field: a card showing the chosen severity, or a muted placeholder while there
 * is none, that opens the severity dialog. The whole card is the tap target; the edit icon is
 * decoration.
 */
@Composable
internal fun SeverityField(
    severity: BugReportSeverity?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val option = severity?.option()
    val shape = RoundedCornerShape(MaterialTheme.cornerRadius.card)

    FlashcardsOverlineLabel(text = stringResource(R.string.report_bug_severity_label))
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.normal)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            // Clipped before `clickable` so the ripple follows the rounded corners.
            .clip(shape)
            .clickable(
                enabled = enabled,
                onClickLabel = stringResource(R.string.report_bug_severity_hint),
                role = Role.Button,
                onClick = onClick,
            ),
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
private fun SeverityFieldEmptyPreview() {
    FlashcardsTheme {
        Surface {
            Column { SeverityField(severity = null, enabled = true, onClick = {}) }
        }
    }
}

@PreviewLightDark
@Composable
private fun SeverityFieldChosenPreview() {
    FlashcardsTheme {
        Surface {
            Column { SeverityField(severity = Blocker, enabled = true, onClick = {}) }
        }
    }
}

@PreviewLightDark
@Composable
private fun SeverityFieldDisabledPreview() {
    FlashcardsTheme {
        Surface {
            Column { SeverityField(severity = Blocker, enabled = false, onClick = {}) }
        }
    }
}
