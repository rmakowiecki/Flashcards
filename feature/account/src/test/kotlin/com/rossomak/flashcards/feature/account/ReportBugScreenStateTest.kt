package com.rossomak.flashcards.feature.account

import com.rossomak.flashcards.core.domain.model.BugReport
import com.rossomak.flashcards.core.domain.model.BugReportSeverity.Minor
import io.kotest.matchers.shouldBe
import org.junit.Test

class ReportBugScreenStateTest {

    private val sendable = ReportBugScreenState(
        draftText = "a".repeat(BugReport.MIN_DESCRIPTION_LENGTH),
        severity = Minor,
    )

    @Test
    fun `a picked severity and a description at the minimum can be sent`() {
        sendable.canSend shouldBe true
    }

    @Test
    fun `a description at the maximum can be sent`() {
        sendable.copy(draftText = "a".repeat(BugReport.MAX_DESCRIPTION_LENGTH)).canSend shouldBe true
    }

    @Test
    fun `a blank screen cannot be sent`() {
        ReportBugScreenState().canSend shouldBe false
    }

    @Test
    fun `no severity cannot be sent`() {
        sendable.copy(severity = null).canSend shouldBe false
    }

    @Test
    fun `a description below the minimum cannot be sent`() {
        sendable.copy(draftText = "a".repeat(BugReport.MIN_DESCRIPTION_LENGTH - 1)).canSend shouldBe false
    }

    @Test
    fun `a description above the maximum cannot be sent`() {
        sendable.copy(draftText = "a".repeat(BugReport.MAX_DESCRIPTION_LENGTH + 1)).canSend shouldBe false
    }

    @Test
    fun `whitespace padding does not lift a short description over the minimum`() {
        val padding = " ".repeat(BugReport.MAX_DESCRIPTION_LENGTH)
        val shortText = "a".repeat(BugReport.MIN_DESCRIPTION_LENGTH - 1)

        sendable.copy(draftText = padding + shortText + padding).canSend shouldBe false
    }

    @Test
    fun `whitespace padding does not push a description over the maximum`() {
        val padding = " ".repeat(BugReport.MAX_DESCRIPTION_LENGTH)
        val longestText = "a".repeat(BugReport.MAX_DESCRIPTION_LENGTH)

        sendable.copy(draftText = padding + longestText + padding).canSend shouldBe true
    }

    @Test
    fun `only whitespace cannot be sent`() {
        sendable.copy(draftText = " ".repeat(BugReport.MIN_DESCRIPTION_LENGTH)).canSend shouldBe false
    }

    @Test
    fun `a form that is sending cannot be sent again`() {
        sendable.copy(isSending = true).canSend shouldBe false
    }

    @Test
    fun `a form that was sent cannot be sent again`() {
        sendable.copy(isSent = true).canSend shouldBe false
    }

    @Test
    fun `sending and sent both lock the form`() {
        ReportBugScreenState().isLocked shouldBe false
        ReportBugScreenState(isSending = true).isLocked shouldBe true
        ReportBugScreenState(isSent = true).isLocked shouldBe true
    }
}
