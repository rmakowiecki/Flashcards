package com.rossomak.flashcards.core.domain.model

import io.kotest.matchers.shouldBe
import org.junit.Test

class BugReportTest {

    @Test
    fun `descriptionLength is the trimmed length`() {
        BugReport.descriptionLength("  hello world \n") shouldBe 11
    }

    @Test
    fun `descriptionLength of blank text is zero`() {
        BugReport.descriptionLength(" \n\t ") shouldBe 0
    }

    @Test
    fun `descriptionLength keeps inner whitespace`() {
        BugReport.descriptionLength("a  b") shouldBe 4
    }
}
