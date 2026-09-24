package com.rossomak.flashcards.core.domain.model

import kotlinx.serialization.Serializable

/**
 * @param voiceLabel The label data of [voiceId], stored beside it so a screen can name the saved
 *   voice without starting a text-to-speech engine to enumerate voices. Null when no voice is
 *   saved, or for a voice saved before the label was stored — a reader resolves that lazily from
 *   the voice list and writes it back.
 */
@Serializable
data class VoiceSettings(
    val speechRate: Float = 1f,
    val voiceId: String? = null,
    val voiceLabel: VoiceLabel? = null,
)

/**
 * The data a voice's user-facing label is built from — [VoiceOption.countryCode] and
 * [VoiceOption.variantIndex] — detached from the voice list so it can be persisted.
 */
@Serializable
data class VoiceLabel(val countryCode: String, val variantIndex: Int)

val VoiceOption.voiceLabel: VoiceLabel get() = VoiceLabel(countryCode = countryCode, variantIndex = variantIndex)
