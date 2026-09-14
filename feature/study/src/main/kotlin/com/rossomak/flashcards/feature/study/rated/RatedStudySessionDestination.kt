package com.rossomak.flashcards.feature.study.rated

import com.rossomak.flashcards.core.ui.navigation.NavigationEvent
import com.rossomak.flashcards.feature.study.StudySessionSummaryRoute

sealed interface RatedStudySessionDestination : NavigationEvent {

    /**
     * The session ended — either the last card reached a Terminal State, or the user confirmed
     * "Exit session?" — carrying the sealed result on to the Session Summary.
     * The session's only destination now; there is no longer a plain "go back" outcome.
     */
    data class Summary(val route: StudySessionSummaryRoute) : RatedStudySessionDestination
}
