package com.rossomak.flashcards.feature.account

sealed interface ReportBugMessage {
    data object ReportSent : ReportBugMessage
    data object ReportFailed : ReportBugMessage
    data object ReportNoConnection : ReportBugMessage
}
