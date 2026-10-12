package com.rossomak.flashcards.feature.account

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/**
 * Starts [intent] and reports whether an app answered it. Only a missing handler counts as a
 * failure: any other exception is a real bug and propagates.
 */
internal fun Context.tryStartActivity(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}
