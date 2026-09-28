package com.rossomak.flashcards.core.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One session's entry in a delivery run's report, which
 * [com.rossomak.flashcards.core.data.worker.SessionSubmissionDeliveryWorker] publishes as WorkManager
 * progress and output data, keyed by session id: the score the server answered with, or a marker that
 * the server rejected the session.
 */
@Serializable
sealed interface DeliveredSessionDto {

    @Serializable
    @SerialName("scored")
    data class Scored(val score: SessionScoreDto) : DeliveredSessionDto

    @Serializable
    @SerialName("rejected")
    data object Rejected : DeliveredSessionDto
}

/** [com.rossomak.flashcards.core.domain.model.SessionScore] in JSON form. */
@Serializable
data class SessionScoreDto(
    val newCards: Int,
    val mastered: Int,
    val partial: Int,
    val masteryDefenseBonus: Int,
    val demastered: Int,
    val timeStudied: Int,
    val sessionCompletionBonus: Int,
    val dailyGoalBonus: Int,
    val streakBonus: Int,
    val level: Int,
    val xpIntoCurrentLevel: Long,
    val xpForNextLevel: Long,
    val levelsCrossed: List<Int>,
    val counts: SessionScoreCountsDto? = null,
    val rates: SessionScoreRatesDto? = null,
)

@Serializable
data class SessionScoreCountsDto(
    val newCardsStudied: Int,
    val newlyMastered: Int? = null,
    val partial: Int? = null,
    val defended: Int? = null,
    val demastered: Int? = null,
)

@Serializable
data class SessionScoreRatesDto(
    val newCardStudied: Int,
    val cardMastered: Int,
    val cardPartial: Int,
    val masteryDefended: Int,
    val cardDemastered: Int,
    val minuteStudied: Int,
    val sessionCompleted: Int,
)
