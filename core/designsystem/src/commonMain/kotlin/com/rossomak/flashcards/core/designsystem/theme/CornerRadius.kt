// Token-grouping file: AppCornerRadius plus its MaterialTheme accessor live together, matching Sizes.kt.
@file:Suppress("MatchingDeclarationName")

package com.rossomak.flashcards.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AppCornerRadius {
    val none: Dp = 0.dp
    val xsmall: Dp = 4.dp
    val small: Dp = 8.dp
    val normal: Dp = 12.dp

    /** Standard rounding for design-system cards, list groups, and list items (R14). */
    val card: Dp = 14.dp
    val medium: Dp = 16.dp
    val large: Dp = 24.dp
    val xlarge: Dp = 32.dp
    val full: Dp = 9999.dp
}

val MaterialTheme.cornerRadius: AppCornerRadius
    get() = AppCornerRadius
