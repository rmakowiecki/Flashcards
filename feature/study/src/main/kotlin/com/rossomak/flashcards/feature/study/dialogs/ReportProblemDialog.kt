package com.rossomak.flashcards.feature.study.dialogs

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tag
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.rossomak.flashcards.core.domain.model.CurationAction
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardsCheckableRow
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardsDecisionDialog
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardsMultiSelectGroup
import com.rossomak.flashcards.feature.study.R

/** One reportable problem: the [CurationAction] it maps to, plus how it is presented. */
private data class ReportableProblem(
    val action: CurationAction,
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
)

/**
 * Every [CurationAction], paired with how it is presented.
 *
 * Exhaustive by construction: the list checks itself against `CurationAction.entries` on first
 * access, so a new action cannot be added to the model and silently stay unreportable — a listing
 * a `when` would have caught, but a `listOf` cannot.
 *
 * Ordered by how a reader reaches for them — the difficulty pair first, then content problems in
 * rising severity, with the destructive [CurationAction.Delete] last.
 */
private val REPORTABLE_PROBLEMS = listOf(
    ReportableProblem(
        action = CurationAction.DifficultyTooEasy,
        icon = Icons.Default.ArrowUpward,
        labelRes = R.string.report_problem_raise_difficulty_label,
    ),
    ReportableProblem(
        action = CurationAction.DifficultyTooHard,
        icon = Icons.Default.ArrowDownward,
        labelRes = R.string.report_problem_lower_difficulty_label,
    ),
    ReportableProblem(
        action = CurationAction.WrongTags,
        icon = Icons.Default.Tag,
        labelRes = R.string.report_problem_wrong_tags_label,
    ),
    ReportableProblem(
        action = CurationAction.NeedsCodeExample,
        icon = Icons.Default.DataObject,
        labelRes = R.string.report_problem_needs_code_example_label,
    ),
    ReportableProblem(
        action = CurationAction.BacktickRedo,
        icon = Icons.Default.Code,
        labelRes = R.string.report_problem_wrong_backticks_label,
    ),
    ReportableProblem(
        action = CurationAction.FullRedo,
        icon = Icons.Default.Refresh,
        labelRes = R.string.report_problem_full_rewrite_label,
    ),
    ReportableProblem(
        action = CurationAction.Delete,
        icon = Icons.Default.Delete,
        labelRes = R.string.report_problem_delete_label,
    ),
).also { problems ->
    val covered = problems.map(ReportableProblem::action).toSet()
    check(covered == CurationAction.entries.toSet()) {
        "ReportProblemDialog is missing rows for ${CurationAction.entries - covered}"
    }
}

/**
 * Reports what is wrong with a flashcard.
 *
 * A [FlashcardsDecisionDialog]: Cancel discards every selection, Submit commits the whole set at
 * once. Submit stays disabled until at least one row is checked — computed by the caller from
 * [selectedActions], so the rule is unit-testable in the ViewModel rather than trapped in a
 * composable.
 *
 * Every [CurationAction] gets a row — see [REPORTABLE_PROBLEMS].
 *
 * Rows toggle independently, but the two difficulty actions are contradictory. The caller is
 * expected to clear the opposite via [CurationAction.difficultyOpposite] when one is checked, so
 * "too easy **and** too hard" never reaches the repository.
 *
 * The draft always starts empty — this files a fresh report rather than editing the card's
 * previous one, so an unchecked box is never ambiguous between "not a problem" and "already
 * reported".
 */
@Composable
fun ReportProblemDialog(
    selectedActions: Set<CurationAction>,
    onActionCheckedChange: (CurationAction, Boolean) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlashcardsDecisionDialog(
        title = stringResource(R.string.report_problem_dialog_title),
        confirmLabel = stringResource(R.string.report_problem_submit_button),
        onConfirm = onSubmit,
        onCancel = onCancel,
        modifier = modifier,
        icon = Icons.Default.Flag,
        supportingText = stringResource(R.string.report_problem_dialog_message),
        confirmEnabled = selectedActions.isNotEmpty(),
    ) {
        FlashcardsMultiSelectGroup {
            REPORTABLE_PROBLEMS.forEach { problem ->
                FlashcardsCheckableRow(
                    icon = problem.icon,
                    label = stringResource(problem.labelRes),
                    checked = problem.action in selectedActions,
                    onCheckedChange = { checked -> onActionCheckedChange(problem.action, checked) },
                )
            }
        }
    }
}

@Preview
@Composable
private fun ReportProblemDialogPreview() {
    ReportProblemDialog(
        selectedActions = setOf(CurationAction.DifficultyTooHard, CurationAction.NeedsCodeExample),
        onActionCheckedChange = { _, _ -> },
        onSubmit = {},
        onCancel = {},
    )
}

@Preview
@Composable
private fun ReportProblemDialogEmptyPreview() {
    ReportProblemDialog(
        selectedActions = emptySet(),
        onActionCheckedChange = { _, _ -> },
        onSubmit = {},
        onCancel = {},
    )
}
