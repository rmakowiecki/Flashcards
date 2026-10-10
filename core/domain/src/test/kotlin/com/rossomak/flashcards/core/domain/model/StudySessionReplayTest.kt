package com.rossomak.flashcards.core.domain.model

import com.rossomak.flashcards.core.domain.model.SessionSourceType.Custom
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Quick
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.junit.Test

class StudySessionReplayTest {

    private val subcategoryIds = listOf(COMPOSE_ID, COROUTINES_ID)
    private val subcategoryNames = listOf(COMPOSE_NAME, COROUTINES_NAME)

    private fun replayOf(
        sourceType: SessionSourceType,
        studyMode: StudyMode = StudyMode.Rated,
        voiceAnsweringEnabled: Boolean? = null,
        readAloudEnabled: Boolean? = null,
    ): StudySessionReplay = StudySessionReplay.of(
        categoryId = CATEGORY_ID,
        categoryName = CATEGORY_NAME,
        sourceType = sourceType,
        subcategoryIds = subcategoryIds,
        subcategoryNames = subcategoryNames,
        studyMode = studyMode,
        voiceAnsweringEnabled = voiceAnsweringEnabled,
        readAloudEnabled = readAloudEnabled,
    )

    @Test
    fun `a single-subcategory session replays its Subcategory`() {
        val replay = replayOf(SingleSubcategory)

        replay.sourceType shouldBe SingleSubcategory
        replay.subcategoryIds shouldBe subcategoryIds
        replay.subcategoryNames shouldBe subcategoryNames
    }

    @Test
    fun `a Quick session replays the Category alone so Preview samples afresh`() {
        val replay = replayOf(Quick)

        replay.categoryId shouldBe CATEGORY_ID
        replay.categoryName shouldBe CATEGORY_NAME
        replay.sourceType shouldBe Quick
        replay.subcategoryIds shouldBe emptyList()
        replay.subcategoryNames shouldBe emptyList()
    }

    @Test
    fun `a Custom session replays every stored Subcategory in stored order`() {
        val replay = replayOf(Custom)

        replay.subcategoryIds shouldBe subcategoryIds
        replay.subcategoryNames shouldBe subcategoryNames
    }

    @Test
    fun `a Rated session keeps voice answering and drops read-aloud`() {
        val replay = replayOf(SingleSubcategory, StudyMode.Rated, voiceAnsweringEnabled = true, readAloudEnabled = true)

        replay.studyMode shouldBe StudyMode.Rated
        replay.voiceAnsweringEnabled shouldBe true
        replay.readAloudEnabled shouldBe null
    }

    @Test
    fun `a Fast session keeps read-aloud and drops voice answering`() {
        val replay = replayOf(SingleSubcategory, StudyMode.Fast, voiceAnsweringEnabled = false, readAloudEnabled = true)

        replay.studyMode shouldBe StudyMode.Fast
        replay.voiceAnsweringEnabled shouldBe null
        replay.readAloudEnabled shouldBe true
    }

    @Test
    fun `a Rated Recent replays through its own fields`() {
        val recent = RecentSession.Rated(
            id = SESSION_ID,
            startedAt = Instant.parse("2026-09-06T10:00:00Z"),
            durationSeconds = 600,
            sourceType = Custom,
            categoryId = CATEGORY_ID,
            categoryName = CATEGORY_NAME,
            subcategoryIds = subcategoryIds,
            subcategoryNames = subcategoryNames,
            studiedCount = 10,
            xpTotal = 300,
            voiceAnsweringEnabled = true,
        )

        recent.toReplay() shouldBe StudySessionReplay(
            categoryId = CATEGORY_ID,
            categoryName = CATEGORY_NAME,
            sourceType = Custom,
            subcategoryIds = subcategoryIds,
            subcategoryNames = subcategoryNames,
            studyMode = StudyMode.Rated,
            voiceAnsweringEnabled = true,
            readAloudEnabled = null,
        )
    }

    @Test
    fun `a Quick Fast Recent replays the Category with read-aloud`() {
        val recent = RecentSession.Fast(
            id = SESSION_ID,
            startedAt = Instant.parse("2026-09-06T10:00:00Z"),
            durationSeconds = 600,
            sourceType = Quick,
            categoryId = CATEGORY_ID,
            categoryName = CATEGORY_NAME,
            subcategoryIds = subcategoryIds,
            subcategoryNames = subcategoryNames,
            studiedCount = 10,
            xpTotal = 300,
            readAloudEnabled = true,
        )

        recent.toReplay() shouldBe StudySessionReplay(
            categoryId = CATEGORY_ID,
            categoryName = CATEGORY_NAME,
            sourceType = Quick,
            subcategoryIds = emptyList(),
            subcategoryNames = emptyList(),
            studyMode = StudyMode.Fast,
            voiceAnsweringEnabled = null,
            readAloudEnabled = true,
        )
    }

    private companion object {
        const val CATEGORY_ID = "android"
        const val CATEGORY_NAME = "Android"
        const val COMPOSE_ID = "compose"
        const val COMPOSE_NAME = "Compose"
        const val COROUTINES_ID = "coroutines"
        const val COROUTINES_NAME = "Coroutines"
        const val SESSION_ID = "session-1"
    }
}
