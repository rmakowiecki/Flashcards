package com.rossomak.flashcards.core.ui.composables.lists

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import com.rossomak.flashcards.core.ui.theme.sizes

private const val FAVORITE_ICON_INLINE_ID = "favorite"
private const val ROW_TITLE_MAX_LINES = 3

/**
 * Row title text shared by every list-row shape that supports [isFavorited]: same style/color/
 * line-cap everywhere, and — when favorited — a small bookmark glyph appended as inline text
 * content so it flows with the last word rather than living in its own layout slot. Earlier lines
 * of a wrapped title are never shortened to make room for it; only the last line reflows around
 * the glyph, exactly like an extra word would.
 *
 * The glyph is decorative only (no content description) — the favorited state isn't narrated to
 * screen readers from here, to avoid it repeating on every row of a long list.
 */
@Composable
internal fun FlashcardsRowTitleText(
    title: String,
    isFavorited: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!isFavorited) {
        Text(
            text = title,
            modifier = modifier,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = ROW_TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    val iconSizeSp = with(LocalDensity.current) { MaterialTheme.sizes.favoriteInlineIcon.toSp() }
    val annotatedTitle = buildAnnotatedString {
        append(title)
        append('\u00A0') // add non-breaking space to never orphan the favorite icon
        appendInlineContent(FAVORITE_ICON_INLINE_ID)
    }
    val inlineContent = remember(iconSizeSp) {
        mapOf(
            FAVORITE_ICON_INLINE_ID to InlineTextContent(
                placeholder = Placeholder(
                    width = iconSizeSp,
                    height = iconSizeSp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = null, // decorative only, no a11y cd in order to not clutter TalkBack
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )
    }
    Text(
        text = annotatedTitle,
        modifier = modifier,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = ROW_TITLE_MAX_LINES,
        overflow = TextOverflow.Ellipsis,
        inlineContent = inlineContent,
    )
}
