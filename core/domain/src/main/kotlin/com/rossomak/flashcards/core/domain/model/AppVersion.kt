package com.rossomak.flashcards.core.domain.model

/**
 * The installed build's version. [name] is the human-readable version, build type suffix included
 * (e.g. `1.4.142-debug`); [code] is the monotonically increasing build number.
 */
data class AppVersion(
    val name: String,
    val code: Long,
)
