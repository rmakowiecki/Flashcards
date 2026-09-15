package com.rossomak.flashcards.feature.onboarding.step

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.ui.composables.FlashcardsScrollFadeHeight
import com.rossomak.flashcards.core.ui.composables.flashcardsGridScrollFade
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.onboarding.R
import com.rossomak.flashcards.feature.onboarding.component.FavoriteTopicCard
import com.rossomak.flashcards.feature.onboarding.component.OnboardingStepHeader
import com.rossomak.flashcards.feature.onboarding.model.FavoriteTopicOption
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

private const val FAVORITE_GRID_COLUMNS = 2

/**
 * Target height of the topic grid — about 3.5 rows, so a peek of the next row shows under the
 * bottom fade and hints the grid scrolls. [FavoritesStepLayout] only grants this in full when the
 * header leaves enough room; on a short screen or a large font scale it shrinks the grid instead
 * of letting the step overflow.
 */
private val FavoriteGridHeight = 372.dp

/**
 * Lets the user pin subcategories from a short, curated list as favorites
 *
 * Unlike every other step this one scrolls a grid rather than a column, so it hosts its own
 * [LazyVerticalGrid] instead of the shared [com.rossomak.flashcards.feature.onboarding.component.OnboardingStepColumn].
 */
@Composable
internal fun FavoritesStep(
    options: ImmutableList<FavoriteTopicOption>,
    selectedIds: ImmutableSet<String>,
    onTopicToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FavoritesStepLayout(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MaterialTheme.spacing.medium),
        spacing = MaterialTheme.spacing.medium,
        header = {
            OnboardingStepHeader(
                eyebrow = stringResource(R.string.favorites_eyebrow_label),
                headline = stringResource(R.string.favorites_headline_title),
                message = stringResource(R.string.favorites_intro_message),
            )
        },
        grid = {
            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(FAVORITE_GRID_COLUMNS),
                modifier = Modifier
                    .fillMaxSize()
                    .flashcardsGridScrollFade(gridState),
                contentPadding = PaddingValues(bottom = FlashcardsScrollFadeHeight),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
            ) {
                items(items = options, key = { option -> option.id }) { option ->
                    FavoriteTopicCard(
                        name = option.name,
                        categoryName = option.categoryName,
                        icon = option.categoryName.categoryIcon(),
                        selected = option.id in selectedIds,
                        onSelectedChange = { onTopicToggle(option.id) },
                    )
                }
            }
        },
    )
}

/**
 * Measures [header] first at its natural width and height, then gives [grid] whatever height is
 * left, up to [FavoriteGridHeight], before centering the pair vertically as one block, [spacing]
 * apart.
 *
 * A plain `Column` can't express this: it either sizes the grid to a fixed [FavoriteGridHeight]
 * regardless of how much room the header actually used (the original bug — a compact screen or a
 * large font scale grows the header until it, and the grid below it, no longer fit and clip), or
 * it sizes the grid to fill all remaining space (the header's own problem — the pager centers this
 * step's content as one block, so an unbounded grid inflates that block tall enough to crowd the
 * header up against the segmented progress bar above it). Measuring the header first and handing
 * the grid the true remainder avoids both.
 */
@Composable
private fun FavoritesStepLayout(
    header: @Composable () -> Unit,
    grid: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = MaterialTheme.spacing.medium,
) {
    Layout(
        contents = listOf(header, grid),
        modifier = modifier,
    ) { (headerMeasurables, gridMeasurables), constraints ->
        val spacingPx = spacing.roundToPx()

        val headerPlaceable = headerMeasurables.first().measure(
            constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity),
        )

        // What is left is a hard ceiling, not a preference: holding the grid to any floor of its
        // own once the header has eaten the screen would place it past the bottom edge and clip
        // it, which is the overflow this layout exists to prevent. The grid scrolls, so losing
        // rows off its end is the milder failure.
        val remainingForGrid = (constraints.maxHeight - headerPlaceable.height - spacingPx)
            .coerceAtLeast(0)
        val gridHeightPx = remainingForGrid.coerceAtMost(FavoriteGridHeight.roundToPx())
        val gridPlaceable = gridMeasurables.first().measure(
            Constraints.fixed(constraints.maxWidth, gridHeightPx),
        )

        val totalHeight = headerPlaceable.height + spacingPx + gridPlaceable.height
        val top = ((constraints.maxHeight - totalHeight) / 2).coerceAtLeast(0)

        layout(constraints.maxWidth, constraints.maxHeight) {
            headerPlaceable.placeRelative((constraints.maxWidth - headerPlaceable.width) / 2, top)
            gridPlaceable.placeRelative(0, top + headerPlaceable.height + spacingPx)
        }
    }
}

/**
 * Placeholder glyph mapping. Categories carry their own remote icon (`Category.iconSvg`), but this
 * step runs on a hardcoded option list that has no Category attached yet.
 */
// TODO(favorites): drop this in favour of Category.iconSvg once real subcategories are fetched.
private fun String.categoryIcon(): ImageVector = when (this) {
    "Android" -> Icons.Default.Android
    "iOS" -> Icons.Default.PhoneIphone
    "Python" -> Icons.Default.DataObject
    else -> Icons.Default.Code
}
