package com.rossomak.flashcards.feature.study.summary

import com.rossomak.flashcards.core.ui.dialog.DialogEvent

typealias StudySessionSummaryDialogEvent = DialogEvent<StudySessionSummaryDialog>

sealed interface StudySessionSummaryDialog {

    data object XpBreakdown : StudySessionSummaryDialog
}
