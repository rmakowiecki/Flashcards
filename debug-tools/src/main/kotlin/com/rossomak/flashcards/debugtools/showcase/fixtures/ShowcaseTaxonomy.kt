@file:Suppress("MagicNumber")

package com.rossomak.flashcards.debugtools.showcase.fixtures

import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressSummary
import com.rossomak.flashcards.core.domain.model.UserFavorites
import java.time.Instant

/**
 * A made-up developer taxonomy for the screen showcase: five Categories with their Topics, the progress
 * summary that fills their rings, and the User's Favorites. Built from the domain constructors only, so
 * the showcase never touches a repository.
 *
 * System Design has a color but no glyph, and DevOps has neither, so the showcase also shows both
 * fallbacks.
 */
internal object ShowcaseTaxonomy {

    private const val FEATURED_SUBCATEGORY_COUNT = 5

    // Bare Categories that the Topics below are built from; [categories] adds each one's Topic count and chip line.
    private val kotlinBase = category(id = "kotlin", name = "Kotlin", order = 0, iconSvg = KOTLIN_ICON_SVG, color = KOTLIN_COLOR)
    private val androidBase = category(id = "android", name = "Android", order = 1, iconSvg = ANDROID_ICON_SVG, color = ANDROID_COLOR)
    private val pythonBase = category(id = "python", name = "Python", order = 2, iconSvg = PYTHON_ICON_SVG, color = PYTHON_COLOR)
    private val systemDesignBase = category(id = "system-design", name = "System Design", order = 3, iconSvg = null, color = "#E8710A")
    private val devOpsBase = category(id = "devops", name = "DevOps", order = 4, iconSvg = null, color = null)

    // Coroutines' card count matches the Flashcards in [ShowcaseFlashcards.coroutines], so Subcategory Details agrees with its row.
    val coroutines = subcategory(kotlinBase, id = "kotlin-coroutines", name = "Coroutines", order = 0, cardCount = 7)
    val flows = subcategory(kotlinBase, id = "kotlin-flows", name = "Flows", order = 1, cardCount = 28)
    val collections = subcategory(kotlinBase, id = "kotlin-collections", name = "Collections", order = 2, cardCount = 24)
    val generics = subcategory(kotlinBase, id = "kotlin-generics", name = "Generics", order = 3, cardCount = 18)
    val nullSafety = subcategory(kotlinBase, id = "kotlin-null-safety", name = "Null safety", order = 4, cardCount = 15)
    val sealedTypes = subcategory(kotlinBase, id = "kotlin-sealed-types", name = "Sealed types", order = 5, cardCount = 11)

    val compose = subcategory(androidBase, id = "android-compose", name = "Compose", order = 0, cardCount = 64)
    val navigation = subcategory(androidBase, id = "android-navigation", name = "Navigation", order = 1, cardCount = 22)
    val lifecycle = subcategory(androidBase, id = "android-lifecycle", name = "Lifecycle", order = 2, cardCount = 19)
    val room = subcategory(androidBase, id = "android-room", name = "Room", order = 3, cardCount = 17)
    val workManager = subcategory(androidBase, id = "android-workmanager", name = "WorkManager", order = 4, cardCount = 12)

    val asyncio = subcategory(pythonBase, id = "python-asyncio", name = "Asyncio", order = 0, cardCount = 21)
    val typing = subcategory(pythonBase, id = "python-typing", name = "Type hints", order = 1, cardCount = 16)
    val decorators = subcategory(pythonBase, id = "python-decorators", name = "Decorators", order = 2, cardCount = 14)
    val generators = subcategory(pythonBase, id = "python-generators", name = "Generators", order = 3, cardCount = 12)

    val caching = subcategory(systemDesignBase, id = "system-design-caching", name = "Caching", order = 0, cardCount = 20)
    val consistency = subcategory(systemDesignBase, id = "system-design-consistency", name = "Consistency models", order = 1, cardCount = 18)
    val loadBalancing = subcategory(systemDesignBase, id = "system-design-load-balancing", name = "Load balancing", order = 2, cardCount = 14)
    val rateLimiting = subcategory(systemDesignBase, id = "system-design-rate-limiting", name = "Rate limiting", order = 3, cardCount = 10)

    val kubernetes = subcategory(devOpsBase, id = "devops-kubernetes", name = "Kubernetes", order = 0, cardCount = 30)
    val docker = subcategory(devOpsBase, id = "devops-docker", name = "Docker", order = 1, cardCount = 25)
    val continuousDelivery = subcategory(devOpsBase, id = "devops-ci-cd", name = "CI/CD", order = 2, cardCount = 16)

    val subcategoriesByCategoryId: Map<String, List<Subcategory>> = listOf(
        coroutines, flows, collections, generics, nullSafety, sealedTypes,
        compose, navigation, lifecycle, room, workManager,
        asyncio, typing, decorators, generators,
        caching, consistency, loadBalancing, rateLimiting,
        kubernetes, docker, continuousDelivery,
    ).groupBy { it.categoryId }

    /** Every Category, each with its chip line filled from its own Topics, in the taxonomy's order. */
    val categories: List<Category> = listOf(kotlinBase, androidBase, pythonBase, systemDesignBase, devOpsBase).map { category ->
        val topics = subcategoriesByCategoryId[category.id].orEmpty()
        val featuredNames = topics.sortedByDescending { it.cardCount }.take(FEATURED_SUBCATEGORY_COUNT).map { it.name }
        category.copy(subcategoryCount = topics.size, featuredSubcategoryNames = featuredNames)
    }

    val kotlin: Category get() = categoryById(kotlinBase.id)
    val android: Category get() = categoryById(androidBase.id)
    val python: Category get() = categoryById(pythonBase.id)
    val systemDesign: Category get() = categoryById(systemDesignBase.id)
    val devOps: Category get() = categoryById(devOpsBase.id)

    fun categoryById(categoryId: String): Category = categories.first { it.id == categoryId }

    fun subcategoriesOf(category: Category): List<Subcategory> = subcategoriesByCategoryId[category.id].orEmpty()

    /** Studied counts for the Topics Alex has touched; every other Topic is resolved and never studied. */
    val progressSummary = ProgressSummary(
        subcategories = mapOf(
            coroutines.id to SubcategoryProgressSummary(masteredCount = 3, studiedCount = 5),
            flows.id to SubcategoryProgressSummary(masteredCount = 6, studiedCount = 12),
            collections.id to SubcategoryProgressSummary(masteredCount = 24, studiedCount = 24),
            generics.id to SubcategoryProgressSummary(masteredCount = 1, studiedCount = 4),
            compose.id to SubcategoryProgressSummary(masteredCount = 22, studiedCount = 40),
            navigation.id to SubcategoryProgressSummary(masteredCount = 3, studiedCount = 9),
            asyncio.id to SubcategoryProgressSummary(masteredCount = 5, studiedCount = 8),
            caching.id to SubcategoryProgressSummary(masteredCount = 2, studiedCount = 7),
            kubernetes.id to SubcategoryProgressSummary(masteredCount = 0, studiedCount = 3),
        ),
    )

    private val favoritedAt: Instant = Instant.parse("2026-09-01T09:00:00Z")

    val favorites = UserFavorites(
        categoryIds = mapOf(kotlinBase.id to favoritedAt.plusSeconds(3_600), systemDesignBase.id to favoritedAt),
        subcategoryIds = mapOf(
            coroutines.id to favoritedAt.plusSeconds(7_200),
            compose.id to favoritedAt.plusSeconds(5_400),
            asyncio.id to favoritedAt.plusSeconds(1_800),
        ),
    )

    private fun category(id: String, name: String, order: Int, iconSvg: String?, color: String?) = Category(
        id = id,
        name = name,
        order = order,
        subcategoryCount = 0,
        iconSvg = iconSvg,
        color = color,
        featuredSubcategoryNames = emptyList(),
    )

    private fun subcategory(category: Category, id: String, name: String, order: Int, cardCount: Int) = Subcategory(
        id = id,
        name = name,
        categoryId = category.id,
        categoryName = category.name,
        order = order,
        cardCount = cardCount,
    )
}
