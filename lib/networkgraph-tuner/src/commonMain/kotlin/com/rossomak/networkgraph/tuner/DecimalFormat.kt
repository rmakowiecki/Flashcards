package com.rossomak.networkgraph.tuner

import kotlin.math.abs
import kotlin.math.floor

/**
 * The value with exactly [decimals] digits after the point, rounded half up, like `"%.Nf"` in the
 * US locale: common code has no `String.format`.
 */
internal fun Float.toFixed(decimals: Int): String {
    require(decimals >= 0) { "decimals must not be negative, was $decimals" }
    var factor = 1L
    repeat(decimals) { factor *= DECIMAL_BASE }
    val scaled = floor(abs(toDouble()) * factor + ROUNDING_HALF).toLong()
    val sign = if (this < 0f) "-" else ""
    val whole = scaled / factor
    if (decimals == 0) return "$sign$whole"
    val fraction = (scaled % factor).toString().padStart(decimals, '0')
    return "$sign$whole.$fraction"
}

private const val DECIMAL_BASE = 10L
private const val ROUNDING_HALF = 0.5
