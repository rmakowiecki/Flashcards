package com.rossomak.flashcards.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.ui.composables.FlashcardsInlineCategoryGlyph
import com.rossomak.flashcards.core.ui.composables.FlashcardsProgressRing
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsIconButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsChevron
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsListGroupItem
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.browse.details.category.SubcategoryProgress
import kotlin.math.roundToInt

private const val PROGRESS_PERCENT_SCALE = 100

/** The ring's fill, `0f` for a subcategory with no cards rather than dividing by zero. */
private fun SubcategoryProgress.Resolved.studiedFraction(cardCount: Int): Float =
    if (cardCount > 0) studiedCount / cardCount.toFloat() else 0f

/**
 * Names Studied, never a number in the unknown state — mirrors CategoryDetailsScreen's own ring
 * content description, own key per ADR-0023 (see the `browse_search_topic_progress_*` strings).
 */
@Composable
internal fun SubcategoryProgress.searchRingContentDescription(cardCount: Int): String = when (this) {
    SubcategoryProgress.Unresolved -> stringResource(R.string.browse_search_topic_progress_unavailable_cd)
    is SubcategoryProgress.Resolved -> stringResource(
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
 * [SearchResultSubtitle]'s leading glyph. The separator itself is `R.string.browse_middle_dot_separator`,
 * shared with [CategoryDetailsRowSubtitle] and the chip line in BrowseScreen. [separator] is
 * resolved once by the caller and passed down rather than re-resolved per subcategory.
 */
@Composable
internal fun Subcategory.searchResultCardsStudiedText(progress: SubcategoryProgress, separator: String): AnnotatedString {
    val cardCountLabel = pluralStringResource(R.plurals.browse_card_count_label, cardCount, cardCount)
    val studiedCount = (progress as? SubcategoryProgress.Resolved)?.studiedCount ?: 0
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
 * `"[glyph] · <cards> · <studied>"`, forced to one line, ellipsized on overflow — never a marquee;
 * that's the title's job, same reasoning as [CategoryDetailsRowSubtitle]. The glyph stands in for
 * the parent category name entirely (no "in Category" text, no separate label) — that's the whole
 * point of the glyph, reclaiming the line's space for [text]; [categoryName] only reaches a screen
 * reader via [FlashcardsInlineCategoryGlyph]'s contentDescription, never rendered visibly.
 */
@Composable
private fun SearchResultSubtitle(iconSvg: String?, categoryName: String, text: AnnotatedString) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlashcardsInlineCategoryGlyph(
            iconSvg = iconSvg,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            contentDescription = categoryName,
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

internal fun Subcategory.toSearchResultListGroupItem(
    progress: SubcategoryProgress,
    ringContentDescription: String,
    cardsStudiedText: AnnotatedString,
    iconSvg: String?,
    startSessionContentDescription: String,
    onSubcategoryClick: (Subcategory) -> Unit,
    onSubcategorySessionStart: (Subcategory) -> Unit,
): FlashcardsListGroupItem {
    val subcategory = this
    val ringFraction = (progress as? SubcategoryProgress.Resolved)?.studiedFraction(subcategory.cardCount)
    return FlashcardsListGroupItem.Row(
        key = subcategory.id,
        title = subcategory.name,
        secondaryContent = {
            SearchResultSubtitle(iconSvg = iconSvg, categoryName = subcategory.categoryName, text = cardsStudiedText)
        },
        onClick = { onSubcategoryClick(subcategory) },
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
                FlashcardsIconButton(
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
