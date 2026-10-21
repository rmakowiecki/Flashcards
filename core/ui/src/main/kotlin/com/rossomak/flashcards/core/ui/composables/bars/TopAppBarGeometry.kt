package com.rossomak.flashcards.core.ui.composables.bars

import androidx.compose.ui.util.lerp
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsSubtitleWhenCollapsed.Fade
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsSubtitleWhenCollapsed.Keep
import kotlin.math.max
import kotlin.math.roundToInt

internal const val FADED_SLOTS_GONE_AT_FRACTION = 0.5f

internal fun fadedSlotAlpha(collapsedFraction: Float): Float = (1f - collapsedFraction / FADED_SLOTS_GONE_AT_FRACTION).coerceIn(0f, 1f)

internal data class SlotSize(val width: Int, val height: Int)

/** The bar's dimensions in px; [width] is inside the horizontal insets, heights exclude the status bar. */
internal data class TopAppBarMetrics(
    val width: Int,
    val pinnedHeight: Int,
    val minBlockHeight: Int,
    val navigationInset: Int,
    val contentPadding: Int,
    val itemGap: Int,
    val bottomPadding: Int,
)

/** [offset] runs from `0` (expanded) to [limit] (collapsed), negative px, as on `TopAppBarState`. */
internal data class TopAppBarTravel(val limit: Float, val offset: Float) {
    val fraction: Float get() = if (limit != 0f) offset / limit else 0f
}

/** [expandedTitle] and [expandedSubtitle] come from the expanded-style sizers; [title] and [subtitle] are the live nodes. */
internal data class TopAppBarSlots(
    val navigation: SlotSize,
    val actions: SlotSize,
    val titleLeading: SlotSize?,
    val titleLeadingCollapsedScale: Float,
    val expandedTitle: SlotSize,
    val expandedSubtitle: SlotSize?,
    val title: SlotSize,
    val subtitle: SlotSize?,
    val details: SlotSize?,
    val subtitleWhenCollapsed: FlashcardsSubtitleWhenCollapsed,
)

internal data class TopAppBarTextWidths(val title: Int, val subtitle: Int)

/** Relative to the content's top-start corner. */
internal data class SlotPlacement(val x: Int, val y: Int, val scale: Float = 1f, val alpha: Float = 1f) {
    val isPlaced: Boolean get() = alpha > 0f
}

/** [layoutHeight] excludes the status bar. */
internal data class TopAppBarPlacements(
    val layoutHeight: Int,
    val navigation: SlotPlacement,
    val actions: SlotPlacement,
    val titleLeading: SlotPlacement?,
    val title: SlotPlacement,
    val subtitle: SlotPlacement?,
    val details: SlotPlacement?,
)

/**
 * The collapsing bar's arithmetic, Compose-free for JVM tests. The measure policy calls it in this order:
 * [expandedTextWidth] and [detailsWidth], [blockHeight] and [travel], [textWidths], [placements].
 *
 * Expanded, the identity row (leading element, then title over subtitle) sits above the details,
 * bottom-aligned. Collapsed, the scaled leading element and the title follow the navigation icon and
 * end before the actions. A [Keep] subtitle travels under the title; a [Fade] subtitle and the details
 * scroll up and fade.
 */
internal object TopAppBarGeometry {

    fun expandedTextWidth(metrics: TopAppBarMetrics, titleLeading: SlotSize?): Int =
        (metrics.width - expandedTextStart(metrics, titleLeading) - metrics.contentPadding).coerceAtLeast(0)

    fun detailsWidth(metrics: TopAppBarMetrics): Int = (metrics.width - 2 * metrics.contentPadding).coerceAtLeast(0)

    fun blockHeight(
        metrics: TopAppBarMetrics,
        titleLeading: SlotSize?,
        expandedTitle: SlotSize,
        expandedSubtitle: SlotSize?,
        details: SlotSize?,
    ): Int = max(metrics.minBlockHeight, contentHeight(metrics, titleLeading, expandedTitle, expandedSubtitle, details))

    /**
     * A fully collapsed bar stays collapsed when the limit changes (also pulling a preview's overshoot back
     * to the limit); any other offset is clamped into the new range.
     */
    fun travel(blockHeight: Int, previous: TopAppBarTravel): TopAppBarTravel {
        val limit = -blockHeight.toFloat()
        return when {
            previous.limit == limit -> previous
            previous.fraction == 1f -> TopAppBarTravel(limit = limit, offset = limit)
            else -> TopAppBarTravel(limit = limit, offset = previous.offset.coerceIn(limit, 0f))
        }
    }

    /** A [Fade] subtitle keeps its expanded width. */
    fun textWidths(
        metrics: TopAppBarMetrics,
        navigation: SlotSize,
        actions: SlotSize,
        titleLeading: SlotSize?,
        titleLeadingCollapsedScale: Float,
        subtitleWhenCollapsed: FlashcardsSubtitleWhenCollapsed,
        fraction: Float,
    ): TopAppBarTextWidths {
        val start = textStart(metrics, navigation, titleLeading, titleLeadingCollapsedScale, fraction)
        val end = lerp(expandedTextEnd(metrics).toFloat(), collapsedTextEnd(metrics, actions).toFloat(), fraction)
        val titleWidth = (end - start).roundToInt().coerceAtLeast(0)
        val subtitleWidth = when (subtitleWhenCollapsed) {
            Keep -> titleWidth
            Fade -> expandedTextWidth(metrics, titleLeading)
        }
        return TopAppBarTextWidths(title = titleWidth, subtitle = subtitleWidth)
    }

    fun placements(
        metrics: TopAppBarMetrics,
        slots: TopAppBarSlots,
        blockHeight: Int,
        fraction: Float,
    ): TopAppBarPlacements = with(slots) {
        val collapsedOffset = (fraction * blockHeight).roundToInt()
        val identityRowHeight = identityRowHeight(titleLeading, expandedTitle, expandedSubtitle)
        val contentHeight = contentHeight(metrics, titleLeading, expandedTitle, expandedSubtitle, details)
        val identityTop = metrics.pinnedHeight + blockHeight - contentHeight
        val fadedAlpha = fadedSlotAlpha(fraction)

        val expandedTitleY = identityTop + (identityRowHeight - expandedTitle.height - (expandedSubtitle?.height ?: 0)) / 2
        val keptSubtitleHeight = if (subtitleWhenCollapsed == Keep) subtitle?.height ?: 0 else 0
        val collapsedTitleY = (metrics.pinnedHeight - title.height - keptSubtitleHeight) / 2
        val titleX = textStart(metrics, navigation, titleLeading, titleLeadingCollapsedScale, fraction).roundToInt()
        val titleY = lerp(expandedTitleY, collapsedTitleY, fraction)

        TopAppBarPlacements(
            layoutHeight = metrics.pinnedHeight + blockHeight - collapsedOffset,
            navigation = SlotPlacement(x = metrics.navigationInset, y = (metrics.pinnedHeight - navigation.height) / 2),
            actions = SlotPlacement(
                x = metrics.width - metrics.navigationInset - actions.width,
                y = (metrics.pinnedHeight - actions.height) / 2,
            ),
            titleLeading = titleLeading?.let { leading ->
                val collapsedY = ((metrics.pinnedHeight - leading.height * titleLeadingCollapsedScale) / 2).roundToInt()
                SlotPlacement(
                    x = lerp(metrics.contentPadding, collapsedContentStart(metrics, navigation), fraction),
                    y = lerp(identityTop + (identityRowHeight - leading.height) / 2, collapsedY, fraction),
                    scale = lerp(1f, titleLeadingCollapsedScale, fraction),
                )
            },
            title = SlotPlacement(x = titleX, y = titleY),
            subtitle = subtitle?.let {
                when (subtitleWhenCollapsed) {
                    Keep -> SlotPlacement(x = titleX, y = titleY + title.height)
                    Fade -> SlotPlacement(
                        x = expandedTextStart(metrics, titleLeading),
                        y = expandedTitleY + expandedTitle.height - collapsedOffset,
                        alpha = fadedAlpha,
                    )
                }
            },
            details = details?.let {
                SlotPlacement(
                    x = metrics.contentPadding,
                    y = identityTop + identityRowHeight + metrics.itemGap - collapsedOffset,
                    alpha = fadedAlpha,
                )
            },
        )
    }
}

private fun identityRowHeight(titleLeading: SlotSize?, expandedTitle: SlotSize, expandedSubtitle: SlotSize?): Int =
    max(titleLeading?.height ?: 0, expandedTitle.height + (expandedSubtitle?.height ?: 0))

private fun contentHeight(
    metrics: TopAppBarMetrics,
    titleLeading: SlotSize?,
    expandedTitle: SlotSize,
    expandedSubtitle: SlotSize?,
    details: SlotSize?,
): Int {
    val detailsHeight = details?.let { metrics.itemGap + it.height } ?: 0
    return identityRowHeight(titleLeading, expandedTitle, expandedSubtitle) + detailsHeight + metrics.bottomPadding
}

private fun expandedTextStart(metrics: TopAppBarMetrics, titleLeading: SlotSize?): Int =
    metrics.contentPadding + (titleLeading?.let { it.width + metrics.contentPadding } ?: 0)

private fun expandedTextEnd(metrics: TopAppBarMetrics): Int = metrics.width - metrics.contentPadding

private fun collapsedContentStart(metrics: TopAppBarMetrics, navigation: SlotSize): Int =
    if (navigation.width > 0) metrics.navigationInset + navigation.width + metrics.itemGap else metrics.contentPadding

private fun collapsedTextStart(
    metrics: TopAppBarMetrics,
    navigation: SlotSize,
    titleLeading: SlotSize?,
    titleLeadingCollapsedScale: Float,
): Float = collapsedContentStart(metrics, navigation) + (titleLeading?.let { it.width * titleLeadingCollapsedScale + metrics.itemGap } ?: 0f)

private fun collapsedTextEnd(metrics: TopAppBarMetrics, actions: SlotSize): Int =
    if (actions.width > 0) metrics.width - metrics.navigationInset - actions.width - metrics.itemGap else expandedTextEnd(metrics)

private fun textStart(
    metrics: TopAppBarMetrics,
    navigation: SlotSize,
    titleLeading: SlotSize?,
    titleLeadingCollapsedScale: Float,
    fraction: Float,
): Float = lerp(
    start = expandedTextStart(metrics, titleLeading).toFloat(),
    stop = collapsedTextStart(metrics, navigation, titleLeading, titleLeadingCollapsedScale),
    fraction = fraction,
)
