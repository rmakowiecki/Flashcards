package com.rossomak.flashcards.core.ui.composables.bars

import androidx.compose.runtime.Stable

/** [collapsedFraction] runs `0` to `1`; read it in layout or draw. */
@Stable
interface FlashcardsTopAppBarBackdropScope {
    val collapsedFraction: () -> Float
}

internal class TopAppBarBackdropScope(override val collapsedFraction: () -> Float) : FlashcardsTopAppBarBackdropScope
