package com.rossomak.flashcards.debugtools.darktheme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme

/**
 * The live dark theme tuning, process-wide so a value set on the tuner screen applies to every real
 * screen while you navigate. Held in memory only: a restart starts again from the shipped dark theme.
 */
object DarkThemeTunerState {
    var knobs: DarkThemeTunerKnobs by mutableStateOf(DarkThemeTunerKnobs())
    val colors by derivedStateOf { knobs.toColors() }

    fun reset() {
        knobs = DarkThemeTunerKnobs()
    }
}

/**
 * Re-themes [content] with [DarkThemeTunerState]'s colors while the system is dark, and leaves it
 * alone in light. Place it inside `FlashcardsTheme`.
 */
@Composable
fun ProvideDarkThemeTuning(content: @Composable () -> Unit) {
    if (isSystemInDarkTheme()) {
        FlashcardsTheme(colors = DarkThemeTunerState.colors, content = content)
    } else {
        content()
    }
}
