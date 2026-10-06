package com.rossomak.flashcards.core.domain.model

/**
 * The device the app runs on. [model] is a manufacturer and model name, never blank: a diagnostic
 * label sent to support as-is, not localised UI text. [systemVersion] is the platform's integer
 * system version; turning it into text for people is the consumer's job.
 */
data class DeviceInfo(
    val model: String,
    val systemVersion: Int,
)
