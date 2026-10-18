package com.rossomak.flashcards.core.ui.composables.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.core.ui.R
import com.rossomak.flashcards.core.ui.composables.FlashcardsDifficultyRangePill
import com.rossomak.flashcards.core.ui.composables.FlashcardsIntRangeSlider
import com.rossomak.flashcards.core.ui.composables.FlashcardsTagChip
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTextButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize

/**
 * Narrows a session's card pool by tag and difficulty.
 *
 * Deliberately has **no** "Keep as my default" checkbox: tags are per-subcategory, so a tag set
 * cannot meaningfully carry to a different subcategory, and filters stay session-scoped
 * (ADR-0030). That is expressed by simply not passing `keepAsDefault` down to the scaffold.
 *
 * Tags are OR-within (any selected tag matches) and AND-combined with the difficulty range.
 * [availableTags] is empty for multi-subcategory sessions, which filter by difficulty only — in
 * that case the whole Tag section (label, bulk actions, chips) is omitted rather than shown with
 * an empty-chips fallback message, since a multi-subcategory pool has no coherent tag vocabulary
 * to offer at all.
 *
 * The screen this dialog is opened from is expected to already carry every tag selected by
 * default — [filters] is never seeded empty-meaning-all here, unlike the difficulty range. Every
 * Flashcard carries at least one Tag (`CONTEXT.md`), so at least one must stay checked: the
 * confirm button disables once [FlashcardFilters.selectedTags] is empty while [availableTags] is
 * not, via the Select all / Deselect all row above the chip grid.
 *
 * A single [onFiltersChange] rather than one callback per field: every edit here — a tag toggle,
 * select/deselect all, the difficulty slider — is a whole new [FlashcardFilters], so the host does
 * exactly the total `copy(draft = it)` ADR-0036 calls for, and this composable stays under
 * detekt's parameter-count limit as the tag bulk-actions were added.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlashcardFiltersDialog(
    availableTags: List<String>,
    filters: FlashcardFilters,
    difficultyBounds: IntRange,
    onFiltersChange: (FlashcardFilters) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlashcardsSingleActionDialog(
        title = stringResource(R.string.flashcard_filters_dialog_title),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmEnabled = filters.selectedTags.isNotEmpty() || availableTags.isEmpty(),
        modifier = modifier,
    ) {
        if (availableTags.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall)) {
                SectionLabel(text = stringResource(R.string.flashcard_filters_tags_label))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    FlashcardsTextButton(
                        text = stringResource(R.string.flashcard_filters_select_all_button),
                        onClick = { onFiltersChange(filters.selectAllTags(availableTags)) },
                        size = FlashcardsComponentSize.Small,
                        enabled = filters.selectedTags != availableTags.toSet(),
                    )
                    FlashcardsTextButton(
                        text = stringResource(R.string.flashcard_filters_deselect_all_button),
                        onClick = { onFiltersChange(filters.deselectAllTags()) },
                        size = FlashcardsComponentSize.Small,
                        enabled = filters.selectedTags.isNotEmpty(),
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
                ) {
                    availableTags.forEach { tag ->
                        FlashcardsTagChip(
                            label = tag,
                            selected = tag in filters.selectedTags,
                            onSelectedChange = { selected ->
                                onFiltersChange(filters.withTag(tag, selected))
                            },
                        )
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(
                    text = stringResource(R.string.flashcard_filters_difficulty_label),
                    modifier = Modifier.weight(1f),
                )
                FlashcardsDifficultyRangePill(
                    lowLevel = filters.difficultyRange.first,
                    highLevel = filters.difficultyRange.last,
                )
            }
            FlashcardsIntRangeSlider(
                value = filters.difficultyRange,
                bounds = difficultyBounds,
                onValueChange = { onFiltersChange(filters.copy(difficultyRange = it)) },
            )
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Preview
@Composable
private fun FlashcardFiltersDialogPreview() {
    FlashcardFiltersDialog(
        availableTags = listOf("state", "recomposition", "side-effects", "modifiers"),
        filters = FlashcardFilters(selectedTags = setOf("state", "modifiers"), difficultyRange = 3..8),
        difficultyBounds = 1..10,
        onFiltersChange = {},
        onConfirm = {},
        onDismiss = {},
    )
}

@Preview
@Composable
private fun FlashcardFiltersDialogMultiSubcategoryPreview() {
    FlashcardFiltersDialog(
        availableTags = emptyList(),
        filters = FlashcardFilters(selectedTags = emptySet(), difficultyRange = 1..10),
        difficultyBounds = 1..10,
        onFiltersChange = {},
        onConfirm = {},
        onDismiss = {},
    )
}
