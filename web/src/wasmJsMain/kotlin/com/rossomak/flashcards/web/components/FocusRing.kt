package com.rossomak.flashcards.web.components

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import com.rossomak.flashcards.web.theme.WebColors
import com.rossomak.flashcards.web.theme.WebDimensions

/** A ring in the shape of [shape] while the focusable below it, such as a `clickable`, has focus, so a keyboard user sees where they are. */
@Composable
fun Modifier.focusRing(shape: Shape): Modifier {
    var focused by remember { mutableStateOf(false) }
    return onFocusChanged { state -> focused = state.isFocused }
        .then(if (focused) Modifier.border(WebDimensions.focusRingWidth, WebColors.focusRing, shape) else Modifier)
}
