package com.rossomak.flashcards.feature.study.summary

import com.rossomak.flashcards.core.ui.dialog.DialogEvent

typealias StudySessionSummaryDialogEvent = DialogEvent<StudySessionSummaryDialog>

/** Every dialog the Session Summary can show. */
sealed interface StudySessionSummaryDialog {

    /** The itemised XP lines behind the total. Read-only, so it carries no draft. */
    data object XpBreakdown : StudySessionSummaryDialog
}
