package com.rossomak.flashcards.core.domain.logging

class FakeDomainLogger : DomainLogger {

    enum class Level { Info, Warn, Error }

    data class Entry(val level: Level, val message: String, val throwable: Throwable? = null)

    val entries = mutableListOf<Entry>()

    override fun info(message: () -> String) {
        entries += Entry(Level.Info, message())
    }

    override fun warn(message: () -> String) {
        entries += Entry(Level.Warn, message())
    }

    override fun error(throwable: Throwable?, message: () -> String) {
        entries += Entry(Level.Error, message(), throwable)
    }
}
