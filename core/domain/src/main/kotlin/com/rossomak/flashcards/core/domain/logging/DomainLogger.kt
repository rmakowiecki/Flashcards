package com.rossomak.flashcards.core.domain.logging

/**
 * Logging for `core:domain`, which cannot see the app's logging library. Log sparingly, as
 * AGENTS.md describes: failures and key lifecycle transitions only.
 */
interface DomainLogger {
    fun info(message: () -> String)
    fun warn(message: () -> String)
    fun error(throwable: Throwable? = null, message: () -> String)
}
