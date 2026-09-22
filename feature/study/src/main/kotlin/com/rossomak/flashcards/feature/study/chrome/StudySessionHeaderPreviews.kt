package com.rossomak.flashcards.feature.study.chrome

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rossomak.flashcards.core.domain.model.Flashcard

private val previewCard = Flashcard(
    id = "card-1",
    subcategoryId = "compose",
    tags = emptyList(),
    question = "What does `remember` do?",
    answer = "Caches a value across recompositions.",
    difficulty = 3,
    questionCode = null,
    answerCode = null,
    questionSpoken = null,
    answerSpoken = null,
    extendedContext = null,
)

private val previewCardWithTags = previewCard.copy(
    id = "card-2",
    tags = listOf("State", "Recomposition", "Modifiers"),
)

@Preview(showBackground = true)
@Composable
private fun StudySessionHeaderFastVariantPreview() {
    StudySessionHeader(
        title = "Fast Session",
        reportableCard = previewCard,
        progressLabel = "Card",
        completedCount = 5,
        totalCount = 15,
        progressFraction = 5 / 15f,
        onClose = {},
        onReportProblem = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun StudySessionHeaderRatedVariantPreview() {
    StudySessionHeader(
        title = "Rated Session",
        reportableCard = previewCard,
        progressLabel = "Completed cards",
        completedCount = 5,
        totalCount = 15,
        progressFraction = 5 / 15f,
        onClose = {},
        onReportProblem = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun StudySessionHeaderNoProgressPreview() {
    StudySessionHeader(
        title = "Session",
        reportableCard = null,
        progressLabel = null,
        completedCount = null,
        totalCount = null,
        progressFraction = null,
        onClose = {},
        onReportProblem = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun StudySessionHeaderWithTagsPreview() {
    StudySessionHeader(
        title = "Rated Session",
        reportableCard = previewCardWithTags,
        progressLabel = "Completed cards",
        completedCount = 8,
        totalCount = 15,
        progressFraction = 8 / 15f,
        onClose = {},
        onReportProblem = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun StudySessionHeaderNoFlagPreview() {
    StudySessionHeader(
        title = "Rated Session",
        reportableCard = null,
        progressLabel = "Completed cards",
        completedCount = 5,
        totalCount = 15,
        progressFraction = 5 / 15f,
        onClose = {},
        onReportProblem = {},
    )
}
