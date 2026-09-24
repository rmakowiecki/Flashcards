package com.rossomak.flashcards.feature.debug

/**
 * Identifies the installed build on the debug hub. Debug and profiling builds share one package, so
 * the app info screen alone can't tell them apart. The host app fills this from its own
 * `BuildConfig`, which this module can't see.
 */
data class BuildInfo(
    val buildType: String,
    val versionName: String,
    val versionCode: Int,
    val gitShortSha: String,
)
