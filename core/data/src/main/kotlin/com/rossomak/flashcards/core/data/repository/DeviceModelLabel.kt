package com.rossomak.flashcards.core.data.repository

private const val UNKNOWN_DEVICE_LABEL = "Unknown device"
private const val UNKNOWN_MANUFACTURER = "unknown"

/**
 * One readable device name from the platform's manufacturer and model strings. Many models already
 * start with the manufacturer (`HTC One`), and manufacturers are often lowercase (`samsung`), so the
 * manufacturer is only prepended when the model lacks it. Both values can be null on a stubbed
 * platform and either can be blank; emulators and custom ROMs report the manufacturer as
 * [UNKNOWN_MANUFACTURER], which counts as blank. [UNKNOWN_DEVICE_LABEL] means neither said anything.
 * It is a diagnostic value sent to support as-is, not localised UI text.
 */
internal fun deviceModelLabel(manufacturer: String?, model: String?): String {
    val trimmedManufacturer = manufacturer.orEmpty().trim().takeUnless { it.equals(UNKNOWN_MANUFACTURER, ignoreCase = true) }.orEmpty()
    val trimmedModel = model.orEmpty().trim()
    return when {
        trimmedModel.startsWith(trimmedManufacturer, ignoreCase = true) && trimmedModel.isNotEmpty() -> trimmedModel
        trimmedManufacturer.isEmpty() && trimmedModel.isEmpty() -> UNKNOWN_DEVICE_LABEL
        else -> "${trimmedManufacturer.replaceFirstChar { it.uppercase() }} $trimmedModel".trim()
    }
}
