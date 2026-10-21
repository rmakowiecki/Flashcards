package com.rossomak.flashcards.core.ui.composables.bars

import androidx.compose.ui.util.lerp
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsSubtitleWhenCollapsed.Fade
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsSubtitleWhenCollapsed.Keep
import kotlin.math.max
import kotlin.math.roundToInt

/** Collapse fraction by which the faded slots (a [Fade] subtitle and the details) are invisible, unplaced and silent. */
internal const val FADED_SLOTS_GONE_AT_FRACTION = 0.5f

/** A faded slot's alpha at [collapsedFraction]: fully shown expanded, gone by [FADED_SLOTS_GONE_AT_FRACTION]. */
internal fun fadedSlotAlpha(collapsedFraction: Float): Float = (1f - collapsedFraction / FADED_SLOTS_GONE_AT_FRACTION).coerceIn(0f, 1f)

/** A measured slot's size, in px. */
internal data class SlotSize(val width: Int, val height: Int)

/**
 * The bar's fixed dimensions, in px. [width] is the content width inside the horizontal insets, and
 * every height excludes the status-bar inset. [minBlockHeight] is the size's expanded height less
 * [pinnedHeight]: the expanded block never gets shorter, and grows past it when its content needs room.
 */
internal data class TopAppBarMetrics(
    val width: Int,
    val pinnedHeight: Int,
    val minBlockHeight: Int,
    val edge: Int,
    val padding: Int,
    val gap: Int,
    val bottomPadding: Int,
)

/**
 * How far the bar has travelled: [offset] runs from `0` (expanded) to [limit] (collapsed), both
 * negative px, as on `TopAppBarState`. [fraction] is worked out the same way the state does.
 */
internal data class TopAppBarTravel(val limit: Float, val offset: Float) {
    val fraction: Float get() = if (limit != 0f) offset / limit else 0f
}

/**
 * The measured slots. [expandedTitle] and [expandedSubtitle] are measured at the expanded text styles
 * and the expanded width, so the block height they give never moves with the fraction; [title] and
 * [subtitle] are the live nodes, measured at the current, lerped styles.
 */
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

/** The widths the live title and subtitle are measured at. */
internal data class TopAppBarTextWidths(val title: Int, val subtitle: Int)

/** Where a slot goes, relative to the content's top-start corner. A slot at [alpha] `0` is left unplaced. */
internal data class SlotPlacement(val x: Int, val y: Int, val scale: Float = 1f, val alpha: Float = 1f) {
    val isPlaced: Boolean get() = alpha > 0f
}

/** Every slot's placement, and the bar's height below the status bar. */
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
 * The collapsing top app bar's arithmetic, kept free of Compose so it can be tested on the JVM. The
 * measure policy calls it in four steps, because each step needs what the one before measured:
 *
 * 1. [expandedTextWidth] and [detailsWidth] size the fraction-independent measurements.
 * 2. [blockHeight] and [travel] turn those into the travel limit and the corrected offset.
 * 3. [textWidths] sizes the live title and subtitle at the corrected fraction.
 * 4. [placements] places everything.
 *
 * Expanded, the block under the pinned row holds an identity row (the title leading element, then the
 * title over the subtitle, centred against it) with the details under it, bottom-aligned above
 * [TopAppBarMetrics.bottomPadding]. Collapsed, the title leading element, scaled, follows the
 * navigation icon in the pinned row and the title follows it, narrowed to end before the actions. A
 * [Keep] subtitle travels under the title and the two are centred as a pair; a [Fade] subtitle and the
 * details stay where they were, scroll up with the block and fade out.
 */
internal object TopAppBarGeometry {

    /** The width the title and subtitle get in the expanded identity row. */
    fun expandedTextWidth(metrics: TopAppBarMetrics, titleLeading: SlotSize?): Int =
        (metrics.width - expandedTextStart(metrics, titleLeading) - metrics.padding).coerceAtLeast(0)

    /** The width the details get under the identity row. */
    fun detailsWidth(metrics: TopAppBarMetrics): Int = (metrics.width - 2 * metrics.padding).coerceAtLeast(0)

    /** The expanded block's height: the content's, but never less than [TopAppBarMetrics.minBlockHeight]. */
    fun blockHeight(
        metrics: TopAppBarMetrics,
        titleLeading: SlotSize?,
        expandedTitle: SlotSize,
        expandedSubtitle: SlotSize?,
        details: SlotSize?,
    ): Int = max(metrics.minBlockHeight, contentHeight(metrics, titleLeading, expandedTitle, expandedSubtitle, details))

    /**
     * The travel for a block [blockHeight] tall, given the state's [previous] travel. An unchanged
     * limit keeps [previous] as it is. A changed one keeps a fully collapsed bar fully collapsed, which
     * also pulls a preview's offset past any limit back to exactly collapsed, and clamps any other
     * offset into the new range.
     */
    fun travel(blockHeight: Int, previous: TopAppBarTravel): TopAppBarTravel {
        val limit = -blockHeight.toFloat()
        return when {
            previous.limit == limit -> previous
            previous.fraction == 1f -> TopAppBarTravel(limit = limit, offset = limit)
            else -> TopAppBarTravel(limit = limit, offset = previous.offset.coerceIn(limit, 0f))
        }
    }

    /** The live title's and subtitle's widths at [fraction]. A [Fade] subtitle keeps its expanded width. */
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
            navigation = SlotPlacement(x = metrics.edge, y = (metrics.pinnedHeight - navigation.height) / 2),
            actions = SlotPlacement(
                x = metrics.width - metrics.edge - actions.width,
                y = (metrics.pinnedHeight - actions.height) / 2,
            ),
            titleLeading = titleLeading?.let { leading ->
                val collapsedY = ((metrics.pinnedHeight - leading.height * titleLeadingCollapsedScale) / 2).roundToInt()
                SlotPlacement(
                    x = lerp(metrics.padding, collapsedContentStart(metrics, navigation), fraction),
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
                    x = metrics.padding,
                    y = identityTop + identityRowHeight + metrics.gap - collapsedOffset,
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
    val detailsHeight = details?.let { metrics.gap + it.height } ?: 0
    return identityRowHeight(titleLeading, expandedTitle, expandedSubtitle) + detailsHeight + metrics.bottomPadding
}

private fun expandedTextStart(metrics: TopAppBarMetrics, titleLeading: SlotSize?): Int =
    metrics.padding + (titleLeading?.let { it.width + metrics.padding } ?: 0)

private fun expandedTextEnd(metrics: TopAppBarMetrics): Int = metrics.width - metrics.padding

/** Where the collapsed row's content starts: after the navigation icon, or at the padding without one. */
private fun collapsedContentStart(metrics: TopAppBarMetrics, navigation: SlotSize): Int =
    if (navigation.width > 0) metrics.edge + navigation.width + metrics.gap else metrics.padding

private fun collapsedTextStart(
    metrics: TopAppBarMetrics,
    navigation: SlotSize,
    titleLeading: SlotSize?,
    titleLeadingCollapsedScale: Float,
): Float = collapsedContentStart(metrics, navigation) + (titleLeading?.let { it.width * titleLeadingCollapsedScale + metrics.gap } ?: 0f)

/** Where the collapsed title ends: before the actions, or at the padding without any. */
private fun collapsedTextEnd(metrics: TopAppBarMetrics, actions: SlotSize): Int =
    if (actions.width > 0) metrics.width - metrics.edge - actions.width - metrics.gap else expandedTextEnd(metrics)

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
