package com.rossomak.flashcards.core.ui.composables.bars

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

/**
 * A [FlashcardsCollapsingTopAppBar]'s minimum expanded height on M3's classic tokens, below the status
 * bar and pinned row included. Both sizes collapse to the same pinned row.
 */
@OptIn(ExperimentalMaterial3Api::class)
enum class FlashcardsTopAppBarSize(
    val expandedHeight: Dp,
    internal val expandedTitleMaxLines: Int,
) {
    /** 112dp expanded. */
    Medium(expandedHeight = TopAppBarDefaults.MediumAppBarExpandedHeight, expandedTitleMaxLines = 1),

    /** 152dp expanded. */
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

/** What a [FlashcardsCollapsingTopAppBar]'s subtitle does as the bar collapses. */
enum class FlashcardsSubtitleWhenCollapsed {
    /** Travels under the title into the pinned row. */
    Keep,

    /** Scrolls up with the expanded block and fades out by half the collapse. */
    Fade,
}
