package com.rossomak.flashcards.feature.home

import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.RecentItem
import com.rossomak.flashcards.core.domain.model.RecentSession
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Custom
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Quick
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import com.rossomak.flashcards.core.domain.model.Subcategory
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
private val sampleComposeSubcategory = Subcategory(
    id = "compose",
    name = "Compose",
    categoryId = sampleAndroidCategory.id,
    categoryName = sampleAndroidCategory.name,
    order = 0,
    cardCount = 30,
)
private val sampleCoroutinesSubcategory = Subcategory(
    id = "coroutines",
    name = "Coroutines",
    categoryId = sampleAndroidCategory.id,
    categoryName = sampleAndroidCategory.name,
    order = 1,
    cardCount = 25,
)
private val sampleGraphsSubcategory = Subcategory(
    id = "graphs",
    name = "Graph traversal, shortest paths and minimum spanning trees in weighted graphs",
    categoryId = sampleAlgorithmsCategory.id,
    categoryName = sampleAlgorithmsCategory.name,
    order = 0,
    cardCount = 40,
)
private val sampleSortingSubcategory = Subcategory(
    id = "sorting",
    name = "Sorting",
    categoryId = sampleAlgorithmsCategory.id,
    categoryName = sampleAlgorithmsCategory.name,
    order = 1,
    cardCount = 20,
)

// Declared after the samples it reads: file-level properties initialize top to bottom.
internal val previewRecentItems: List<RecentItem> = sampleRecentItems(now = previewRecentsNow)

/** Newest first, covering each source type, Study Mode, hands-free flag and display edge case. */
@Suppress("MagicNumber", "LongMethod")
internal fun sampleRecentItems(now: Instant): List<RecentItem> = listOf(
    RecentItem(
        session = RecentSession.Rated(
            id = "sample-single-voice",
            startedAt = now.minus(4, ChronoUnit.MINUTES),
            durationSeconds = 480,
            sourceType = SingleSubcategory,
            categoryId = sampleAndroidCategory.id,
            subcategoryIds = listOf(sampleComposeSubcategory.id),
            studiedCount = 15,
            xpTotal = 140,
            voiceAnsweringEnabled = true,
        ),
        category = sampleAndroidCategory,
        subcategories = listOf(sampleComposeSubcategory),
    ),
    RecentItem(
        session = RecentSession.Fast(
            id = "sample-quick-read-aloud",
            startedAt = now.minus(3, ChronoUnit.HOURS),
            durationSeconds = 720,
            sourceType = Quick,
            categoryId = sampleAndroidCategory.id,
            subcategoryIds = listOf(sampleComposeSubcategory.id, sampleCoroutinesSubcategory.id, "navigation"),
            studiedCount = 20,
            xpTotal = 95,
            readAloudEnabled = true,
        ),
        category = sampleAndroidCategory,
        subcategories = emptyList(),
    ),
    RecentItem(
        session = RecentSession.Rated(
            id = "sample-custom",
            startedAt = now.minus(1, ChronoUnit.DAYS),
            durationSeconds = 600,
            sourceType = Custom,
            categoryId = sampleAlgorithmsCategory.id,
            subcategoryIds = listOf(sampleGraphsSubcategory.id, sampleSortingSubcategory.id),
            studiedCount = 12,
            xpTotal = 60,
            voiceAnsweringEnabled = false,
        ),
        category = sampleAlgorithmsCategory,
        subcategories = listOf(sampleGraphsSubcategory, sampleSortingSubcategory),
    ),
    RecentItem(
        session = RecentSession.Fast(
            id = "sample-xp-loss",
            startedAt = now.minus(3, ChronoUnit.DAYS),
            durationSeconds = 300,
            sourceType = SingleSubcategory,
            categoryId = sampleAlgorithmsCategory.id,
            subcategoryIds = listOf(sampleGraphsSubcategory.id),
            studiedCount = 8,
            xpTotal = -20,
            readAloudEnabled = false,
        ),
        category = sampleAlgorithmsCategory,
        subcategories = listOf(sampleGraphsSubcategory),
    ),
    RecentItem(
        session = RecentSession.Rated(
            id = "sample-short",
            startedAt = now.minus(5, ChronoUnit.DAYS),
            durationSeconds = 45,
            sourceType = SingleSubcategory,
            categoryId = sampleAndroidCategory.id,
            subcategoryIds = listOf(sampleCoroutinesSubcategory.id),
            studiedCount = 1,
            xpTotal = 0,
            voiceAnsweringEnabled = false,
        ),
        category = sampleAndroidCategory,
        subcategories = listOf(sampleCoroutinesSubcategory),
    ),
    RecentItem(
        session = RecentSession.Fast(
            id = "sample-older",
            startedAt = now.minus(20, ChronoUnit.DAYS),
            durationSeconds = 1_500,
            sourceType = Custom,
            categoryId = sampleAndroidCategory.id,
            subcategoryIds = listOf(sampleComposeSubcategory.id, sampleCoroutinesSubcategory.id),
            studiedCount = 30,
            xpTotal = 210,
            readAloudEnabled = false,
        ),
        category = sampleAndroidCategory,
        subcategories = listOf(sampleComposeSubcategory, sampleCoroutinesSubcategory),
    ),
    RecentItem(
        session = RecentSession.Rated(
            id = "sample-last-year",
            startedAt = now.minus(400, ChronoUnit.DAYS),
            durationSeconds = 900,
            sourceType = Quick,
            categoryId = sampleAlgorithmsCategory.id,
            subcategoryIds = listOf(sampleGraphsSubcategory.id, sampleSortingSubcategory.id),
            studiedCount = 18,
            xpTotal = 120,
            voiceAnsweringEnabled = true,
        ),
        category = sampleAlgorithmsCategory,
        subcategories = emptyList(),
    ),
)
