package com.rossomak.flashcards.feature.home

import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.RecentItem
import com.rossomak.flashcards.core.domain.model.RecentSession
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Custom
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Quick
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Fixed, so previews render the same every time. */
internal val previewRecentsNow: Instant = Instant.parse("2026-09-15T12:00:00Z")
internal val previewRecentsZoneId: ZoneId = ZoneOffset.UTC

private val sampleAndroidCategory = Category(
    id = "android",
    name = "Android",
    order = 0,
    subcategoryCount = 14,
    iconSvg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/></svg>""",
    color = "#2B6AA5",
    featuredSubcategoryNames = emptyList(),
)
private val sampleAlgorithmsCategory = Category(
    id = "algorithms",
    name = "Algorithms and Data Structures for Coding Interviews",
    order = 1,
    subcategoryCount = 9,
    iconSvg = null,
    color = null,
    featuredSubcategoryNames = emptyList(),
)
private val sampleComposeSubcategory = "compose" to "Compose"
private val sampleCoroutinesSubcategory = "coroutines" to "Coroutines"
private val sampleGraphsSubcategory = "graphs" to "Graph traversal, shortest paths and minimum spanning trees in weighted graphs"
private val sampleSortingSubcategory = "sorting" to "Sorting"

// Declared after the samples it reads: file-level properties initialize top to bottom.
internal val previewRecentItems: List<RecentItem> = sampleRecentItems(now = previewRecentsNow)

/** Newest first, covering each source type, Study Mode, hands-free flag and display edge case, including an unread Category. */
@Suppress("MagicNumber", "LongMethod")
internal fun sampleRecentItems(now: Instant): List<RecentItem> = listOf(
    RecentItem(
        session = RecentSession.Rated(
            id = "sample-single-voice",
            startedAt = now.minus(4, ChronoUnit.MINUTES),
            durationSeconds = 480,
            sourceType = SingleSubcategory,
            categoryId = sampleAndroidCategory.id,
            categoryName = sampleAndroidCategory.name,
            subcategoryIds = listOf(sampleComposeSubcategory.first),
            subcategoryNames = listOf(sampleComposeSubcategory.second),
            studiedCount = 15,
            xpTotal = 140,
            voiceAnsweringEnabled = true,
        ),
        category = sampleAndroidCategory,
    ),
    RecentItem(
        session = RecentSession.Fast(
            id = "sample-quick-read-aloud",
            startedAt = now.minus(3, ChronoUnit.HOURS),
            durationSeconds = 720,
            sourceType = Quick,
            categoryId = sampleAndroidCategory.id,
            categoryName = sampleAndroidCategory.name,
            subcategoryIds = listOf(sampleComposeSubcategory.first, sampleCoroutinesSubcategory.first, "navigation"),
            subcategoryNames = listOf(sampleComposeSubcategory.second, sampleCoroutinesSubcategory.second, "Navigation"),
            studiedCount = 20,
            xpTotal = 95,
            readAloudEnabled = true,
        ),
        category = sampleAndroidCategory,
    ),
    RecentItem(
        session = RecentSession.Rated(
            id = "sample-custom",
            startedAt = now.minus(1, ChronoUnit.DAYS),
            durationSeconds = 600,
            sourceType = Custom,
            categoryId = sampleAlgorithmsCategory.id,
            categoryName = sampleAlgorithmsCategory.name,
            subcategoryIds = listOf(sampleGraphsSubcategory.first, sampleSortingSubcategory.first),
            subcategoryNames = listOf(sampleGraphsSubcategory.second, sampleSortingSubcategory.second),
            studiedCount = 12,
            xpTotal = 60,
            voiceAnsweringEnabled = false,
        ),
        category = sampleAlgorithmsCategory,
    ),
    RecentItem(
        session = RecentSession.Fast(
            id = "sample-xp-loss",
            startedAt = now.minus(3, ChronoUnit.DAYS),
            durationSeconds = 300,
            sourceType = SingleSubcategory,
            categoryId = sampleAlgorithmsCategory.id,
            categoryName = sampleAlgorithmsCategory.name,
            subcategoryIds = listOf(sampleGraphsSubcategory.first),
            subcategoryNames = listOf(sampleGraphsSubcategory.second),
            studiedCount = 8,
            xpTotal = -20,
            readAloudEnabled = false,
        ),
        category = sampleAlgorithmsCategory,
    ),
    RecentItem(
        session = RecentSession.Rated(
            id = "sample-short",
            startedAt = now.minus(5, ChronoUnit.DAYS),
            durationSeconds = 45,
            sourceType = SingleSubcategory,
            categoryId = sampleAndroidCategory.id,
            categoryName = sampleAndroidCategory.name,
            subcategoryIds = listOf(sampleCoroutinesSubcategory.first),
            subcategoryNames = listOf(sampleCoroutinesSubcategory.second),
            studiedCount = 1,
            xpTotal = 0,
            voiceAnsweringEnabled = false,
        ),
        category = sampleAndroidCategory,
    ),
    RecentItem(
        session = RecentSession.Fast(
            id = "sample-older",
            startedAt = now.minus(20, ChronoUnit.DAYS),
            durationSeconds = 1_500,
            sourceType = Custom,
            categoryId = sampleAndroidCategory.id,
            categoryName = sampleAndroidCategory.name,
            subcategoryIds = listOf(sampleComposeSubcategory.first, sampleCoroutinesSubcategory.first),
            subcategoryNames = listOf(sampleComposeSubcategory.second, sampleCoroutinesSubcategory.second),
            studiedCount = 30,
            xpTotal = 210,
            readAloudEnabled = false,
        ),
        category = sampleAndroidCategory,
    ),
    RecentItem(
        session = RecentSession.Rated(
            id = "sample-last-year",
            startedAt = now.minus(400, ChronoUnit.DAYS),
            durationSeconds = 900,
            sourceType = Quick,
            categoryId = sampleAlgorithmsCategory.id,
            categoryName = sampleAlgorithmsCategory.name,
            subcategoryIds = listOf(sampleGraphsSubcategory.first, sampleSortingSubcategory.first),
            subcategoryNames = listOf(sampleGraphsSubcategory.second, sampleSortingSubcategory.second),
            studiedCount = 18,
            xpTotal = 120,
            voiceAnsweringEnabled = true,
        ),
        category = null,
    ),
)
