package com.rossomak.flashcards.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressState
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.composables.FlashcardsInlineCategoryGlyph
import com.rossomak.flashcards.core.ui.composables.FlashcardsProgressRing
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTonalIconButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsChevron
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsListRow
import com.rossomak.flashcards.core.ui.theme.rememberCategoryColor
import kotlin.math.roundToInt

private const val PROGRESS_PERCENT_SCALE = 100

/**
 * Names Studied, never a number in the unknown state — mirrors CategoryDetailsScreen's own ring
 * content description, own key per ADR-0023 (see the `browse_search_topic_progress_*` strings).
 */
@Composable
internal fun SubcategoryProgressState.searchRingContentDescription(cardCount: Int): String = when (this) {
    SubcategoryProgressState.Unresolved -> stringResource(R.string.browse_search_topic_progress_unavailable_cd)
    is SubcategoryProgressState.Resolved -> stringResource(
        R.string.browse_search_topic_progress_cd,
        (studiedFraction(cardCount) * PROGRESS_PERCENT_SCALE).roundToInt(),
    )
}

/**
 * `"<total> cards · <studied>"` once resolved with a nonzero studied count; `"<total> cards"`
 * alone for a subcategory never studied — mirrors [CategoryDetailsRowSubtitle]'s composition, the
 * segment dropped rather than shown as "0 studied". A not-yet-resolved [progress] renders the same
 * as never-studied rather than a dashed placeholder, same simplification CategoryDetailsScreen's
 * subtitle makes (see its `studiedLabel`), since a search result that resolves to zero moments
 * later looks identical anyway. The parent category itself is no longer named here as text — see
 * [SearchResultSubtitle]'s leading glyph. The separator itself is `CoreUiR.string.common_middle_dot_separator`,
 * shared with [CategoryDetailsRowSubtitle] and the chip line in BrowseScreen.
 */
@Composable
internal fun Subcategory.searchResultCardsStudiedText(progress: SubcategoryProgressState, separator: String): AnnotatedString {
    val cardCountLabel = pluralStringResource(R.plurals.browse_card_count_label, cardCount, cardCount)
    val studiedCount = (progress as? SubcategoryProgressState.Resolved)?.studiedCount ?: 0
    if (studiedCount <= 0) return buildAnnotatedString { append(cardCountLabel) }

    val studiedText = stringResource(R.string.category_details_topic_studied_label, studiedCount)
    val studiedColor = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        append(cardCountLabel)
        append(separator)
        withStyle(SpanStyle(color = studiedColor)) { append(studiedText) }
    }
}

/**
 * `"[glyph] <cards> · <studied>"` on one line. The parent's glyph, in its color, stands in for its
 * name, which only a screen reader hears.
 */
@Composable
private fun SearchResultSubtitle(parentCategory: Category, text: AnnotatedString) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlashcardsInlineCategoryGlyph(
            iconSvg = parentCategory.iconSvg,
            tint = rememberCategoryColor(hex = parentCategory.color, fallback = MaterialTheme.colorScheme.onSurfaceVariant),
            contentDescription = parentCategory.name,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One matched Subcategory: progress ring, name, [SearchResultSubtitle], play button and chevron. */
@Composable
internal fun SubcategorySearchResultRow(
    modifier: Modifier,
    subcategory: Subcategory,
    parentCategory: Category,
    progress: SubcategoryProgressState,
    isFavorited: Boolean,
    onSubcategoryClick: (Subcategory) -> Unit,
    onSubcategorySessionStart: (Subcategory) -> Unit,
) {
    val ringFraction = (progress as? SubcategoryProgressState.Resolved)?.studiedFraction(subcategory.cardCount)
    val ringContentDescription = progress.searchRingContentDescription(subcategory.cardCount)
    val cardsStudiedText = subcategory.searchResultCardsStudiedText(
        progress = progress,
        separator = stringResource(CoreUiR.string.common_middle_dot_separator),
    )
    val startSessionContentDescription = stringResource(R.string.browse_search_start_session_cd, subcategory.name)
    FlashcardsListRow(
        modifier = modifier,
        title = subcategory.name,
        secondaryContent = {
            SearchResultSubtitle(parentCategory = parentCategory, text = cardsStudiedText)
        },
        onClick = { onSubcategoryClick(subcategory) },
        isFavorited = isFavorited,
        leading = {
            FlashcardsProgressRing(
                progress = ringFraction,
                contentDescription = ringContentDescription,
            )
        },
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlashcardsTonalIconButton(
                    icon = Icons.Default.PlayArrow,
                    contentDescription = startSessionContentDescription,
                    onClick = { onSubcategorySessionStart(subcategory) },
                    size = FlashcardsComponentSize.Small,
                )
                FlashcardsChevron()
            }
        },
    )
}
