package com.rossomak.flashcards.core.domain.model

import kotlinx.serialization.Serializable

/**
 * @param voiceLabel names [voiceId] without loading the voice list. Null when no voice is saved,
 *   or the voice was saved before labels were stored.
 */
@Serializable
data class VoiceSettings(
    val speechRate: Float = DEFAULT_SPEECH_RATE,
    val voiceId: String? = null,
    val voiceLabel: VoiceLabel? = null,
) {
    companion object {
        const val DEFAULT_SPEECH_RATE = 1f
        const val MIN_SPEECH_RATE = 0.5f
        const val MAX_SPEECH_RATE = 2f
    }
}

/** The persistable part of a [VoiceOption] its display label is built from. */
@Serializable
data class VoiceLabel(val countryCode: String, val variantIndex: Int)

val VoiceOption.voiceLabel: VoiceLabel get() = VoiceLabel(countryCode = countryCode, variantIndex = variantIndex)
