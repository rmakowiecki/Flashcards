package com.rossomak.flashcards.feature.study.summary

import com.rossomak.flashcards.core.domain.model.StudySessionReplay
import com.rossomak.flashcards.core.ui.navigation.NavigationEvent

/** One-time navigation out of the Session Summary, other than leaving it (ADR-0019). */
sealed interface StudySessionSummaryDestination : NavigationEvent {

    /** The Study Again button: opens Preview for [replay], as tapping this session's Recent on Home would. */
    data class StudyAgain(val replay: StudySessionReplay) : StudySessionSummaryDestination
}
