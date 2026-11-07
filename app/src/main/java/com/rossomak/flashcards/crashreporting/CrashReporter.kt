package com.rossomak.flashcards.crashreporting

/**
 * Seam over Firebase Crashlytics, so [CrashlyticsTree] is testable on the JVM. Never pass a user
 * identifier: Account Deletion cannot delete Crashlytics data.
 */
interface CrashReporter {
    fun recordException(throwable: Throwable)

    fun log(message: String)

    fun setCustomKey(key: String, value: String)
}
