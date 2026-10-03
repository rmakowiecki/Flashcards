package com.rossomak.flashcards.feature.study.rated

import com.rossomak.flashcards.core.ui.navigation.NavigationEvent
import com.rossomak.flashcards.feature.study.StudySessionSummaryRoute

sealed interface RatedStudySessionDestination : NavigationEvent {

    /**
     * The session ended — either the last card reached a Terminal State, or the user confirmed
     * "Exit session?" — carrying the sealed result on to the Session Summary.
     */
    data class Summary(val route: StudySessionSummaryRoute) : RatedStudySessionDestination

    /**
     * The session's cards could not be loaded, so no session ever started and there is no result to
     * summarise. Returns to Preview, which can load the cards again. A fallback for that one failure
     * only: leaving a session at any point, even before its cards load, goes to [Summary].
     */
    data object Back : RatedStudySessionDestination
}
