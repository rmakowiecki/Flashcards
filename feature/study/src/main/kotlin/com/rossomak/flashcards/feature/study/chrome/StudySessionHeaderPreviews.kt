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
        progress = StudySessionProgress(label = "Card", completedCount = 5, totalCount = 15),
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
        progress = StudySessionProgress(label = "Completed cards", completedCount = 5, totalCount = 15),
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
        progress = null,
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
        progress = StudySessionProgress(label = "Completed cards", completedCount = 8, totalCount = 15),
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
        progress = StudySessionProgress(label = "Completed cards", completedCount = 5, totalCount = 15),
        onClose = {},
        onReportProblem = {},
    )
}
