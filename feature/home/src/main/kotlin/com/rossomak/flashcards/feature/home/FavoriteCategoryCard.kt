package com.rossomak.flashcards.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.ui.composables.FlashcardsInlineCategoryGlyph
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.core.ui.theme.toCategoryColorOrNull

private val WatermarkSize = 96.dp

/** Opacity of the Category glyph watermark over the card. */
private const val WATERMARK_ALPHA = 0.12f

/**
 * A Favorite Category: its name in the Category's color, how many topics it holds, a faint large
 * watermark of its icon, and a Quick session button.
 *
 * The body (name and topic count) is one button; the Quick session button is a separate node with its
 * own description, so a screen reader announces them apart.
 *
 * @param parallaxOffsetPx horizontal shift of the watermark in pixels. It is read while drawing, never
 * during composition, so a scrolling row moves the watermark without recomposing the card.
 */
@Composable
internal fun FavoriteCategoryCard(
    category: Category,
    parallaxOffsetPx: () -> Float,
    onClick: () -> Unit,
    onQuickSessionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categoryColor = remember(category.color) { category.color.toCategoryColorOrNull() }
        ?: MaterialTheme.colorScheme.primary
    val watermarkTint = remember(categoryColor) { categoryColor.copy(alpha = WATERMARK_ALPHA) }
    val quickSessionDescription = stringResource(R.string.favorites_category_quick_session_cd, category.name)

    FavoriteCardContainer(modifier = modifier) {
        FlashcardsInlineCategoryGlyph(
            iconSvg = category.iconSvg,
            tint = watermarkTint,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = MaterialTheme.spacing.small, y = -MaterialTheme.spacing.xsmall)
                .graphicsLayer { translationX = parallaxOffsetPx() },
            size = WatermarkSize,
            reserveSpaceWhileLoading = false,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(MaterialTheme.spacing.normal),
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = categoryColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.favorites_category_topics, category.subcategoryCount, category.subcategoryCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FlashcardsFilledButton(
            text = stringResource(R.string.favorites_category_quick_session_button),
            onClick = onQuickSessionClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(MaterialTheme.spacing.normal)
                .semantics { contentDescription = quickSessionDescription },
            size = FlashcardsComponentSize.Small,
            icon = Icons.Filled.Bolt,
        )
    }
}

@PreviewLightDark
@Composable
private fun FavoriteCategoryCardPreview() {
    FlashcardsTheme {
        FavoriteCategoryCard(
            category = Category(
                id = "android",
                name = "Android",
                order = 0,
                subcategoryCount = 14,
                iconSvg = null,
                color = "#2B6AA5",
                featuredSubcategoryNames = emptyList(),
            ),
            parallaxOffsetPx = { 0f },
            onClick = {},
            onQuickSessionClick = {},
        )
    }
}
