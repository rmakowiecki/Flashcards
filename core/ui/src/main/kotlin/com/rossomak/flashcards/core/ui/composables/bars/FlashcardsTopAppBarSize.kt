package com.rossomak.flashcards.core.ui.composables.bars

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

/** Minimum expanded height on M3's classic tokens, pinned row included, status bar excluded. */
@OptIn(ExperimentalMaterial3Api::class)
enum class FlashcardsTopAppBarSize(
    val expandedHeight: Dp,
    internal val expandedTitleMaxLines: Int,
) {
    Medium(expandedHeight = TopAppBarDefaults.MediumAppBarExpandedHeight, expandedTitleMaxLines = 1),

    Large(expandedHeight = TopAppBarDefaults.LargeAppBarExpandedHeight, expandedTitleMaxLines = 2),
    ;

    val collapsedHeight: Dp get() = TopAppBarDefaults.LargeAppBarCollapsedHeight

    val titleStyle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = when (this) {
            Medium -> MaterialTheme.typography.headlineMediumEmphasized
            Large -> MaterialTheme.typography.displaySmallEmphasized
        }
}

/** What the subtitle does as the bar collapses. */
enum class FlashcardsSubtitleWhenCollapsed {
    /** Moves under the title into the pinned row. */
    Keep,

    /** Scrolls away with the block, fading out by half the collapse. */
    Fade,
}
