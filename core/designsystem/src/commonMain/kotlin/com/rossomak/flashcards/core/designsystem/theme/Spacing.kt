// Token-grouping file: AppSpacing plus its MaterialTheme accessor live together, matching Sizes.kt.
@file:Suppress("MatchingDeclarationName")

package com.rossomak.flashcards.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AppSpacing {
    val none: Dp = 0.dp
    val xxsmall: Dp = 4.dp
    val xsmall: Dp = 8.dp
    val small: Dp = 12.dp
    val normal: Dp = 16.dp
    val medium: Dp = 24.dp
    val large: Dp = 32.dp
    val xlarge: Dp = 48.dp
    val xxlarge: Dp = 64.dp
}

val MaterialTheme.spacing: AppSpacing
    get() = AppSpacing
