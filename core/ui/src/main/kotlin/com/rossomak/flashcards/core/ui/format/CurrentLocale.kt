package com.rossomak.flashcards.core.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/** The locale numbers and dates on screen are formatted for. It follows a runtime locale change. */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]
