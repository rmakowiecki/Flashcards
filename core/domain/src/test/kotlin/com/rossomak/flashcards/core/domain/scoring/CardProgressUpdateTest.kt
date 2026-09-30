package com.rossomak.flashcards.core.domain.scoring

import com.rossomak.flashcards.core.domain.model.CardProgressEntry
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState.Failed
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState.Mastered
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState.Partial
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.junit.Test

/** Stamping only: which entry an update produces is covered by the shared merge cases in [CardProgressMergeTest]. */
class CardProgressUpdateTest {

    @Test
    fun `a first record is stamped first studied and, when Mastered, mastered at the given time`() {
        val update = CardProgressUpdate(Mastered, stampFirstStudied = true, stampMastered = true)

        update.applyTo(prior = null, stampedAt = STAMPED_AT) shouldBe CardProgressEntry(Mastered, firstStudiedAt = STAMPED_AT, masteredAt = STAMPED_AT)
    }

    @Test
    fun `a re-mastery keeps the first studied time and moves the mastered time`() {
        val prior = CardProgressEntry(Partial, firstStudiedAt = EARLIER, masteredAt = EARLIER)
        val update = CardProgressUpdate(Mastered, stampFirstStudied = false, stampMastered = true)

        update.applyTo(prior, stampedAt = STAMPED_AT) shouldBe CardProgressEntry(Mastered, firstStudiedAt = EARLIER, masteredAt = STAMPED_AT)
    }

    @Test
    fun `a de-mastery keeps both earlier stamps`() {
        val prior = CardProgressEntry(Mastered, firstStudiedAt = EARLIER, masteredAt = EARLIER)
        val update = CardProgressUpdate(Failed, stampFirstStudied = false, stampMastered = false)

        update.applyTo(prior, stampedAt = STAMPED_AT) shouldBe CardProgressEntry(Failed, firstStudiedAt = EARLIER, masteredAt = EARLIER)
    }

    private companion object {
        val EARLIER: Instant = Instant.parse("2026-09-01T10:00:00Z")
        val STAMPED_AT: Instant = Instant.parse("2026-09-06T10:00:00Z")
    }
}
