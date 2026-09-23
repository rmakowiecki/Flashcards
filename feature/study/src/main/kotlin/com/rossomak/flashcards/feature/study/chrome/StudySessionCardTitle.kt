package com.rossomak.flashcards.feature.study.chrome

import com.rossomak.flashcards.core.domain.model.Flashcard

/**
 * "[categoryName][separator]<subcategory name of [currentCard]>", or just [categoryName] when
 * [currentCard] is null or its subcategory isn't in [subcategoryNameById] — a composite session's
 * cards can each belong to a different Subcategory, so this is resolved per card, not once per
 * session. Shared by [StudySessionHeader]'s Fast and Rated callers.
 */
fun studySessionCardTitle(
    categoryName: String,
    currentCard: Flashcard?,
    subcategoryNameById: Map<String, String>,
    separator: String,
): String {
    val subcategoryName = currentCard?.let { subcategoryNameById[it.subcategoryId] } ?: return categoryName
    return "$categoryName$separator$subcategoryName"
}
