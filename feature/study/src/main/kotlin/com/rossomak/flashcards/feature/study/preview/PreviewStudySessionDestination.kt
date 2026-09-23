package com.rossomak.flashcards.feature.study.preview

import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.ui.navigation.NavigationEvent
import com.rossomak.flashcards.feature.study.FastStudySessionRoute
import com.rossomak.flashcards.feature.study.RatedStudySessionRoute

sealed interface PreviewStudySessionDestination : NavigationEvent {

    /** Chosen when the confirmed Study Mode is Fast (ADR-0045). */
    data class FastStudySession(val route: FastStudySessionRoute) : PreviewStudySessionDestination

    /** Chosen when the confirmed Study Mode is Rated (ADR-0045). */
    data class RatedStudySession(val route: RatedStudySessionRoute) : PreviewStudySessionDestination
}

/**
 * The confirmed Study Mode picks the destination (ADR-0045): Fast and Rated each open their own
 * screen, carrying only the settings that mode uses. Kept out of [PreviewStudySessionViewModel]
 * since it reads nothing but the state it is handed.
 */
internal fun PreviewStudySessionScreenState.toSessionDestination(categoryId: String, cardIds: List<String>): PreviewStudySessionDestination =
    if (config.mode == StudyMode.Fast) {
        PreviewStudySessionDestination.FastStudySession(
            FastStudySessionRoute(
                categoryId = categoryId,
                sessionTitle = sessionTitle,
                subcategoryIds = config.subcategoryIds,
                cardIds = cardIds,
                readAloudEnabled = config.readAloudEnabled,
                speechRate = config.voiceSettings.speechRate,
                voiceId = config.voiceSettings.voiceId,
                categoryName = categoryName,
                subcategoryNames = subcategoryNames,
            )
        )
    } else {
        PreviewStudySessionDestination.RatedStudySession(
            RatedStudySessionRoute(
                categoryId = categoryId,
                sessionTitle = sessionTitle,
                subcategoryIds = config.subcategoryIds,
                cardIds = cardIds,
                voiceAnsweringEnabled = config.voiceAnsweringEnabled,
                ratedAttempts = config.ratedAttempts,
                partialRatingCardRequeueingEnabled = config.partialRatingCardRequeueingEnabled,
                speechRate = config.voiceSettings.speechRate,
                voiceId = config.voiceSettings.voiceId,
                categoryName = categoryName,
                subcategoryNames = subcategoryNames,
            )
        )
    }

private val PreviewStudySessionScreenState.sessionTitle: String
    get() = if (isSingleSubcategory) subcategoryNames.first() else categoryName
