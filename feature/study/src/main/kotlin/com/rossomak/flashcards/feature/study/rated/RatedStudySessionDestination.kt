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
     * The cards failed to load, so no session started. Returns to Preview to load them again. Only for
     * that failure: leaving a session, even before its cards load, goes to [Summary].
     */
    data object Back : RatedStudySessionDestination
}
