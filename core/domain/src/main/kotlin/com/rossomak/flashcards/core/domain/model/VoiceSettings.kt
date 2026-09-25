package com.rossomak.flashcards.core.domain.model

import kotlinx.serialization.Serializable

/**
 * @param voiceLabel names [voiceId] without loading the voice list. Null when no voice is saved,
 *   or the voice was saved before labels were stored.
 */
@Serializable
data class VoiceSettings(
    val speechRate: Float = 1f,
    val voiceId: String? = null,
    val voiceLabel: VoiceLabel? = null,
)

/** The persistable part of a [VoiceOption] its display label is built from. */
@Serializable
data class VoiceLabel(val countryCode: String, val variantIndex: Int)

val VoiceOption.voiceLabel: VoiceLabel get() = VoiceLabel(countryCode = countryCode, variantIndex = variantIndex)
