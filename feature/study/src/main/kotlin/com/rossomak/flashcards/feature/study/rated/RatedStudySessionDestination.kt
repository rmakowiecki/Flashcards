package com.rossomak.flashcards.feature.study.rated

import com.rossomak.flashcards.core.ui.navigation.NavigationEvent
import com.rossomak.flashcards.feature.study.StudySessionSummaryRoute

sealed interface RatedStudySessionDestination : NavigationEvent {

    /**
     * The session ended with at least one Studied Flashcard — either the last card reached a Terminal
     * State, or the user confirmed "Exit session?" — carrying the sealed result on to the Session Summary.
     */
    data class Summary(val route: StudySessionSummaryRoute) : RatedStudySessionDestination

    /**
     * No session was recorded: the cards failed to load, or the session ended with no Studied
     * Flashcard. Returns to Preview.
     */
    data object Back : RatedStudySessionDestination
}
