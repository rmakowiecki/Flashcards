package com.rossomak.flashcards.crashreporting

import android.util.Log
import timber.log.Timber

/**
 * Sends `AppLog` lines from info up as breadcrumbs, and an error with a throwable as a non-fatal.
 * Breadcrumbs drop the stack frames Timber appends, keeping the exception line.
 */
class CrashlyticsTree(private val crashReporter: CrashReporter) : Timber.Tree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.INFO

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        crashReporter.log(breadcrumb(priority, tag, message))
        if (priority >= Log.ERROR && t != null) {
            crashReporter.recordException(t)
        }
    }

    private fun breadcrumb(priority: Int, tag: String?, message: String): String {
        val letter = priorityLetter(priority)
        val prefix = tag?.let { "$letter/$it" } ?: letter
        return "$prefix: ${message.substringBefore(STACK_FRAME_START).trimEnd()}"
    }

    private fun priorityLetter(priority: Int): String = when (priority) {
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        else -> "A"
    }

    private companion object {
        const val STACK_FRAME_START = "\n\tat "
    }
}
