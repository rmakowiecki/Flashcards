package com.rossomak.flashcards.core.ui.voice

import com.rossomak.flashcards.core.domain.model.VoiceLabel
import com.rossomak.flashcards.core.domain.model.VoiceOption
import io.kotest.matchers.shouldBe
import org.junit.Test

class VoiceSettingsDraftStateTest {

    @Test
    fun `a draft with the voice list takes the label of the chosen voice`() {
        val draft = VoiceSettingsDraftState(availableVoices = VOICES, draftVoiceId = GB_VOICE_ID, seededVoiceLabel = US_LABEL)

        draft.toVoiceSettings().voiceLabel shouldBe GB_LABEL
    }

    @Test
    fun `a draft saved before the voice list arrives keeps the seeded label`() {
        val draft = VoiceSettingsDraftState(draftVoiceId = US_VOICE_ID, seededVoiceLabel = US_LABEL)

        draft.toVoiceSettings().voiceLabel shouldBe US_LABEL
    }

    @Test
    fun `a seeded label is dropped once the list shows the voice is not installed`() {
        val draft = VoiceSettingsDraftState(availableVoices = VOICES, draftVoiceId = MISSING_VOICE_ID, seededVoiceLabel = US_LABEL)

        draft.toVoiceSettings().voiceLabel shouldBe null
    }

    private companion object {
        const val US_VOICE_ID = "en-us-x-1"
        const val GB_VOICE_ID = "en-gb-x-1"
        const val MISSING_VOICE_ID = "en-au-x-1"
        val US_LABEL = VoiceLabel(countryCode = "US", variantIndex = 1)
        val GB_LABEL = VoiceLabel(countryCode = "GB", variantIndex = 1)
        val VOICES = listOf(
            VoiceOption(id = US_VOICE_ID, countryCode = "US", variantIndex = 1),
            VoiceOption(id = GB_VOICE_ID, countryCode = "GB", variantIndex = 1),
        )
    }
}
