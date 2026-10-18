package com.rossomak.flashcards.core.ui.composables.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.core.ui.R
import com.rossomak.flashcards.core.ui.composables.FlashcardsIntRangeSlider
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme

/**
 * Picks how many Subcategories a Quick Session randomly samples its pool from (ADR-0040).
 *
 * A [RangeSlider] rather than [SessionLengthDialog]'s stepper — the same widget
 * [FlashcardFiltersDialog] already uses for its Difficulty facet — since this edits a low/high
 * pair, not a single value. Deferred commit like every dialog on this scaffold: the slider edits
 * [draft] and nothing is applied until [onConfirm].
 *
 * Pass [keepAsDefault] as non-null where the choice is session-scoped and can optionally be
 * promoted to a permanent default; pass `null` on the Settings screen, where the change is
 * permanent by definition.
 */
@Composable
fun SubcategoryCountRangeDialog(
    draft: IntRange,
    bounds: IntRange,
    onDraftChange: (IntRange) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    keepAsDefault: Boolean? = null,
    onKeepAsDefaultChange: (Boolean) -> Unit = {},
) {
    FlashcardsSingleActionDialog(
        title = stringResource(R.string.subcategory_count_range_dialog_title),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier,
        supportingText = stringResource(R.string.subcategory_count_range_dialog_message),
        keepAsDefault = keepAsDefault,
        onKeepAsDefaultChange = onKeepAsDefaultChange,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
        ) {
            Text(
                text = stringResource(R.string.subcategory_count_range_value_label, draft.first, draft.last),
                style = MaterialTheme.typography.labelLarge,
            )
            FlashcardsIntRangeSlider(
                value = draft,
                bounds = bounds,
                onValueChange = onDraftChange,
            )
        }
    }
}

@ShowkaseComposable(name = "Subcategory count range dialog", group = "Dialogs")
@Preview
@Composable
fun SubcategoryCountRangeDialogShowcase() {
    FlashcardsTheme {
        Surface {
            SubcategoryCountRangeDialog(
                draft = 3..5,
                bounds = 1..5,
                onDraftChange = {},
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
}

@Preview
@Composable
private fun SubcategoryCountRangeDialogSessionPreview() {
    SubcategoryCountRangeDialog(
        draft = 3..5,
        bounds = 1..5,
        onDraftChange = {},
        onConfirm = {},
        onDismiss = {},
        keepAsDefault = false,
    )
}
