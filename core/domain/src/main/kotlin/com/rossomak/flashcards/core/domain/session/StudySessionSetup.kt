package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.SessionSourceType
import com.rossomak.flashcards.core.domain.model.VoiceSettings

/**
 * Everything a Rated Study Session is started with, fixed once the session starts.
 *
 * @param cardIds the Preview screen's draw, in presentation order.
 * @param sessionTitle the fixed name the voice notification shows for the whole session.
 * @param voiceSettings the voice and speech rate the session starts with.
 */
data class RatedSessionSetup(
    val categoryId: String,
    val categoryName: String,
    val subcategoryIds: List<String>,
    val subcategoryNames: List<String>,
    val cardIds: List<String>,
    val sessionTitle: String,
    val voiceSettings: VoiceSettings,
    val voiceAnsweringEnabled: Boolean,
    val attemptsLimit: Int,
    val partialRatingCardRequeueingEnabled: Boolean,
    val sourceType: SessionSourceType,
)

/** Everything a Fast Study Session is started with, fixed once the session starts. */
data class FastSessionSetup(
    val categoryId: String,
    val categoryName: String,
    val subcategoryIds: List<String>,
    val subcategoryNames: List<String>,
    val cardIds: List<String>,
    val sessionTitle: String,
    val voiceSettings: VoiceSettings,
    val readAloudEnabled: Boolean,
    val sourceType: SessionSourceType,
)
