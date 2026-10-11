package com.rossomak.flashcards.core.ui.format

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.absoluteValue

private const val MINUS_SIGN = '−'

/**
 * A signed XP amount: locale grouping, an explicit `+` for a gain, a real minus sign (U+2212) for a
 * loss, and no sign for zero. Shared by every place that shows an XP amount, so a figure reads the
 * same in the total, the breakdown lines and the dialog.
 */
fun formatSignedXp(amount: Int, locale: Locale): String {
    val magnitude = formatMagnitude(amount, locale)
    return when {
        amount > 0 -> "+$magnitude"
        amount < 0 -> "$MINUS_SIGN$magnitude"
        else -> magnitude
    }
}

/** A count or rate: locale grouping and a real minus sign for a negative value, never an explicit `+`. */
fun formatXpFactor(value: Int, locale: Locale): String {
    val magnitude = formatMagnitude(value, locale)
    return if (value < 0) "$MINUS_SIGN$magnitude" else magnitude
}

private fun formatMagnitude(value: Int, locale: Locale): String =
    NumberFormat.getIntegerInstance(locale).format(value.toLong().absoluteValue)
