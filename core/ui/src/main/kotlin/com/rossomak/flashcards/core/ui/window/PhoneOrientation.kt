package com.rossomak.flashcards.core.ui.window

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Point
import android.os.Build

/** Smallest width, in dp, from which a screen is large: tablets and unfolded foldables. */
internal const val LARGE_SCREEN_SMALLEST_WIDTH_DP = 600

/**
 * Locks a phone to portrait and lets a large screen rotate freely (ADR-0074). Every app activity calls it
 * from `onCreate`, right after `super.onCreate`, and also declares `android:screenOrientation="portrait"`
 * so a phone is portrait before the first frame. It runs only in `onCreate`, which is enough while no
 * activity declares `configChanges`: a fold, an unfold or a window resize recreates the activity.
 */
fun Activity.lockPortraitOnPhones() {
    requestedOrientation = orientationForSmallestWidth(screenSmallestWidthDp())
}

/** An undefined smallest width (0) counts as a phone. */
internal fun orientationForSmallestWidth(smallestScreenWidthDp: Int): Int =
    if (smallestScreenWidthDp >= LARGE_SCREEN_SMALLEST_WIDTH_DP) {
        ActivityInfo.SCREEN_ORIENTATION_FULL_USER
    } else {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

/** The whole screen's smallest width, not the window's, so a large screen in a small window stays large. */
@Suppress("DEPRECATION") // Display.getRealSize is the only whole-screen size below API 30.
private fun Activity.screenSmallestWidthDp(): Int {
    val smallestSidePx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowManager.maximumWindowMetrics.bounds.run { minOf(width(), height()) }
    } else {
        Point().also { windowManager.defaultDisplay.getRealSize(it) }.run { minOf(x, y) }
    }
    return (smallestSidePx / resources.displayMetrics.density).toInt()
}
