package com.rossomak.flashcards.core.ui.composables.bars

import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsSubtitleWhenCollapsed.Fade
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsSubtitleWhenCollapsed.Keep
import io.kotest.matchers.shouldBe
import org.junit.Test

/** Every size is in px at a density of 1, so the numbers read as the dp they stand for. */
class TopAppBarGeometryTest {

    private val medium = TopAppBarMetrics(
        width = 400,
        pinnedHeight = 64,
        minBlockHeight = 48,
        navigationInset = 4,
        contentPadding = 16,
        itemGap = 8,
        bottomPadding = 12,
    )
    private val large = medium.copy(minBlockHeight = 88, bottomPadding = 16)

    private val iconButton = SlotSize(width = 48, height = 48)
    private val noActions = SlotSize(width = 0, height = 0)
    private val mediumTitle = SlotSize(width = 120, height = 36)
    private val largeTitle = SlotSize(width = 120, height = 44)
    private val collapsedTitle = SlotSize(width = 100, height = 28)
    private val subtitle = SlotSize(width = 140, height = 24)
    private val collapsedSubtitle = SlotSize(width = 120, height = 20)
    private val avatar = SlotSize(width = 88, height = 88)
    private val collapsedAvatarSize = 32
    private val avatarCollapsedScale = collapsedAvatarSize.toFloat() / avatar.height
    private val details = SlotSize(width = 200, height = 40)

    private val collapsedContentStart = medium.navigationInset + iconButton.width + medium.itemGap

    private val titleOnlySlots = TopAppBarSlots(
        navigation = iconButton,
        actions = iconButton,
        titleLeading = null,
        titleLeadingCollapsedScale = 1f,
        expandedTitle = mediumTitle,
        expandedSubtitle = null,
        title = mediumTitle,
        subtitle = null,
        details = null,
        subtitleWhenCollapsed = Keep,
    )

    private fun TopAppBarSlots.blockHeight(metrics: TopAppBarMetrics): Int =
        TopAppBarGeometry.blockHeight(metrics, titleLeading, expandedTitle, expandedSubtitle, details)

    private fun TopAppBarSlots.placementsAt(metrics: TopAppBarMetrics, fraction: Float): TopAppBarPlacements =
        TopAppBarGeometry.placements(metrics, this, blockHeight(metrics), fraction)

    @Test
    fun `a Medium bar with a one-line title is 112 expanded and 64 collapsed`() {
        val collapsed = titleOnlySlots.copy(title = collapsedTitle)

        titleOnlySlots.placementsAt(medium, fraction = 0f).layoutHeight shouldBe medium.pinnedHeight + medium.minBlockHeight
        collapsed.placementsAt(medium, fraction = 1f).layoutHeight shouldBe medium.pinnedHeight
    }

    @Test
    fun `a Large bar with a one-line title and a subtitle is 152 expanded and 64 collapsed`() {
        val expanded = titleOnlySlots.copy(expandedTitle = largeTitle, title = largeTitle, expandedSubtitle = subtitle, subtitle = subtitle)
        val collapsed = expanded.copy(title = collapsedTitle, subtitle = collapsedSubtitle)

        expanded.placementsAt(large, fraction = 0f).layoutHeight shouldBe large.pinnedHeight + large.minBlockHeight
        collapsed.placementsAt(large, fraction = 1f).layoutHeight shouldBe large.pinnedHeight
    }

    @Test
    fun `block height grows past the token for a two-line Large title and a subtitle`() {
        val twoLineTitle = SlotSize(width = 360, height = 88)
        val slots = titleOnlySlots.copy(expandedTitle = twoLineTitle, title = twoLineTitle, expandedSubtitle = subtitle, subtitle = subtitle)

        val blockHeight = slots.blockHeight(large)

        blockHeight shouldBe twoLineTitle.height + subtitle.height + large.bottomPadding
        TopAppBarGeometry.travel(blockHeight, TopAppBarTravel(limit = -Float.MAX_VALUE, offset = 0f)).limit shouldBe -blockHeight.toFloat()
        slots.placementsAt(large, fraction = 0f).layoutHeight shouldBe large.pinnedHeight + blockHeight
    }

    @Test
    fun `block height grows past the token at font scale 2`() {
        val doubledTitle = SlotSize(width = 240, height = 72)
        val slots = titleOnlySlots.copy(expandedTitle = doubledTitle, title = doubledTitle)

        slots.blockHeight(medium) shouldBe doubledTitle.height + medium.bottomPadding
    }

    @Test
    fun `block height comes from the expanded measurement, not the live title`() {
        val slots = titleOnlySlots.copy(title = collapsedTitle)

        slots.blockHeight(medium) shouldBe titleOnlySlots.blockHeight(medium)
    }

    @Test
    fun `title travels from the block's start into the pinned row after the navigation icon`() {
        val halfwayTitle = SlotSize(width = 110, height = 32)

        titleOnlySlots.placementsAt(medium, fraction = 0f).title shouldBe SlotPlacement(x = medium.contentPadding, y = medium.pinnedHeight)
        titleOnlySlots.copy(title = halfwayTitle).placementsAt(medium, fraction = 0.5f).title shouldBe SlotPlacement(x = 38, y = 40)
        titleOnlySlots.copy(title = collapsedTitle).placementsAt(medium, fraction = 1f).title shouldBe
            SlotPlacement(x = collapsedContentStart, y = (medium.pinnedHeight - collapsedTitle.height) / 2)
    }

    @Test
    fun `title leading element shrinks into the pinned row and the title follows it`() {
        val nameTitle = SlotSize(width = 200, height = 32)
        val slots = titleOnlySlots.copy(
            titleLeading = avatar,
            titleLeadingCollapsedScale = avatarCollapsedScale,
            expandedTitle = nameTitle,
            title = nameTitle,
        )

        val expanded = slots.placementsAt(large, fraction = 0f)
        val halfway = slots.placementsAt(large, fraction = 0.5f)
        val collapsed = slots.placementsAt(large, fraction = 1f)

        expanded.titleLeading shouldBe SlotPlacement(x = large.contentPadding, y = large.pinnedHeight, scale = 1f)
        expanded.title.x shouldBe large.contentPadding + avatar.width + large.contentPadding
        halfway.titleLeading shouldBe SlotPlacement(x = 38, y = 40, scale = (1f + avatarCollapsedScale) / 2)
        collapsed.titleLeading shouldBe
            SlotPlacement(x = collapsedContentStart, y = (large.pinnedHeight - collapsedAvatarSize) / 2, scale = avatarCollapsedScale)
        collapsed.title.x shouldBe collapsedContentStart + collapsedAvatarSize + large.itemGap
    }

    @Test
    fun `a kept subtitle travels under the title and the pair centres in the pinned row`() {
        val slots = titleOnlySlots.copy(
            expandedSubtitle = subtitle,
            title = collapsedTitle,
            subtitle = collapsedSubtitle,
        )

        val collapsed = slots.placementsAt(medium, fraction = 1f)

        val pairTop = (medium.pinnedHeight - collapsedTitle.height - collapsedSubtitle.height) / 2

        collapsed.title shouldBe SlotPlacement(x = collapsedContentStart, y = pairTop)
        collapsed.subtitle shouldBe SlotPlacement(x = collapsedContentStart, y = pairTop + collapsedTitle.height)
    }

    @Test
    fun `a fading subtitle scrolls up with the block and is unplaced from halfway`() {
        val slots = titleOnlySlots.copy(expandedSubtitle = subtitle, subtitle = subtitle, subtitleWhenCollapsed = Fade)
        val blockHeight = slots.blockHeight(medium)
        val expandedY = slots.placementsAt(medium, fraction = 0f).subtitle?.y ?: error("subtitle missing")

        val quarter = slots.placementsAt(medium, fraction = 0.25f).subtitle

        quarter shouldBe SlotPlacement(x = medium.contentPadding, y = expandedY - blockHeight / 4, alpha = 0.5f)
        slots.placementsAt(medium, fraction = 0.5f).subtitle?.isPlaced shouldBe false
        slots.placementsAt(medium, fraction = 1f).subtitle?.isPlaced shouldBe false
    }

    @Test
    fun `subtitle mode leaves the block height unchanged`() {
        val kept = titleOnlySlots.copy(expandedSubtitle = subtitle, subtitle = subtitle)
        val faded = kept.copy(subtitleWhenCollapsed = Fade)

        faded.blockHeight(large) shouldBe kept.blockHeight(large)
    }

    @Test
    fun `details scroll up with the block, fade, and still count toward its height once unplaced`() {
        val slots = titleOnlySlots.copy(expandedTitle = largeTitle, title = largeTitle, details = details)
        val blockHeight = slots.blockHeight(large)

        val expanded = slots.placementsAt(large, fraction = 0f).details
        val quarter = slots.placementsAt(large, fraction = 0.25f).details

        val expandedY = large.pinnedHeight + largeTitle.height + large.itemGap

        blockHeight shouldBe largeTitle.height + large.itemGap + details.height + large.bottomPadding
        expanded shouldBe SlotPlacement(x = large.contentPadding, y = expandedY)
        quarter shouldBe SlotPlacement(x = large.contentPadding, y = expandedY - blockHeight / 4, alpha = 0.5f)
        slots.placementsAt(large, fraction = 0.5f).details?.isPlaced shouldBe false
    }

    @Test
    fun `an unchanged limit keeps the travel as it is`() {
        val blockHeight = 48
        val previous = TopAppBarTravel(limit = -blockHeight.toFloat(), offset = -20f)

        TopAppBarGeometry.travel(blockHeight, previous) shouldBe previous
    }

    @Test
    fun `a collapsed bar stays collapsed when the block grows or shrinks`() {
        val initialBlockHeight = 88
        val grownBlockHeight = 128
        val shrunkBlockHeight = 64
        val collapsed = TopAppBarTravel(limit = -initialBlockHeight.toFloat(), offset = -initialBlockHeight.toFloat())

        TopAppBarGeometry.travel(grownBlockHeight, collapsed) shouldBe
            TopAppBarTravel(limit = -grownBlockHeight.toFloat(), offset = -grownBlockHeight.toFloat())
        TopAppBarGeometry.travel(shrunkBlockHeight, collapsed) shouldBe
            TopAppBarTravel(limit = -shrunkBlockHeight.toFloat(), offset = -shrunkBlockHeight.toFloat())
    }

    @Test
    fun `a partly collapsed bar keeps its offset, clamped into the new range`() {
        val partlyOffset = -60f
        val grownBlockHeight = 128
        val shrunkBlockHeight = 48
        val partly = TopAppBarTravel(limit = -88f, offset = partlyOffset)

        TopAppBarGeometry.travel(grownBlockHeight, partly) shouldBe TopAppBarTravel(limit = -grownBlockHeight.toFloat(), offset = partlyOffset)
        TopAppBarGeometry.travel(shrunkBlockHeight, partly) shouldBe
            TopAppBarTravel(limit = -shrunkBlockHeight.toFloat(), offset = -shrunkBlockHeight.toFloat())
    }

    @Test
    fun `an offset past any limit is pulled back to exactly collapsed`() {
        val preview = TopAppBarTravel(limit = -Float.MAX_VALUE, offset = -Float.MAX_VALUE)

        val blockHeight = 88

        TopAppBarGeometry.travel(blockHeight, preview) shouldBe TopAppBarTravel(limit = -blockHeight.toFloat(), offset = -blockHeight.toFloat())
    }

    @Test
    fun `absent leading element, subtitle and details are not placed and leave the title at the padding`() {
        val placements = titleOnlySlots.placementsAt(medium, fraction = 0f)

        placements.titleLeading shouldBe null
        placements.subtitle shouldBe null
        placements.details shouldBe null
        placements.title.x shouldBe medium.contentPadding
    }

    @Test
    fun `without a navigation icon the collapsed title starts at the padding`() {
        val slots = titleOnlySlots.copy(navigation = noActions, title = collapsedTitle)

        slots.placementsAt(medium, fraction = 1f).title.x shouldBe medium.contentPadding
    }

    @Test
    fun `collapsed title narrows to end before the actions`() {
        val expanded = TopAppBarGeometry.textWidths(medium, iconButton, iconButton, null, 1f, Keep, fraction = 0f)
        val collapsed = TopAppBarGeometry.textWidths(medium, iconButton, iconButton, null, 1f, Keep, fraction = 1f)
        val withoutActions = TopAppBarGeometry.textWidths(medium, iconButton, noActions, null, 1f, Keep, fraction = 1f)

        val collapsedEnd = medium.width - medium.navigationInset - iconButton.width - medium.itemGap

        expanded.title shouldBe medium.width - 2 * medium.contentPadding
        collapsed.title shouldBe collapsedEnd - collapsedContentStart
        withoutActions.title shouldBe medium.width - medium.contentPadding - collapsedContentStart
    }

    @Test
    fun `a fading subtitle keeps its expanded width as the title narrows`() {
        val widths = TopAppBarGeometry.textWidths(medium, iconButton, iconButton, avatar, avatarCollapsedScale, Fade, fraction = 1f)

        widths.subtitle shouldBe TopAppBarGeometry.expandedTextWidth(medium, avatar)
    }
}
