package com.rossomak.flashcards.presentation.main

import androidx.compose.runtime.Composable
import com.rossomak.flashcards.debugtools.darktheme.ProvideDarkThemeTuning

/** Re-themes [content] with the debug dark theme tuner's live colors while the system is dark; sits inside `FlashcardsTheme`. */
@Composable
internal fun DebugThemeOverrides(content: @Composable () -> Unit) = ProvideDarkThemeTuning(content)
