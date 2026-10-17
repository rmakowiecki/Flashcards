// Token-grouping file: AppSizes plus its MaterialTheme accessor and border helper live together,
// matching the sibling Spacing.kt / CornerRadius.kt convention.
@file:Suppress("MatchingDeclarationName")

package com.rossomak.flashcards.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
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

    /** Outline width of an unselected tag chip — deliberately heavier than [hairline]. */
    val tagChipBorder: Dp = 1.5.dp

    /** Leading icon inside a metadata badge. */
    val metadataBadgeIcon: Dp = 16.dp

    /** Leading icon inside a compact metadata badge. */
    val metadataBadgeIconCompact: Dp = 14.dp

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

    /** Border of every `Flashcards*Button`, `FlashcardsOptionCard` and `FavoriteSubcategoryCard`; other on-gradient borders are a [hairline]. */
    val onGradientButtonBorder: Dp = 1.5.dp

    /** Blur radius behind every translucent on-gradient fill; `LocalBackdropBlurRadius` defaults to it. */
    val backdropBlurRadius: Dp = 20.dp

    /** Leading icon inside a [com.rossomak.flashcards.core.ui.composables.banners.FlashcardsXpBreakdownRow]. */
    val xpBreakdownRowIcon: Dp = 20.dp
}

val MaterialTheme.sizes: AppSizes
    get() = AppSizes
