package com.rossomak.flashcards.core.data.mapper

import com.rossomak.flashcards.core.data.model.CategoryDto
import com.rossomak.flashcards.core.data.model.FlashcardDto
import com.rossomak.flashcards.core.data.model.OnboardingSubcategoryDto
import com.rossomak.flashcards.core.data.model.SubcategoryDto
import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.CodeBlock
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.OnboardingSubcategory
import com.rossomak.flashcards.core.domain.model.Subcategory

fun CategoryDto.toDomain() = Category(
    id = id,
    name = name,
    order = order,
    subcategoryCount = subcategoryCount,
    iconSvg = iconSvg,
    color = color,
    featuredSubcategoryNames = featuredSubcategoryNames,
)

fun SubcategoryDto.toDomain() = Subcategory(
    id = id,
    name = name,
    categoryId = categoryId,
    categoryName = categoryName,
    order = order,
    cardCount = cardCount
)

fun OnboardingSubcategoryDto.toDomain() = OnboardingSubcategory(
    id = subcategoryId,
    name = subcategoryName,
    categoryId = categoryId,
    categoryName = categoryName,
    order = order,
    iconSvg = iconSvg,
)

fun FlashcardDto.toDomain(subcategoryId: String): Flashcard? = difficulty?.let {
    Flashcard(
        id = id,
        subcategoryId = subcategoryId,
        question = question,
        answer = answer,
        tags = tags,
        difficulty = it,
        questionCode = questionCode?.map { CodeBlock(it.language, it.code) },
        answerCode = answerCode?.map { CodeBlock(it.language, it.code) },
        questionSpoken = questionSpoken,
        answerSpoken = answerSpoken,
        extendedContext = extendedContext
    )
}
