package com.rossomak.flashcards.core.ui.composables.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha

/** Opacity of a component that is not [enabled][disabledAlpha], per the design's disabled rows and cards. */
private const val DISABLED_ALPHA = 0.6f

/** Dims the whole component when it is disabled, so every row and card reads as disabled the same way. */
fun Modifier.disabledAlpha(enabled: Boolean): Modifier = alpha(if (enabled) 1f else DISABLED_ALPHA)
