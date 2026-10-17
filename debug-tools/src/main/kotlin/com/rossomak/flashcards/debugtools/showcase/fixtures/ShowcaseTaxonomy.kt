@file:Suppress("MagicNumber")

package com.rossomak.flashcards.debugtools.showcase.fixtures

import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressSummary
import com.rossomak.flashcards.core.domain.model.UserFavorites
import java.time.Instant

/**
 * The real taxonomy for the screen showcase: every seeded Category with all of its Topics, named, ordered and
 * sized as the seed pipeline writes them, the progress summary that fills their rings, and the User's Favorites.
 * Built from the domain constructors only, so the showcase never touches a repository.
 */
internal object ShowcaseTaxonomy {

    private const val FEATURED_SUBCATEGORY_COUNT = 5

    // Bare Categories that the Topics below are built from; [categories] adds each one's Topic count and chip line.
    private val androidBase = category(id = "android", name = "Android", order = 0, iconSvg = ANDROID_ICON_SVG, color = ANDROID_COLOR)
    private val applePlatformsBase = category(id = "apple-platforms", name = "Apple Platforms", order = 1, iconSvg = APPLE_PLATFORMS_ICON_SVG, color = APPLE_PLATFORMS_COLOR)
    private val pythonBase = category(id = "python", name = "Python", order = 2, iconSvg = PYTHON_ICON_SVG, color = PYTHON_COLOR)
    private val dataSciencePythonBase =
        category(id = "data-science-python", name = "Data Science Python", order = 3, iconSvg = DATA_SCIENCE_PYTHON_ICON_SVG, color = DATA_SCIENCE_PYTHON_COLOR)
    private val machineLearningBase = category(id = "machine-learning", name = "Machine Learning", order = 4, iconSvg = MACHINE_LEARNING_ICON_SVG, color = MACHINE_LEARNING_COLOR)
    private val swiftBase = category(id = "swift", name = "Swift", order = 5, iconSvg = SWIFT_ICON_SVG, color = SWIFT_COLOR)
    private val kotlinBase = category(id = "kotlin", name = "Kotlin", order = 6, iconSvg = KOTLIN_ICON_SVG, color = KOTLIN_COLOR)

    // Each Category's Topics with their card counts, in stored order: card volume, descending.
    private val androidTopics = topics(
        androidBase,
        "Compose" to 1_180, "Media" to 392, "Performance" to 387, "Background Work" to 349, "Testing" to 341, "Security" to 336, "Firebase" to 309,
        "Connectivity" to 294, "Architecture" to 285, "Build System" to 271, "App Fundamentals" to 253, "Android Tv" to 243, "Advanced Features" to 236,
        "Automotive" to 230, "Notifications" to 208, "Persistence" to 198, "Location And Sensors" to 193, "Android Features" to 192, "App Distribution" to 172,
        "System Ui" to 172, "Networking" to 166, "Graphics" to 164, "Wear Os" to 160, "Permissions" to 158, "Views" to 127, "Dependency Injection" to 121,
        "Localization" to 118, "Adaptive Ui" to 116, "Navigation" to 115, "Accessibility" to 105, "Concurrency" to 70,
    )
    private val applePlatformsTopics = topics(
        applePlatformsBase,
        "Human Interface Guidelines" to 1_997, "Swiftui" to 1_300, "Appkit" to 697, "Uikit" to 651, "App Distribution" to 621, "Testing" to 429, "Visionos" to 335,
        "Concurrency Combine" to 326, "Macos" to 227, "Watchos" to 193, "Tvos" to 109,
    )
    private val pythonTopics = topics(
        pythonBase,
        "Networking And Web" to 336, "Collections And Structured Data" to 286, "Syntax And Idioms" to 279, "Core Types" to 258,
        "Data Persistence And Serialization" to 239, "Concurrency" to 216, "Cli And Os Services" to 192, "Stdlib Misc" to 185, "Modules And Packaging" to 166,
        "Runtime Internals" to 159, "Testing And Debugging" to 144, "Numeric And Math" to 141, "Oop And Classes" to 133, "Text Processing" to 123,
        "Typing And Interfaces" to 123, "Files And Io" to 115, "Gui" to 104, "Functions And Scope" to 90, "Exceptions And Context Managers" to 75,
        "Dates And Times" to 45,
    )
    private val dataSciencePythonTopics = topics(
        dataSciencePythonBase,
        "Pandas" to 541, "Statsmodels" to 375, "Polars" to 373, "Scipy" to 340, "Matplotlib" to 289, "Plotly" to 286, "Numpy" to 274, "Jupyter" to 223, "Dask" to 200,
        "Numba" to 193, "Seaborn" to 175,
    )
    private val machineLearningTopics = topics(
        machineLearningBase,
        "Scikit Learn" to 704, "Tensorflow" to 343, "Hugging Face Transformers" to 313, "Pytorch" to 298, "Keras" to 273, "Gradient Boosting" to 225,
        "Math Foundations" to 94,
    )
    private val swiftTopics = topics(
        swiftBase,
        "Object Model" to 244, "Collections" to 192, "Concurrency" to 180, "Numbers And Values" to 177, "Generics And Metaprogramming" to 167,
        "Control Flow And Functions" to 158, "Strings And Text" to 149, "Fundamentals" to 148, "Memory Management" to 117, "Formatting And Units" to 107,
        "Common Protocols" to 74, "Serialization" to 72, "Reflection And Diagnostics" to 57, "Dates And Time" to 54,
    )
    private val kotlinTopics = topics(
        kotlinBase,
        "Coroutines" to 194, "Classes And Oop" to 170, "Serialization" to 167, "Functions" to 166, "Syntax And Idioms" to 128, "Collections Fundamentals" to 125,
        "Collections Operations" to 100, "Core Types" to 79, "Control Flow And Exceptions" to 66, "Properties And Delegation" to 65, "Null Safety And Equality" to 64,
        "Stdlib Misc" to 60, "Generics And Reflection" to 53,
    )

    val compose = androidTopics.named("Compose")
    val navigation = androidTopics.named("Navigation")
    val connectivity = androidTopics.named("Connectivity")
    val backgroundWork = androidTopics.named("Background Work")
    val testing = androidTopics.named("Testing")
    val coroutines = kotlinTopics.named("Coroutines")
    val functions = kotlinTopics.named("Functions")
    val controlFlowAndExceptions = kotlinTopics.named("Control Flow And Exceptions")
    val nullSafetyAndEquality = kotlinTopics.named("Null Safety And Equality")
    val genericsAndReflection = kotlinTopics.named("Generics And Reflection")
    val coreTypes = pythonTopics.named("Core Types")
    val typingAndInterfaces = pythonTopics.named("Typing And Interfaces")
    val swiftConcurrency = swiftTopics.named("Concurrency")
    val controlFlowAndFunctions = swiftTopics.named("Control Flow And Functions")
    val memoryManagement = swiftTopics.named("Memory Management")

    val subcategoriesByCategoryId: Map<String, List<Subcategory>> =
        listOf(androidTopics, applePlatformsTopics, pythonTopics, dataSciencePythonTopics, machineLearningTopics, swiftTopics, kotlinTopics).flatten().groupBy { it.categoryId }

    /** Every Category, each with its chip line filled from its own Topics, in the taxonomy's order. */
    val categories: List<Category> = listOf(androidBase, applePlatformsBase, pythonBase, dataSciencePythonBase, machineLearningBase, swiftBase, kotlinBase).map { category ->
        val topics = subcategoriesByCategoryId[category.id].orEmpty()
        val featuredNames = topics.sortedByDescending { it.cardCount }.take(FEATURED_SUBCATEGORY_COUNT).map { it.name }
        category.copy(subcategoryCount = topics.size, featuredSubcategoryNames = featuredNames)
    }

    val android: Category get() = categoryById(androidBase.id)
    val python: Category get() = categoryById(pythonBase.id)
    val swift: Category get() = categoryById(swiftBase.id)
    val kotlin: Category get() = categoryById(kotlinBase.id)

    fun categoryById(categoryId: String): Category = categories.first { it.id == categoryId }

    fun subcategoriesOf(category: Category): List<Subcategory> = subcategoriesByCategoryId[category.id].orEmpty()

    /** Studied counts for the Topics Ross has touched; every other Topic is resolved and never studied. */
    val progressSummary = ProgressSummary(
        subcategories = mapOf(
            compose.id to SubcategoryProgressSummary(masteredCount = 158, studiedCount = 248),
            navigation.id to SubcategoryProgressSummary(masteredCount = 61, studiedCount = 82),
            coroutines.id to SubcategoryProgressSummary(masteredCount = 8, studiedCount = 19),
            controlFlowAndExceptions.id to SubcategoryProgressSummary(masteredCount = 3, studiedCount = 9),
            genericsAndReflection.id to SubcategoryProgressSummary(masteredCount = 9, studiedCount = 20),
            typingAndInterfaces.id to SubcategoryProgressSummary(masteredCount = 13, studiedCount = 30),
            swiftConcurrency.id to SubcategoryProgressSummary(masteredCount = 19, studiedCount = 32),
            memoryManagement.id to SubcategoryProgressSummary(masteredCount = 32, studiedCount = 95),
        ),
    )

    private val favoritedAt: Instant = Instant.parse("2026-09-01T09:00:00Z")

    val favorites = UserFavorites(
        categoryIds = mapOf(kotlinBase.id to favoritedAt.plusSeconds(3_600), dataSciencePythonBase.id to favoritedAt),
        subcategoryIds = mapOf(
            coroutines.id to favoritedAt.plusSeconds(7_200),
            compose.id to favoritedAt.plusSeconds(5_400),
            swiftConcurrency.id to favoritedAt.plusSeconds(1_800),
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

    /** One Topic per name, ordered as given, with the id the seed pipeline derives from its name. */
    private fun topics(category: Category, vararg cardCountByName: Pair<String, Int>): List<Subcategory> =
        cardCountByName.mapIndexed { order, (name, cardCount) ->
            Subcategory(
                id = "${category.id}-${name.lowercase().replace(' ', '-')}",
                name = name,
                categoryId = category.id,
                categoryName = category.name,
                order = order,
                cardCount = cardCount,
            )
        }

    private fun List<Subcategory>.named(name: String): Subcategory = first { it.name == name }
}
