package com.rossomak.flashcards.crashreporting

import android.util.Log
import timber.log.Timber

/**
 * Sends `AppLog` output to crash reporting. Info, warning and error lines become breadcrumbs; an
 * error with a throwable is also recorded as a non-fatal. Debug and verbose lines are dropped.
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
        val prefix = tag?.let { "${priorityLetter(priority)}/$it" } ?: priorityLetter(priority)
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
