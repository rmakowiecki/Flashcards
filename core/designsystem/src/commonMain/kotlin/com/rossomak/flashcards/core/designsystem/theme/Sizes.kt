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

    /**
     * The heavier outline that marks the selected or current item on a themed surface: a selected
     * option card and the attempt indicator's current slot. Deliberately heavier than [hairline].
     */
    val selectedBorder: Dp = 1.5.dp

    /** Ring around a rating: the rating buttons and the attempt indicator's rated slots, where the theme draws one. */
    val ratingBorder: Dp = 1.5.dp

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
     * Track height of the linear and segmented progress bars at
     * [com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize.Normal]. The circular
     * indicators (the progress ring, the Daily Goal arc, the voice disc) set their own thinner stroke.
     */
    val progressBarThicknessNormal: Dp = 5.dp

    /** Progress-bar thickness at [com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize.Small]. */
    val progressBarThicknessSmall: Dp = 4.dp

    /**
     * Border of the outlined and tonal `Flashcards*Button`s and of an onboarding Favorite pick card;
     * other on-gradient borders are a [hairline].
     */
    val onGradientButtonBorder: Dp = 1.5.dp

    /** Blur radius behind every translucent on-gradient fill; `LocalBackdropBlurRadius` defaults to it. */
    val backdropBlurRadius: Dp = 4.dp

    /** Leading icon inside a [com.rossomak.flashcards.core.ui.composables.banners.FlashcardsXpBreakdownRow]. */
    val xpBreakdownRowIcon: Dp = 20.dp

    /** Width of the brand mark on the login screen and the onboarding cover, where it lands after flying in from splash. */
    val launchLogoWidth: Dp = 200.dp
}

val MaterialTheme.sizes: AppSizes
    get() = AppSizes
