package com.rossomak.flashcards.core.ui.animation

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Whether the User allows decorative motion: `false` when the system's animator duration scale is
 * off (Settings → Accessibility → Remove animations, or the matching developer option). A screen
 * with a long scripted sequence renders its final state directly instead of playing it.
 *
 * Read once when the composable enters the composition, not observed: the setting lives in system
 * settings, so changing it means leaving the app, and the next screen reads it afresh. Always `true`
 * in previews, where there is no content resolver to ask.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val isPreview = LocalInspectionMode.current
    val context = LocalContext.current
    return remember(isPreview, context) {
        isPreview || Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}
