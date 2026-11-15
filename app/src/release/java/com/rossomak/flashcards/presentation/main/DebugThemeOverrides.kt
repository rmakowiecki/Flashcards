package com.rossomak.flashcards.presentation.main

import androidx.compose.runtime.Composable

/** Release has no debug theme overrides: [content] keeps the theme around it. */
@Composable
internal fun DebugThemeOverrides(content: @Composable () -> Unit) = content()
