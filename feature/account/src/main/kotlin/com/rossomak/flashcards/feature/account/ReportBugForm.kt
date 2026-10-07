package com.rossomak.flashcards.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.rossomak.flashcards.core.domain.model.BugReport
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsInfoBanner
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing

/** The body of the Report a bug screen, in the column of its scrolling content. */
@Composable
internal fun ColumnScope.ReportBugForm(
    state: ReportBugScreenState,
    onSeverityClick: () -> Unit,
    onDescriptionChange: (String) -> Unit,
) {
    ReportBugSeverityField(
        severity = state.severity,
        enabled = !state.isLocked,
        onClick = onSeverityClick,
    )
    GuidingQuestions()
    DescriptionField(
        text = state.draftText,
        enabled = !state.isLocked,
        onTextChange = onDescriptionChange,
    )
    FlashcardsInfoBanner(
        text = stringResource(R.string.report_bug_diagnostics_message),
        icon = Icons.Default.Info,
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaterialTheme.spacing.normal),
    )
}

/** The inset every block under the severity field shares: the screen's side gutter, and a gap above. */
@Composable
private fun Modifier.formBlockPadding(): Modifier = padding(
    start = MaterialTheme.spacing.normal,
    top = MaterialTheme.spacing.normal,
    end = MaterialTheme.spacing.normal,
)

@Composable
private fun GuidingQuestions() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .formBlockPadding(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall),
    ) {
        GuidingQuestion(text = stringResource(R.string.report_bug_question_activity_message))
        GuidingQuestion(text = stringResource(R.string.report_bug_question_outcome_message))
        GuidingQuestion(text = stringResource(R.string.report_bug_question_reproducibility_message))
    }
}

@Composable
private fun GuidingQuestion(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // A drawn dot rather than a bullet character, so a screen reader does not announce "bullet".
        Box(
            modifier = Modifier
                .padding(end = MaterialTheme.spacing.xsmall)
                .size(MaterialTheme.sizes.bulletDot)
                .background(color = MaterialTheme.colorScheme.onSurfaceVariant, shape = CircleShape),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DescriptionField(
    text: String,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
) {
    val length = BugReport.descriptionLength(text)

    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier
            .fillMaxWidth()
            .formBlockPadding(),
        enabled = enabled,
        placeholder = { Text(text = stringResource(R.string.report_bug_description_hint)) },
        supportingText = { Text(text = counterText(length)) },
        isError = length > BugReport.MAX_DESCRIPTION_LENGTH,
        // No Send IME action: Enter inserts a newline.
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        minLines = DESCRIPTION_MIN_LINES,
        shape = RoundedCornerShape(MaterialTheme.cornerRadius.card),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun counterText(length: Int): String = when {
    length < BugReport.MIN_DESCRIPTION_LENGTH ->
        stringResource(R.string.report_bug_counter_minimum_message, length, BugReport.MIN_DESCRIPTION_LENGTH)
    length <= BugReport.MAX_DESCRIPTION_LENGTH ->
        stringResource(R.string.report_bug_counter_message, length, BugReport.MAX_DESCRIPTION_LENGTH)
    else -> stringResource(R.string.report_bug_counter_over_limit_message, length, BugReport.MAX_DESCRIPTION_LENGTH)
}

private const val DESCRIPTION_MIN_LINES = 5
