@file:Suppress("MagicNumber")

package com.rossomak.flashcards.debugtools.showcase.fixtures

import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.RecentItem
import com.rossomak.flashcards.core.domain.model.RecentSession
import com.rossomak.flashcards.core.domain.model.SessionSourceType
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Custom
import com.rossomak.flashcards.core.domain.model.SessionSourceType.Quick
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.Subcategory
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val LATEST_SESSION_SECONDS = 300
private const val EARLIER_TODAY_SESSION_SECONDS = 420

/** Every source type, Study Mode and delivery. The first two fall today in [zone], so Home's Daily Goal sums them. */
internal fun ShowcaseFixtures.recentItems(now: Instant, zone: ZoneId): List<RecentItem> = listOf(
    recent(
        id = "recent-coroutines-voice",
        startedAt = startedToday(now, zone, endedMinutesAgo = 4, durationSeconds = LATEST_SESSION_SECONDS),
        mode = StudyMode.Rated,
        isHandsFree = true,
        sourceType = SingleSubcategory,
        category = kotlin,
        subcategories = listOf(coroutines),
        durationSeconds = LATEST_SESSION_SECONDS,
        studiedCount = 8,
        xpTotal = 560,
    ),
    recent(
        id = "recent-android-quick",
        startedAt = startedToday(now, zone, endedMinutesAgo = 180, durationSeconds = EARLIER_TODAY_SESSION_SECONDS),
        mode = StudyMode.Fast,
        isHandsFree = true,
        sourceType = Quick,
        category = android,
        subcategories = listOf(compose, navigation, backgroundWork),
        durationSeconds = EARLIER_TODAY_SESSION_SECONDS,
        studiedCount = 11,
        xpTotal = 290,
    ),
    recent(
        id = "recent-swift-custom",
        startedAt = now.minus(1, ChronoUnit.DAYS),
        mode = StudyMode.Rated,
        isHandsFree = false,
        sourceType = Custom,
        category = swift,
        subcategories = listOf(swiftConcurrency, memoryManagement),
        durationSeconds = 660,
        studiedCount = 12,
        xpTotal = 890,
    ),
    recent(
        id = "recent-python-typing",
        startedAt = now.minus(2, ChronoUnit.DAYS),
        mode = StudyMode.Fast,
        isHandsFree = false,
        sourceType = SingleSubcategory,
        category = python,
        subcategories = listOf(typingAndInterfaces),
        durationSeconds = 300,
        studiedCount = 8,
        xpTotal = 160,
    ),
    recent(
        id = "recent-kotlin-null-safety-loss",
        startedAt = now.minus(4, ChronoUnit.DAYS),
        mode = StudyMode.Rated,
        isHandsFree = false,
        sourceType = SingleSubcategory,
        category = kotlin,
        subcategories = listOf(nullSafetyAndEquality),
        durationSeconds = 420,
        studiedCount = 10,
        xpTotal = -40,
    ),
    recent(
        id = "recent-android-testing",
        startedAt = now.minus(12, ChronoUnit.DAYS),
        mode = StudyMode.Rated,
        isHandsFree = true,
        sourceType = SingleSubcategory,
        category = android,
        subcategories = listOf(testing),
        durationSeconds = 900,
        studiedCount = 18,
        xpTotal = 720,
    ),
)

/** Ended [endedMinutesAgo] before [now], starting no earlier than midnight. */
private fun startedToday(now: Instant, zone: ZoneId, endedMinutesAgo: Long, durationSeconds: Int): Instant {
    val startOfToday = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
    val startedAt = now.minus(endedMinutesAgo, ChronoUnit.MINUTES).minusSeconds(durationSeconds.toLong())
    return maxOf(startedAt, startOfToday)
}

@Suppress("LongParameterList")
private fun recent(
    id: String,
    startedAt: Instant,
    mode: StudyMode,
    isHandsFree: Boolean,
    sourceType: SessionSourceType,
    category: Category,
    subcategories: List<Subcategory>,
    durationSeconds: Int,
    studiedCount: Int,
    xpTotal: Int,
): RecentItem {
    val session = when (mode) {
        StudyMode.Rated -> RecentSession.Rated(
            id = id,
            startedAt = startedAt,
            durationSeconds = durationSeconds,
            sourceType = sourceType,
            categoryId = category.id,
            categoryName = category.name,
            subcategoryIds = subcategories.map { it.id },
            subcategoryNames = subcategories.map { it.name },
            studiedCount = studiedCount,
            xpTotal = xpTotal,
            voiceAnsweringEnabled = isHandsFree,
        )

        StudyMode.Fast -> RecentSession.Fast(
            id = id,
            startedAt = startedAt,
            durationSeconds = durationSeconds,
            sourceType = sourceType,
            categoryId = category.id,
            categoryName = category.name,
            subcategoryIds = subcategories.map { it.id },
            subcategoryNames = subcategories.map { it.name },
            studiedCount = studiedCount,
            xpTotal = xpTotal,
            readAloudEnabled = isHandsFree,
        )
    }
    return RecentItem(session = session, category = category)
}
