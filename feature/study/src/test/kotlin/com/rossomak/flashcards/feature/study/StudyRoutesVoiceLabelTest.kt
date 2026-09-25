package com.rossomak.flashcards.feature.study

import com.rossomak.flashcards.core.domain.model.VoiceLabel
import io.kotest.matchers.shouldBe
import org.junit.Test

class StudyRoutesVoiceLabelTest {

    @Test
    fun `a route with both label halves rebuilds the voice label`() {
        val route = fastRoute(voiceCountryCode = COUNTRY_CODE, voiceVariantIndex = VARIANT_INDEX)

        route.voiceSettings.voiceLabel shouldBe VoiceLabel(countryCode = COUNTRY_CODE, variantIndex = VARIANT_INDEX)
    }

    @Test
    fun `a route missing either label half carries no voice label`() {
        fastRoute(voiceCountryCode = COUNTRY_CODE).voiceSettings.voiceLabel shouldBe null
        ratedRoute(voiceVariantIndex = VARIANT_INDEX).voiceSettings.voiceLabel shouldBe null
    }

    private fun fastRoute(voiceCountryCode: String? = null, voiceVariantIndex: Int? = null): FastStudySessionRoute = FastStudySessionRoute(
        categoryId = CATEGORY_ID,
        sessionTitle = CATEGORY_NAME,
        subcategoryIds = emptyList(),
        cardIds = emptyList(),
        voiceId = VOICE_ID,
        voiceCountryCode = voiceCountryCode,
        voiceVariantIndex = voiceVariantIndex,
        categoryName = CATEGORY_NAME,
        subcategoryNames = emptyList(),
    )

    private fun ratedRoute(voiceCountryCode: String? = null, voiceVariantIndex: Int? = null): RatedStudySessionRoute = RatedStudySessionRoute(
        categoryId = CATEGORY_ID,
        sessionTitle = CATEGORY_NAME,
        subcategoryIds = emptyList(),
        cardIds = emptyList(),
        voiceId = VOICE_ID,
        voiceCountryCode = voiceCountryCode,
        voiceVariantIndex = voiceVariantIndex,
        categoryName = CATEGORY_NAME,
        subcategoryNames = emptyList(),
    )

    private companion object {
        const val CATEGORY_ID = "android"
        const val CATEGORY_NAME = "Android"
        const val VOICE_ID = "en-gb-x-1"
        const val COUNTRY_CODE = "GB"
        const val VARIANT_INDEX = 2
    }
}
