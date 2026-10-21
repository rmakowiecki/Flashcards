package com.rossomak.flashcards.core.ui.composables.bars

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

/**
 * The height of a [FlashcardsCollapsingTopAppBar], on M3's classic app bar tokens. Each height is the
 * bar's total below the status bar, pinned row included, and both sizes collapse to the same pinned
 * row. The expanded block grows past its token rather than clip when the content needs more room.
 */
@OptIn(ExperimentalMaterial3Api::class)
enum class FlashcardsTopAppBarSize(
    /** The expanded height, pinned row included. */
    val expandedHeight: Dp,
    /** How many lines the high-level bar's title may wrap to while expanded. */
    internal val expandedTitleMaxLines: Int,
) {
    /** 112dp expanded. */
    Medium(expandedHeight = TopAppBarDefaults.MediumAppBarExpandedHeight, expandedTitleMaxLines = 1),

    /** 152dp expanded. */
    Large(expandedHeight = TopAppBarDefaults.LargeAppBarExpandedHeight, expandedTitleMaxLines = 2),
    ;

    /** The pinned row's height, the same for both sizes. */
    val collapsedHeight: Dp get() = TopAppBarDefaults.LargeAppBarCollapsedHeight

    /** The title's expanded text style. */
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

    /** Stays where it was, scrolls up with the expanded block and fades out halfway through the collapse. */
    Fade,
}
