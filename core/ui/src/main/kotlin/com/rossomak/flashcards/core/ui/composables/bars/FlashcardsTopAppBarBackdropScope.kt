package com.rossomak.flashcards.core.ui.composables.bars

import androidx.compose.runtime.Stable

/** What a [FlashcardsCollapsingTopAppBar]'s backdrop can read: how far the bar has collapsed, from `0` to `1`, read in layout or draw. */
@Stable
interface FlashcardsTopAppBarBackdropScope {
    val collapsedFraction: () -> Float
}

internal class TopAppBarBackdropScope(override val collapsedFraction: () -> Float) : FlashcardsTopAppBarBackdropScope
