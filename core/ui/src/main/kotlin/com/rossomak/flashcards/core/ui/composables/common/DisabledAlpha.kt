package com.rossomak.flashcards.core.ui.composables.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha

private const val DISABLED_ALPHA = 0.6f

fun Modifier.disabledAlpha(enabled: Boolean): Modifier = alpha(if (enabled) 1f else DISABLED_ALPHA)
