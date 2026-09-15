package com.rossomak.flashcards.core.ui.composables.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.R
import com.rossomak.flashcards.core.ui.composables.FlashcardsDifficultyRangePill
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTonalButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.spacing

/**
 * One adjustable setting in a settings sheet: `label … value [Edit]`, one row among several
 * stacked with plain spacing between them (no divider — see below). Deliberately not built on
 * [FlashcardsListRow]: that component is sized (64dp min height, `titleMedium` title) for a
 * full, independently-navigable list row, and a settings sheet packs many of these into a
 * height-capped [com.rossomak.flashcards.core.ui.composables.FlashcardsBottomSheet] where every
 * extra dp of row height is a row the sheet can't show without scrolling. This row is its own,
 * intrinsically-sized `Row` instead, typeset at the same `titleSmall`/`labelMedium` pairing
 * [FlashcardsDetailedListRow] and [FlashcardsSelectableListRow] already use for their own
 * title/count text — the compact scale a details/settings-style row reads at elsewhere in the
 * app (e.g. Category Details' subcategory rows), rather than the larger scale a primary navigation
 * row's title needs.
 *
 * **The Edit button is the tap target, not the row.** This deliberately departs from
 * (docs/adr/0030)'s "whole row is the tap target" framing: [onClick] is wired to a real, fully
 * interactive [FlashcardsTonalButton] — not a decorative hint — so it is the row's one and only
 * clickable element, with its own accessible label and click semantics. Tapping the row outside
 * the button does nothing.
 *
 * **Edit is always fully visible; [label] is always one line; [value] gets everything [label]
 * doesn't use, flush against Edit.** The row is two nested `Row`s, not three flat siblings. The
 * *outer* Row has exactly one weighted child — an inner `label`+[value] `Row` (`weight(1f)`) — with
 * Edit as its only other, unweighted sibling. Since Compose's `Row` measures every unweighted child
 * at its own full intrinsic width regardless of what else shares the row, three flat
 * unweighted-then-weighted siblings (an earlier version of this row) let a long `label` and Edit
 * each independently claim close to the *entire* row width before their sum was ever checked against
 * it — [value] could be squeezed to nothing, or the row could overflow outright. Nesting
 * `label`+[value] inside their own weighted segment reserves Edit's width unconditionally at the
 * outer level first (`Row`'s unweighted-children pass always runs before its weighted one,
 * regardless of source order), so the inner segment can never claim more than what's left over.
 *
 * *Inside* that inner segment, [label] carries no `weight` at all and [value] is the sole
 * `weight(1f, fill = true)` child — deliberately not two weighted siblings sharing a static
 * proportional split. Giving both a `weight` (an intermediate version of this fix tried
 * `weight(1f, fill = false)` on each) computes each one's share from the weight ratio *before either
 * is measured for actual content* — so a short [label] (e.g. "Voice") still capped [value] at a
 * fixed fraction of the row, wasting the space [label] never used and ellipsizing [value] far sooner
 * than the available room justified (a real, reproduced regression of that version). Compose's `Row`
 * instead measures every truly unweighted child (bounded only by the *inner segment's own* width,
 * never past it) for its actual size *first*, then hands the single weighted child *exactly* what's
 * left — so a short [label] leaves [value] nearly the whole segment, and a long one still leaves
 * [value] whatever's genuinely left over, never a static cap unrelated to [label]'s real size.
 * `fill = true` also keeps [value]'s box flush against the inner segment's own trailing edge on every
 * row deterministically: consuming *exactly* the remainder (not merely up to it) means
 * `label + value` always sums to the segment's full width, so nothing "drifts" the way two weighted
 * siblings did. [label] is fixed at `maxLines = 1` (unlike an intermediate version, which allowed 2
 * and could still visibly wrap) — ellipsizing past the inner segment's own width, in the one
 * genuinely pathological case where it alone would overflow the segment, beats wrapping the row
 * disproportionately taller than its siblings in the stack.
 *
 * No [androidx.compose.material3.HorizontalDivider] beneath the row (unlike the row this replaced
 * — matching the design reference, which separates settings with space, not rules): the caller
 * stacks rows with its own `Arrangement.spacedBy`.
 *
 * [value] is a composable slot, not a `String`, because not every setting's current value is text:
 * a Filters row needs to render a tag count next to [FlashcardsDifficultyRangePill], which a
 * `String` can't carry. Pass [valueText] instead for the common plain-text case and leave [value]
 * at its default — the call site stays a one-liner. Supplying [value] directly overrides
 * [valueText] entirely.
 *
 * @param label What the setting is (e.g. "Mode", "Filters").
 * @param onClick Opens the setting's editor. Fires from the Edit button only.
 * @param valueText The setting's current value as plain text, rendered muted by the default
 *   [value]. Ignored once [value] is overridden.
 * @param value The setting's current value. Defaults to muted [valueText] text; override for
 *   non-text values.
 */
@Composable
fun FlashcardsSettingRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueText: String? = null,
    value: @Composable () -> Unit = {
        if (valueText != null) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    },
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier.weight(1f, fill = true),
                contentAlignment = Alignment.CenterEnd,
            ) {
                value()
            }
        }
        FlashcardsTonalButton(
            text = stringResource(R.string.common_edit_button),
            onClick = onClick,
            size = FlashcardsComponentSize.Small,
            icon = Icons.Default.Edit,
        )
    }
}

@ShowkaseComposable(name = "Setting row", group = "Lists")
@Composable
fun FlashcardsSettingRowShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsSettingRow(label = "Mode", valueText = "Rated", onClick = {})
        }
    }
}

@ShowkaseComposable(name = "Setting row — custom value", group = "Lists")
@Composable
fun FlashcardsSettingRowCustomValueShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsSettingRow(label = "Filters", onClick = {}, value = { FiltersSampleValue() })
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsSettingRowPreview() {
    FlashcardsTheme {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                FlashcardsSettingRow(label = "Mode", valueText = "Rated", onClick = {})
                FlashcardsSettingRow(label = "Length", valueText = "18 cards", onClick = {})
                FlashcardsSettingRow(
                    label = "Voice playback settings",
                    valueText = "English (United States) · 1.25x",
                    onClick = {},
                )
                FlashcardsSettingRow(label = "Filters", onClick = {}, value = { FiltersSampleValue() })
            }
        }
    }
}

/** Sample non-text [FlashcardsSettingRow.value]: a tag count next to [FlashcardsDifficultyRangePill]. */
@Composable
private fun FiltersSampleValue() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
    ) {
        Text(
            text = "2 tags",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlashcardsDifficultyRangePill(lowLevel = 2, highLevel = 4)
    }
}
