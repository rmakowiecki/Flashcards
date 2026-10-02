package com.rossomak.flashcards.feature.home

import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.LayoutDirection
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.FavoriteItem
import com.rossomak.flashcards.core.domain.model.FavoriteItem.FavoriteCategory
import com.rossomak.flashcards.core.domain.model.FavoriteItem.FavoriteSubcategory
import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressSummary
import com.rossomak.flashcards.core.domain.model.subcategoryProgressFor
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.spacing
import java.time.Instant

/** How far the watermark drifts, as a share of the card width, when its card is one card-step from the snap position. */
private const val PARALLAX_FACTOR = 0.3f

/** Taps do nothing until Favorites navigation is wired; one shared instance keeps every card's callbacks stable. */
private val NoOpClick: () -> Unit = {}

/**
 * The Favorites section: a heading over a snapping row of [FavoriteItem] cards, most recently
 * favorited first.
 *
 * Items are keyed by kind and id and typed by kind, so a Category and a Subcategory that share an
 * id never collide and a card is only reused for its own kind. Each Subcategory's progress is
 * resolved here, per item, so a progress change recomposes only the cards whose own
 * [com.rossomak.flashcards.core.domain.model.SubcategoryProgressState] changed.
 *
 * @param listState the row's scroll state. The default is saveable, so the position survives tab
 * switches and process death.
 */
@Composable
internal fun FavoritesCarousel(
    items: List<FavoriteItem>,
    progressSummary: ProgressSummary?,
    isProgressResolved: Boolean,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.favorites_section_title),
            modifier = Modifier
                .padding(horizontal = MaterialTheme.spacing.normal)
                .padding(bottom = MaterialTheme.spacing.small)
                .semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.normal),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Start),
        ) {
            items(
                items = items,
                key = { item -> item.carouselKey() },
                contentType = { item -> item::class },
            ) { item ->
                val cardModifier = Modifier.animateItem()
                when (item) {
                    is FavoriteCategory -> FavoriteCategoryCard(
                        category = item.category,
                        parallaxOffsetPx = rememberParallaxOffsetPx(listState, item.carouselKey()),
                        onClick = NoOpClick,
                        onQuickSessionClick = NoOpClick,
                        modifier = cardModifier,
                    )
                    is FavoriteSubcategory -> FavoriteSubcategoryCard(
                        subcategory = item.subcategory,
                        parentCategory = item.parentCategory,
                        progress = progressSummary.subcategoryProgressFor(item.subcategory.id, isProgressResolved),
                        onClick = NoOpClick,
                        onPlayClick = NoOpClick,
                        modifier = cardModifier,
                    )
                }
            }
        }
    }
}

private fun FavoriteItem.carouselKey(): String = when (this) {
    is FavoriteCategory -> "category:${category.id}"
    is FavoriteSubcategory -> "subcategory:${subcategory.id}"
}

/**
 * A watermark offset for the card keyed [itemKey], opposite to the scroll: zero at the snap position,
 * up to [PARALLAX_FACTOR] of the card width one card-step away. Mirrored in right-to-left layouts.
 *
 * The returned lambda reads [listState]'s layout when called, so it must only be called while
 * drawing (a `graphicsLayer` block): then scrolling invalidates the layer, never the composition.
 * It is remembered, so the card it is handed to stays skippable.
 */
@Composable
private fun rememberParallaxOffsetPx(listState: LazyListState, itemKey: String): () -> Float {
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val cardSpacing = MaterialTheme.spacing.small
    return remember(listState, itemKey, density, isRtl, cardSpacing) {
        val cardWidthPx = with(density) { FavoriteCardWidth.toPx() }
        val cardStepPx = cardWidthPx + with(density) { cardSpacing.toPx() }
        val direction = if (isRtl) -1f else 1f
        val offsetPx: () -> Float = {
            val layoutInfo = listState.layoutInfo
            val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { visibleItem -> visibleItem.key == itemKey }
            val distanceInCardSteps = itemInfo
                ?.let { visibleItem -> (visibleItem.offset - layoutInfo.beforeContentPadding) / cardStepPx }
                ?.coerceIn(-1f, 1f)
                ?: 0f
            -distanceInCardSteps * cardWidthPx * PARALLAX_FACTOR * direction
        }
        offsetPx
    }
}

@PreviewLightDark
@Composable
private fun FavoritesCarouselPreview() {
    val category = Category(
        id = "android",
        name = "Android",
        order = 0,
        subcategoryCount = 14,
        iconSvg = null,
        color = "#2B6AA5",
        featuredSubcategoryNames = emptyList(),
    )
    val subcategory = Subcategory(
        id = "compose",
        name = "Compose",
        categoryId = category.id,
        categoryName = category.name,
        order = 0,
        cardCount = 30,
    )
    val favoritedAt = Instant.parse("2026-05-05T10:00:00Z")
    FlashcardsTheme {
        FavoritesCarousel(
            items = listOf(
                FavoriteSubcategory(subcategory, category, favoritedAt),
                FavoriteCategory(category, favoritedAt),
            ),
            progressSummary = ProgressSummary(
                subcategories = mapOf(subcategory.id to SubcategoryProgressSummary(masteredCount = 10, studiedCount = 25)),
            ),
            isProgressResolved = true,
        )
    }
}
