@file:Suppress("MatchingDeclarationName")

package com.rossomak.flashcards.core.ui.composables.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import com.rossomak.flashcards.core.designsystem.theme.brandColors
import com.rossomak.flashcards.core.designsystem.theme.cornerRadius
import com.rossomak.flashcards.core.designsystem.theme.sizes
import com.rossomak.flashcards.core.designsystem.theme.spacing
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsListItemPosition.Bottom
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsListItemPosition.Middle
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsListItemPosition.Single
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsListItemPosition.Top

/**
 * Where a row sits within a grouped list, so a stack of independent `LazyColumn` items reads
 * as one card: only the outer corners round, and interior seams get a 1dp gap.
 */
enum class FlashcardsListItemPosition {

    Single, // The only row in the group — all four corners rounded, no gap
    Top, // First row — top corners rounded, gap below
    Middle, // Interior row — no rounding, gap below
    Bottom; // Last row — bottom corners rounded, no gap

    companion object {
        /** Resolves the position of item [index] in a list of [count] items. */
        fun of(index: Int, count: Int): FlashcardsListItemPosition = when {
            count <= 1 -> Single
            index == 0 -> Top
            index == count - 1 -> Bottom
            else -> Middle
        }
    }
}

/**
 * Insets a list item by a 1dp gap on every edge but the group's trailing one ([Bottom]/[Single],
 * which have no following row) and clips it to the right corners for its [position]. The gap is
 * just [flashcardsListGroupContainer]'s background showing through that 1dp strip — there's no
 * divider drawn. [position] is the only input: this modifier only pads and clips, it never paints
 * a background — each row composable paints its own, so a row's color (including any per-row
 * visual state, e.g. a checkable row rendering itself differently while selected) stays that
 * composable's own implementation detail.
 */
@Composable
fun Modifier.flashcardsListItemShape(
    position: FlashcardsListItemPosition,
): Modifier {
    val largeCorner = MaterialTheme.cornerRadius.card
    val smallCorner = MaterialTheme.cornerRadius.xsmall
    val shape = when (position) {
        Single -> RoundedCornerShape(largeCorner)
        Top -> RoundedCornerShape(topStart = largeCorner, topEnd = largeCorner, bottomStart = smallCorner, bottomEnd = smallCorner)
        Middle -> RoundedCornerShape(smallCorner)
        Bottom -> RoundedCornerShape(bottomStart = largeCorner, bottomEnd = largeCorner, topStart = smallCorner, topEnd = smallCorner)
    }
    val paddingValues = PaddingValues(
        top = MaterialTheme.sizes.hairline,
        start = MaterialTheme.sizes.hairline,
        end = MaterialTheme.sizes.hairline,
        bottom = if (position == Bottom || position == Single) MaterialTheme.sizes.hairline else MaterialTheme.spacing.none,
    )
    return this
        .padding(paddingValues)
        .clip(shape)
}

/**
 * The group background a screen wraps around its grouped `LazyColumn` section, so individually
 * shaped [flashcardsListItemShape] rows appear inside one rounded card and the 1dp gaps between
 * unchecked rows show this color rather than a drawn divider line. It is
 * [com.rossomak.flashcards.core.designsystem.theme.BrandColors.listGroupContainer], which a selected
 * row also fills with (see [FlashcardsSelectableListRow]).
 */
@Composable
fun Modifier.flashcardsListGroupContainer(): Modifier = this
    .clip(RoundedCornerShape(MaterialTheme.cornerRadius.medium)) // needs to be 2dp higher than the items background corner to maintain border width
    .background(MaterialTheme.brandColors.listGroupContainer)

/**
 * [flashcardsListGroupContainer] for a `LazyColumn` holding one group: rounded ends scroll with
 * the rows, an end out of view is cut straight. Under a pinned header the top row's own corner
 * can briefly show as it leaves; accepted.
 */
@Composable
fun Modifier.flashcardsListGroupContainer(listState: LazyListState): Modifier {
    val cornerRadius = MaterialTheme.cornerRadius.medium
    val backgroundColor = MaterialTheme.brandColors.listGroupContainer
    return drawBehind {
        val layoutInfo = listState.layoutInfo
        val firstVisible = layoutInfo.visibleItemsInfo.firstOrNull() ?: return@drawBehind
        val lastVisible = layoutInfo.visibleItemsInfo.last()
        val startsInView = firstVisible.index == 0
        val endsInView = lastVisible.index == layoutInfo.totalItemsCount - 1
        // Item offsets count from the end of the top content padding; viewportStartOffset is minus that padding.
        val top = if (startsInView) (firstVisible.offset - layoutInfo.viewportStartOffset).toFloat() else 0f
        val bottom = if (endsInView) (lastVisible.offset + lastVisible.size - layoutInfo.viewportStartOffset).toFloat() else size.height
        val radiusPx = cornerRadius.toPx()
        val topCorner = if (startsInView) CornerRadius(radiusPx) else CornerRadius.Zero
        val bottomCorner = if (endsInView) CornerRadius(radiusPx) else CornerRadius.Zero
        val groupOutline = RoundRect(
            left = 0f,
            top = top,
            right = size.width,
            bottom = bottom,
            topLeftCornerRadius = topCorner,
            topRightCornerRadius = topCorner,
            bottomRightCornerRadius = bottomCorner,
            bottomLeftCornerRadius = bottomCorner,
        )
        clipRect { drawPath(path = Path().apply { addRoundRect(groupOutline) }, color = backgroundColor) }
    }
}
