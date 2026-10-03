package com.rossomak.flashcards.feature.study.fast

import com.rossomak.flashcards.core.ui.navigation.NavigationEvent
import com.rossomak.flashcards.feature.study.StudySessionSummaryRoute

/**
 * Where a Fast Study Session can send the user. One-time events rather than state (ADR-0019):
 * leaving is a transition, and a flag in state would re-fire it on every recomposition after the
 * fact.
 */
sealed interface FastStudySessionDestination : NavigationEvent {

    /**
     * The session ended — the deck was exhausted, or the user confirmed "Exit session?" — carrying
     * the sealed result on to the Session Summary.
     */
    data class Summary(val route: StudySessionSummaryRoute) : FastStudySessionDestination

    /**
     * The session's cards could not be loaded, so no session ever started and there is no result to
     * summarise. Returns to Preview, which can load the cards again. A fallback for that one failure
     * only: leaving a session at any point, even before its cards load, goes to [Summary].
     */
    data object Back : FastStudySessionDestination
}
