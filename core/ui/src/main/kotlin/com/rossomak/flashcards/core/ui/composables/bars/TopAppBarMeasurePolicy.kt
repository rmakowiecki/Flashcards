package com.rossomak.flashcards.core.ui.composables.bars

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import com.rossomak.flashcards.core.designsystem.theme.AppSpacing

internal enum class TopAppBarSlot {
    Backdrop,
    Navigation,
    Actions,
    TitleLeading,
    Title,
    Subtitle,

    /** [Title] and [Subtitle] at their expanded styles, measured for the block height and never placed. */
    TitleSizer,
    SubtitleSizer,
    Details,
}

/** Places the slots via [TopAppBarGeometry], reading [appBarState] in layout so a scroll never recomposes the bar. */
@OptIn(ExperimentalMaterial3Api::class)
internal class TopAppBarMeasurePolicy(
    private val appBarState: TopAppBarState,
    private val windowInsets: WindowInsets,
    private val size: FlashcardsTopAppBarSize,
    private val titleLeadingCollapsedScale: Float,
    private val subtitleWhenCollapsed: FlashcardsSubtitleWhenCollapsed,
    private val spacing: AppSpacing,
) : MeasurePolicy {

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val slots = measurables.associateBy { it.layoutId }
        val isLtr = layoutDirection == LayoutDirection.Ltr
        val insetLeft = windowInsets.getLeft(this, layoutDirection)
        val insetRight = windowInsets.getRight(this, layoutDirection)
        val insetStart = if (isLtr) insetLeft else insetRight
        val insetTop = windowInsets.getTop(this)
        val width = constraints.maxWidth
        val pinnedHeight = size.collapsedHeight.roundToPx()
        val metrics = TopAppBarMetrics(
            width = (width - insetLeft - insetRight).coerceAtLeast(0),
            pinnedHeight = pinnedHeight,
            minBlockHeight = size.expandedHeight.roundToPx() - pinnedHeight,
            edge = spacing.xxsmall.roundToPx(),
            padding = spacing.normal.roundToPx(),
            gap = spacing.xsmall.roundToPx(),
            bottomPadding = size.bottomPadding(spacing).roundToPx(),
        )

        val widthOnly = Constraints(maxWidth = metrics.width)
        val navigation = slots.getValue(TopAppBarSlot.Navigation).measure(widthOnly)
        val actions = slots.getValue(TopAppBarSlot.Actions).measure(widthOnly)
        val titleLeading = slots[TopAppBarSlot.TitleLeading]?.measure(widthOnly)
        val expandedText = Constraints(maxWidth = TopAppBarGeometry.expandedTextWidth(metrics, titleLeading?.slotSize()))
        val expandedTitle = slots.getValue(TopAppBarSlot.TitleSizer).measure(expandedText)
        val expandedSubtitle = slots[TopAppBarSlot.SubtitleSizer]?.measure(expandedText)
        val details = slots[TopAppBarSlot.Details]?.measure(Constraints(maxWidth = TopAppBarGeometry.detailsWidth(metrics)))

        val blockHeight = TopAppBarGeometry.blockHeight(
            metrics = metrics,
            titleLeading = titleLeading?.slotSize(),
            expandedTitle = expandedTitle.slotSize(),
            expandedSubtitle = expandedSubtitle?.slotSize(),
            details = details?.slotSize(),
        )
        val travel = updateTravel(blockHeight)

        val textWidths = TopAppBarGeometry.textWidths(
            metrics = metrics,
            navigation = navigation.slotSize(),
            actions = actions.slotSize(),
            titleLeading = titleLeading?.slotSize(),
            titleLeadingCollapsedScale = titleLeadingCollapsedScale,
            subtitleWhenCollapsed = subtitleWhenCollapsed,
            fraction = travel.fraction,
        )
        val title = slots.getValue(TopAppBarSlot.Title).measure(Constraints(maxWidth = textWidths.title))
        val subtitle = slots[TopAppBarSlot.Subtitle]?.measure(Constraints(maxWidth = textWidths.subtitle))

        val placements = TopAppBarGeometry.placements(
            metrics = metrics,
            slots = TopAppBarSlots(
                navigation = navigation.slotSize(),
                actions = actions.slotSize(),
                titleLeading = titleLeading?.slotSize(),
                titleLeadingCollapsedScale = titleLeadingCollapsedScale,
                expandedTitle = expandedTitle.slotSize(),
                expandedSubtitle = expandedSubtitle?.slotSize(),
                title = title.slotSize(),
                subtitle = subtitle?.slotSize(),
                details = details?.slotSize(),
                subtitleWhenCollapsed = subtitleWhenCollapsed,
            ),
            blockHeight = blockHeight,
            fraction = travel.fraction,
        )
        val backdrop = slots.getValue(TopAppBarSlot.Backdrop).measure(Constraints.fixed(width, insetTop + pinnedHeight + blockHeight))

        return layout(width, insetTop + placements.layoutHeight) {
            val origin = SlotOrigin(x = insetStart, y = insetTop, pivotX = if (isLtr) 0f else 1f)
            backdrop.place(0, 0)
            details?.let { placeSlot(it, placements.details, origin) }
            subtitle?.let { placeSlot(it, placements.subtitle, origin) }
            titleLeading?.let { placeSlot(it, placements.titleLeading, origin) }
            placeSlot(title, placements.title, origin)
            placeSlot(navigation, placements.navigation, origin)
            placeSlot(actions, placements.actions, origin)
        }
    }

    private fun updateTravel(blockHeight: Int): TopAppBarTravel {
        val previous = TopAppBarTravel(limit = appBarState.heightOffsetLimit, offset = appBarState.heightOffset)
        val travel = TopAppBarGeometry.travel(blockHeight, previous)
        if (travel != previous) {
            appBarState.heightOffsetLimit = travel.limit
            appBarState.heightOffset = travel.offset
        }
        return travel
    }

    /** The content's top-start corner, and the side a scaled slot shrinks toward. */
    private class SlotOrigin(val x: Int, val y: Int, val pivotX: Float)

    /** Scales about the start corner, so [placement] is where the shrunken slot's corner lands. */
    private fun Placeable.PlacementScope.placeSlot(placeable: Placeable, placement: SlotPlacement?, origin: SlotOrigin) {
        if (placement == null || !placement.isPlaced) return
        val x = origin.x + placement.x
        val y = origin.y + placement.y
        if (placement.scale == 1f && placement.alpha == 1f) {
            placeable.placeRelative(x, y)
        } else {
            placeable.placeRelativeWithLayer(x, y) {
                scaleX = placement.scale
                scaleY = placement.scale
                alpha = placement.alpha
                transformOrigin = TransformOrigin(pivotFractionX = origin.pivotX, pivotFractionY = 0f)
            }
        }
    }

    private fun Placeable.slotSize(): SlotSize = SlotSize(width = width, height = height)
}
