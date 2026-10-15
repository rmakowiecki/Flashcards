package com.rossomak.flashcards.core.ui.format

import java.text.NumberFormat
import java.util.Locale

private const val PERCENT_SCALE = 100.0

/** A whole percent such as "58%", with the digits and percent sign of [locale]. */
fun formatPercent(percent: Int, locale: Locale): String =
    NumberFormat.getPercentInstance(locale).format(percent / PERCENT_SCALE)
