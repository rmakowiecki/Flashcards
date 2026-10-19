package com.rossomak.flashcards.debugtools.showcase.fixtures

import android.content.res.Resources
import androidx.annotation.RawRes
import androidx.compose.runtime.staticCompositionLocalOf
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.CodeBlock
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.debugtools.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Real Categories, Topics and a sample of real Flashcards, exported once from Firestore into `res/raw`. */
class ShowcaseFixtures private constructor(
    internal val categories: List<Category>,
    internal val subcategories: List<Subcategory>,
    private val flashcards: List<Flashcard>,
) {

    internal fun category(id: String): Category = categories.first { it.id == id }

    internal fun subcategory(id: String): Subcategory = subcategories.first { it.id == id }

    internal fun subcategoriesOf(categoryId: String): List<Subcategory> = subcategories.filter { it.categoryId == categoryId }

    internal fun flashcardsOf(subcategoryId: String): List<Flashcard> = flashcards.filter { it.subcategoryId == subcategoryId }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun load(resources: Resources): ShowcaseFixtures = ShowcaseFixtures(
            categories = json.decodeFromString<List<CategoryJson>>(resources.readText(R.raw.showcase_categories)).map { it.toDomain() },
            subcategories = json.decodeFromString<List<SubcategoryJson>>(resources.readText(R.raw.showcase_subcategories)).map { it.toDomain() },
            flashcards = resources.readText(R.raw.showcase_flashcards).lineSequence()
                .filter { it.isNotBlank() }
                .map { json.decodeFromString<FlashcardJson>(it).toDomain() }
                .toList(),
        )

        private fun Resources.readText(@RawRes id: Int): String = openRawResource(id).bufferedReader().use { it.readText() }
    }
}

internal val LocalShowcaseFixtures = staticCompositionLocalOf<ShowcaseFixtures> { error("No ShowcaseFixtures provided") }

@Serializable
private data class CategoryJson(
    val id: String,
    val name: String,
    val order: Int,
    val subcategoryCount: Int,
    val iconSvg: String? = null,
    val color: String? = null,
    val featuredSubcategoryNames: List<String> = emptyList(),
) {
    fun toDomain() = Category(id, name, order, subcategoryCount, iconSvg, color, featuredSubcategoryNames)
}

@Serializable
private data class SubcategoryJson(val id: String, val name: String, val categoryId: String, val categoryName: String, val order: Int, val cardCount: Int) {
    fun toDomain() = Subcategory(id, name, categoryId, categoryName, order, cardCount)
}

@Serializable
private data class CodeBlockJson(val language: String, val code: String)

@Serializable
private data class FlashcardJson(
    val id: String,
    val subcategoryId: String,
    val tags: List<String>,
    val question: String,
    val answer: String,
    val difficulty: Int,
    val questionCode: List<CodeBlockJson>? = null,
    val answerCode: List<CodeBlockJson>? = null,
    val questionSpoken: String? = null,
    val answerSpoken: String? = null,
    val extendedContext: String? = null,
) {
    fun toDomain() = Flashcard(
        id = id,
        subcategoryId = subcategoryId,
        tags = tags,
        question = question,
        answer = answer,
        difficulty = difficulty,
        questionCode = questionCode?.map { CodeBlock(it.language, it.code) },
        answerCode = answerCode?.map { CodeBlock(it.language, it.code) },
        questionSpoken = questionSpoken,
        answerSpoken = answerSpoken,
        extendedContext = extendedContext,
    )
}
