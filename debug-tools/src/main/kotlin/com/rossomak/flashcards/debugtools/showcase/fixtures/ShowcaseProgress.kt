@file:Suppress("MagicNumber")

package com.rossomak.flashcards.debugtools.showcase.fixtures

import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressSummary
import com.rossomak.flashcards.core.domain.model.UserFavorites
import java.time.Instant

internal val ShowcaseFixtures.android: Category get() = category("android")
internal val ShowcaseFixtures.python: Category get() = category("python")
internal val ShowcaseFixtures.swift: Category get() = category("swift")
internal val ShowcaseFixtures.kotlin: Category get() = category("kotlin")

internal val ShowcaseFixtures.compose: Subcategory get() = subcategory("android-compose")
internal val ShowcaseFixtures.navigation: Subcategory get() = subcategory("android-navigation")
internal val ShowcaseFixtures.connectivity: Subcategory get() = subcategory("android-connectivity")
internal val ShowcaseFixtures.backgroundWork: Subcategory get() = subcategory("android-background-work")
internal val ShowcaseFixtures.testing: Subcategory get() = subcategory("android-testing")
internal val ShowcaseFixtures.coroutines: Subcategory get() = subcategory("kotlin-coroutines")
internal val ShowcaseFixtures.functions: Subcategory get() = subcategory("kotlin-functions")
internal val ShowcaseFixtures.nullSafetyAndEquality: Subcategory get() = subcategory("kotlin-null-safety-and-equality")
internal val ShowcaseFixtures.coreTypes: Subcategory get() = subcategory("python-core-types")
internal val ShowcaseFixtures.typingAndInterfaces: Subcategory get() = subcategory("python-typing-and-interfaces")
internal val ShowcaseFixtures.swiftConcurrency: Subcategory get() = subcategory("swift-concurrency")
internal val ShowcaseFixtures.memoryManagement: Subcategory get() = subcategory("swift-memory-management")

/** Ross's progress, keyed by Topic id; every other Topic is never studied. */
internal val showcaseProgressSummary = ProgressSummary(
    subcategories = mapOf(
        "android-compose" to SubcategoryProgressSummary(masteredCount = 158, studiedCount = 248),
        "android-navigation" to SubcategoryProgressSummary(masteredCount = 61, studiedCount = 82),
        "kotlin-coroutines" to SubcategoryProgressSummary(masteredCount = 8, studiedCount = 19),
        "kotlin-control-flow-and-exceptions" to SubcategoryProgressSummary(masteredCount = 3, studiedCount = 9),
        "kotlin-generics-and-reflection" to SubcategoryProgressSummary(masteredCount = 9, studiedCount = 20),
        "python-typing-and-interfaces" to SubcategoryProgressSummary(masteredCount = 13, studiedCount = 30),
        "swift-concurrency" to SubcategoryProgressSummary(masteredCount = 19, studiedCount = 32),
        "swift-memory-management" to SubcategoryProgressSummary(masteredCount = 32, studiedCount = 95),
    ),
)

private val favoritedAt: Instant = Instant.parse("2026-09-01T09:00:00Z")

internal val showcaseFavorites = UserFavorites(
    categoryIds = mapOf("kotlin" to favoritedAt.plusSeconds(3_600), "data-science-python" to favoritedAt),
    subcategoryIds = mapOf(
        "kotlin-coroutines" to favoritedAt.plusSeconds(7_200),
        "android-compose" to favoritedAt.plusSeconds(5_400),
        "swift-concurrency" to favoritedAt.plusSeconds(1_800),
    ),
)
