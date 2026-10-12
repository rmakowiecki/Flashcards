package com.rossomak.flashcards.feature.study.summary

import com.rossomak.flashcards.core.domain.model.StudySessionReplay
import com.rossomak.flashcards.core.ui.navigation.NavigationEvent

sealed interface StudySessionSummaryDestination : NavigationEvent {

    data class StudyAgain(val replay: StudySessionReplay) : StudySessionSummaryDestination
}
