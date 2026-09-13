// Token-grouping file: AppSizes plus its MaterialTheme accessor and border helper live together,
// matching the sibling Spacing.kt / CornerRadius.kt convention.
@file:Suppress("MatchingDeclarationName")

package com.rossomak.flashcards.core.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Component dimensions (heights, fixed sizes, border widths) — distinct from [AppSpacing],
 * which owns gaps and padding. Read via [MaterialTheme.sizes] inside composables.
 */
object AppSizes {
    /** Minimum height of a single-line list row (rows grow for multi-line content). */
    val listRowMinHeight: Dp = 64.dp

    /** Rounded, tinted leading icon container used in settings and category rows. */
    val iconTile: Dp = 40.dp

    /** 1px hairline used for card borders and dividers. */
    val hairline: Dp = 1.dp

    /** An inline progress spinner sized to sit in a text button's place in an app bar. */
    val inlineProgress: Dp = 20.dp

    /** Stroke of an [inlineProgress] spinner — heavier than the M3 default at this diameter. */
    val inlineProgressStroke: Dp = 2.dp

    /** Height of the checkable tag chip (M3 filter chip, one size only). */
    val tagChipHeight: Dp = 40.dp

    /** Leading check icon inside a selected tag chip. */
    val tagChipIcon: Dp = 18.dp

    /** Outline width of an unselected tag chip — deliberately heavier than [hairline]. */
    val tagChipBorder: Dp = 1.5.dp

    /** Height of the metadata badge — the smaller, non-checkable stats pill. */
    val metadataBadgeHeight: Dp = 32.dp

    /** Leading icon inside a metadata badge. */
    val metadataBadgeIcon: Dp = 16.dp

    /** Height of a [metadataBadgeHeight] badge in its compact variant (flat card-row tags). */
    val metadataBadgeHeightCompact: Dp = 24.dp

    /** Leading icon inside a compact metadata badge. */
    val metadataBadgeIconCompact: Dp = 14.dp

    /** Diameter of the circular difficulty badge (leading element of flashcard rows). */
    val difficultyBadge: Dp = 24.dp

    /** Height of the difficulty range pill (filters, Study Creation summaries). */
    val difficultyRangePillHeight: Dp = 24.dp

    /** Height of a [com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize.Normal] button. */
    val buttonHeightNormal: Dp = 56.dp

    /**
     * Height of a [com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize.Small] button.
     * Matches M3's `ButtonDefaults.MinHeight` so it doesn't fight the min-height floor built into
     * the M3 `Button`/`FilledTonalButton`/`OutlinedButton`/`TextButton` family.
     */
    val buttonHeightSmall: Dp = 40.dp

    /** Diameter of the circular self-rating button (Failed / Partial / Correct). */
    val ratingButton: Dp = 56.dp

    /** Diameter of the circular mastery progress ring used as a leading element of topic rows. */
    val progressRing: Dp = 40.dp

    /** Stroke width of the [progressRing] track and its filled arc. */
    val progressRingStroke: Dp = 3.dp

    // Button icon size isn't a design-system token: it's androidx.compose.material3.ButtonDefaults.IconSize
    // (18dp), referenced directly in FlashcardsButtonMetrics.kt. Both size tiers share that one M3
    // constant, so there's nothing tier-specific to define here.

    /**
     * Track height (linear/segmented) and stroke width (circular) at
     * [com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize.Normal] — one
     * shared thickness token for every `Flashcards*Progress*` composable, since the design uses
     * the same value for both.
     */
    val progressBarThicknessNormal: Dp = 5.dp

    /** Progress-bar thickness at [com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize.Small]. */
    val progressBarThicknessSmall: Dp = 4.dp

    /** Diameter of [com.rossomak.flashcards.core.ui.composables.progress.FlashcardsCircularProgressRing] at `Normal` ("Continue learning" card). */
    val progressRingDiameterNormal: Dp = 56.dp

    /** Diameter of [com.rossomak.flashcards.core.ui.composables.progress.FlashcardsCircularProgressRing] at `Small` (list rows). */
    val progressRingDiameterSmall: Dp = 40.dp

    /** Larger border width of any component drawn on a brand gradient. */
    val onGradientBorder: Dp = 1.5.dp

    /** Leading icon inside a [com.rossomak.flashcards.core.ui.composables.banners.FlashcardsInfoBanner]. */
    val infoBannerIcon: Dp = 16.dp

    /** Leading icon inside a [com.rossomak.flashcards.core.ui.composables.banners.FlashcardsXpBreakdownRow]. */
    val xpBreakdownRowIcon: Dp = 20.dp

    /** Side of one slot in a [com.rossomak.flashcards.core.ui.composables.FlashcardsAttemptIndicator]. */
    val attemptIndicatorSlot: Dp = 20.dp

    /** Rating icon inside a graded [attemptIndicatorSlot]. */
    val attemptIndicatorIcon: Dp = 12.dp

    /** Diameter of the tinted circle behind a [com.rossomak.flashcards.core.ui.composables.FlashcardsEmptyState]'s icon. */
    val emptyStateIconCircle: Dp = 80.dp

    /** Diameter of a [com.rossomak.flashcards.core.ui.composables.FlashcardsIconCircle]. */
    val iconCircle: Dp = 72.dp

    /** Glyph size inside [emptyStateIconCircle]. */
    val emptyStateIcon: Dp = 40.dp

    /** Diameter of the striped placeholder avatar on [com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCard]. */
    val levelCardAvatar: Dp = 56.dp
}

val MaterialTheme.sizes: AppSizes
    get() = AppSizes

/**
 * The standard 1px hairline border for design-system cards and list groups, tinted with
 * [androidx.compose.material3.ColorScheme.outlineVariant].
 */
val MaterialTheme.hairlineBorder: BorderStroke
    @Composable
    @ReadOnlyComposable
    get() = BorderStroke(width = sizes.hairline, color = colorScheme.outlineVariant)
